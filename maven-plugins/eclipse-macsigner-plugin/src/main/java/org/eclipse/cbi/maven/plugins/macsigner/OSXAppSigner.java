/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.macsigner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.MojoExecutionIOExceptionWrapper;
import org.eclipse.cbi.maven.http.AbstractCompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpResult;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;

/**
 * A signer of OS X applications.
 */
@AutoValue
public abstract class OSXAppSigner {

	/**
	 * The zip file extension.
	 */
	private static final String DOT_ZIP = ".zip";

	/**
	 * Default app folder extension
	 */
	private static final String DOT_APP_GLOB_PATTERN = "glob:**.app";

	private static final String PART_NAME = "file";
	
	abstract ExceptionHandler exceptionHandler();

	abstract Log log();
	
	abstract HttpClient httpClient();
	
	abstract URI serverUri();
	
	abstract Duration timeout();

	public static Builder builder() {
		return new AutoValue_OSXAppSigner.Builder();
	}
	
	OSXAppSigner() {
		
	}

	/**
	 * Signs the apps in the given if they are directories and ended with
	 * {@code .app}.
	 *
	 * @param signFiles
	 *            the file to be signed
	 * @return the number of signed apps.
	 * @throws MojoExecutionException
	 */
	public int signApplications(Set<Path> signFiles) throws MojoExecutionException {
		Objects.requireNonNull(signFiles);
		int ret = 0;
		for (Path signFile : signFiles) {
			final PathMatcher appPattern = signFile.getFileSystem().getPathMatcher(DOT_APP_GLOB_PATTERN);
			if (Files.isDirectory(signFile) && appPattern.matches(signFile)) {
		    	if (signApplication(signFile)) {
		    		ret++;
		    	}
		    } else {
		    	exceptionHandler().handleError("Path '" + signFile.toString() + "' does not exist or is not a valid OS X application"
		    			+ " It must be a folder ending with '.app' extension. It won't be signed.");
		    }
		}
		return ret;
	}

	/**
	 * Browses the given path looking for OS X applications to be signed with a
	 * name contained in the given set.
	 *
	 * @param baseSearchDir
	 * @param pathMatchers
	 * @return the number of signed apps.
	 * @throws MojoExecutionException
	 */
	public int signApplications(Path baseSearchDir, Set<PathMatcher> pathMatchers) throws MojoExecutionException {
		Objects.requireNonNull(baseSearchDir);
		Objects.requireNonNull(pathMatchers);

		int ret = 0;

		try {
			OSXApplicationSignerVisitor applicationSignerVisitor = new OSXApplicationSignerVisitor(pathMatchers);
			Files.walkFileTree(baseSearchDir, applicationSignerVisitor);
			ret = applicationSignerVisitor.getSignedAppCount();
		} catch (MojoExecutionIOExceptionWrapper e) {
			throw e.getCause();
		} catch (IOException e) {
			exceptionHandler().handleError("Error occured while signing OS X application (" + Joiner.on(", ").join(pathMatchers) + ").", e);
		}

		return ret;
	}

    /**
     * Signs the file.
     * @param signer
     * @param appFolder
     * @throws MojoExecutionException
     */
    public boolean signApplication(Path appFolder) throws MojoExecutionException {
    	boolean ret = false;
    	Path zippedApp = null;

    	try {
            zippedApp = Files.createTempFile(Paths.getParent(appFolder), appFolder.getFileName().toString() + "_", DOT_ZIP);
            Zips.packZip(appFolder, zippedApp, true);

            log().info("[" + new Date() + "] Signing OS X application '" + appFolder + "'...");
            if (!processOnSigningServer(zippedApp)) {
                exceptionHandler().handleError("Signing of OS X application '" + appFolder + "' failed. Activate debug (-X, --debug) to see why.");
            } else {
            	ret = true;
            }

            Zips.unpackZip(zippedApp, Paths.getParent(appFolder));
        } catch (IOException e) {
        	exceptionHandler().handleError("Signing of OS X application '" + appFolder + "' failed.", e);
        	ret = false;
        } finally {
        	if (zippedApp != null) {
        		Paths.deleteQuietly(zippedApp);
        	}
        }

    	return ret;
    }
    
    private boolean processOnSigningServer(final Path file) throws IOException {
    		HttpRequest.Config requestConfig = HttpRequest.Config.builder().timeout(timeout()).build();
		final HttpRequest request = HttpRequest.on(serverUri()).withParam(PART_NAME, file).build();
		log().debug("OS X app signing request: " + request.toString());
		boolean success = httpClient().send(request, requestConfig, new AbstractCompletionListener(file.getParent(), file.getFileName().toString(), OSXAppSigner.class.getSimpleName(), new MavenLogger(log())) {
			@Override
			public void onSuccess(HttpResult result) throws IOException {
				if (result.contentLength() == 0) {
					throw new IOException("Length of the returned content is 0");
				}
				result.copyContent(file, StandardCopyOption.REPLACE_EXISTING);
				if (Files.size(file) == 0) {
					throw new IOException("Size of the returned signed app is 0");
				}
			}
		});
		return success;
	}

	private final class OSXApplicationSignerVisitor extends SimpleFileVisitor<Path> {

		private final Set<PathMatcher> pathMatchers;
		private int signedAppCount;

		OSXApplicationSignerVisitor(Set<PathMatcher> pathMatchers) {
			this.pathMatchers = pathMatchers;
			this.signedAppCount = 0;
		}

		public int getSignedAppCount() {
			return signedAppCount;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			if (dir.getFileSystem().getPathMatcher(DOT_APP_GLOB_PATTERN).matches(dir)) {
				for (PathMatcher pathMatcher : pathMatchers) {
					if (pathMatcher.matches(dir)) {
						return signApp(dir);
					}
				}
			}
			return FileVisitResult.CONTINUE;
		}

		private FileVisitResult signApp(Path dir) throws MojoExecutionIOExceptionWrapper {
			try {
				if (signApplication(dir)) {
					signedAppCount++;
				}
			} catch (MojoExecutionException e) {
				throw new MojoExecutionIOExceptionWrapper(e);
			}
			return FileVisitResult.SKIP_SUBTREE;
		}
	}

	/**
	 * A builder of {@link OSXAppSigner}. Default value for options are:
	 * <ul>
	 * <li>{@link #continueOnFail()}: false</li>
	 * <li>{@link #logOn(Log)}: {@link SystemStreamLog}</li>
	 * <li>{@link #maxRetry(int)}: 0</li>
	 * <li>{@link #waitBeforeRetry(int, TimeUnit)}: 0 {@link TimeUnit#SECONDS seconds}</li>
	 * <ul>
	 */
	@AutoValue.Builder
	public static abstract class Builder {

		Builder() {
			
		}
		
		/**
		 * The {@link Log} onto which the feedback should be printed.
		 * @param log
		 * @return this builder for chained calls.
		 */
		public abstract Builder log(Log log);
		public abstract Builder timeout(Duration timeout);
		public abstract Builder serverUri(URI uri);
		public abstract Builder httpClient(HttpClient httpClient);
		public abstract Builder exceptionHandler(ExceptionHandler handler);

		/**
		 * Creates and returns a new OSXAppSigner configured with the options
		 * specified to this builder.
		 *
		 * @return a new {@link OSXAppSigner}.
		 */
		public abstract OSXAppSigner build();
	}
}
