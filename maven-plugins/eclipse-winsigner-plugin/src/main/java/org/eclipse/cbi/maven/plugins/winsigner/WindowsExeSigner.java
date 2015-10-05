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
package org.eclipse.cbi.maven.plugins.winsigner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.MojoExecutionIOExceptionWrapper;
import org.eclipse.cbi.maven.http.AbstractCompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpResult;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;

@AutoValue
public abstract class WindowsExeSigner {

	/**
	 * The name of the part as it will be send to the signing server.
	 */
	private static final String PART_NAME = "file";
	
	abstract HttpClient httpClient();
	abstract Log log();
	abstract ExceptionHandler exceptionHandler();
	abstract URI serverUri();
	
	WindowsExeSigner() {
		
	}
	
	public int signExecutables(Set<Path> exesToSign) throws MojoExecutionException {
		Objects.requireNonNull(exesToSign);
		int ret = 0;
		for (Path exe : exesToSign) {
			if (signExecutable(exe)) {
				ret++;
			}
    	}
		return ret;
	}

	public int signExecutables(Path baseSearchDir, Set<PathMatcher> pathMatchers) throws MojoExecutionException {
		Objects.requireNonNull(baseSearchDir);
		Objects.requireNonNull(pathMatchers);

		int ret = 0;
		try {
			WindowsBinarySignerVisitor signerVisitor = new WindowsBinarySignerVisitor(pathMatchers);
			Files.walkFileTree(baseSearchDir, signerVisitor);
			ret = signerVisitor.getSignedExecutableCount();
		} catch (MojoExecutionIOExceptionWrapper e) {
			throw e.getCause();
		} catch (IOException e) {
			exceptionHandler().handleError("Error occured while signing Windows binary (" + Joiner.on(", ").join(pathMatchers) + ")", e);
		}
		return ret;
	}

	/**
     * signs the file
     * @param signer
     * @param file
     * @throws MojoExecutionException
     */
    public boolean signExecutable(Path file) throws MojoExecutionException {
    	Objects.requireNonNull(file);
    	if (!Files.isRegularFile(file)) {
    		exceptionHandler().handleError("Path '" + file.toString() + "' does not exist or is not a file. It won't be signed.");
    		return false;
    	} else if (!Files.isWritable(file)) {
    		exceptionHandler().handleError("Path '" + file.toString() + "' is not writable. It won't be signed.");
    		return false;
    	}

    	boolean ret = false;
        try {
        	log().info("[" + new Date() + "] Signing Windows executable '" + file + "'...");
            if (!processOnSigningServer(file)) {
            	exceptionHandler().handleError("Signing of Windows executable '" + file + "' failed. Activate debug (-X, --debug) to see why.");
            } else {
            	ret = true;
            }
        } catch (IOException e) {
        	exceptionHandler().handleError("Signing of Windows executable '" + file + "' failed.", e);
        }
        return ret;
    }
    
    private boolean processOnSigningServer(final Path file) throws IOException {
		final HttpRequest request = HttpRequest.on(serverUri()).withParam(PART_NAME, file).build();
		boolean success = httpClient().send(request, new AbstractCompletionListener(file.getParent(), file.getFileName().toString(), this.getClass().getSimpleName(), new MavenLogger(log())) {
			@Override
			public void onSuccess(HttpResult result) throws IOException {
				result.copyContent(file, StandardCopyOption.REPLACE_EXISTING);
			}
		});
		return success;
	}

    public static Builder builder() {
    	return new AutoValue_WindowsExeSigner.Builder();
    }

	private final class WindowsBinarySignerVisitor extends SimpleFileVisitor<Path> {

		private final Set<PathMatcher> pathMatchers;
		private int signedExecutableCount;

		WindowsBinarySignerVisitor(Set<PathMatcher> pathMatchers) {
			this.pathMatchers = pathMatchers;
			signedExecutableCount = 0;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			for (PathMatcher pathMatcher : this.pathMatchers) {
				if (pathMatcher.matches(file)) {
					try {
						if (signExecutable(file)) {
							signedExecutableCount++;
						}
					} catch (MojoExecutionException e) {
						throw new MojoExecutionIOExceptionWrapper(e);
					}
				}
			}
			return FileVisitResult.CONTINUE;
		}

		public int getSignedExecutableCount() {
			return signedExecutableCount;
		}
	}

	/**
	 * A builder of {@link WindowsExeSigner}. Default value for options are:
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
		 * Configure the {@link WindowsExeSigner} to <strong>not</strong> throw
		 * {@link MojoExecutionException} if it can't sign the Jar. Instead, it
		 * will only log a warning.
		 *
		 * @return this builder for chained calls.
		 */
		public abstract Builder exceptionHandler(ExceptionHandler exceptionHandler);

		/**
		 * The {@link Log} onto which the feedback should be printed.
		 * @param log
		 * @return this builder for chained calls.
		 */
		public abstract Builder log(Log log);

		public abstract Builder serverUri(URI uri);
		public abstract Builder httpClient(HttpClient httpClient);

		/**
		 * Creates and returns a new WindowsExeSigner configured with the options
		 * specified to this builder.
		 *
		 * @return a new {@link WindowsExeSigner}.
		 */
		public abstract WindowsExeSigner build();
	}
}
