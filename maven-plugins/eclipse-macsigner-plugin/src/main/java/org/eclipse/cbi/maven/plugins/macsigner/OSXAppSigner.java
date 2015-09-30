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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.maven.common.ExceptionHandler;
import org.eclipse.cbi.maven.common.FileProcessor;
import org.eclipse.cbi.maven.common.MojoExecutionIOExceptionWrapper;

import com.google.common.base.Joiner;

/**
 * A signer of OS X applications.
 */
public class OSXAppSigner {

	/**
	 * The zip file extension.
	 */
	private static final String DOT_ZIP = ".zip";

	/**
	 * Default app folder extension
	 */
	private static final String DOT_APP_GLOB_PATTERN = "glob:**.app";

	private final FileProcessor signer;
	private final ExceptionHandler exceptionHandler;

	private final Log log;

	private final int maxRetry;

	private final int retryInterval;

	private final TimeUnit retryIntervalUnit;

	private OSXAppSigner(FileProcessor signer, boolean continueOnFail, Log log, int maxRetry, int retryInterval, TimeUnit retryIntervalUnit) {
		this.signer = signer;
		this.log = log;
		this.maxRetry = maxRetry;
		this.retryInterval = retryInterval;
		this.retryIntervalUnit = retryIntervalUnit;
		this.exceptionHandler = new ExceptionHandler(log, continueOnFail);
	}

	public static Builder builder(FileProcessor signer) {
		return new Builder(signer);
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
		    	if (signApplication(signer, signFile)) {
		    		ret++;
		    	}
		    } else {
		    	exceptionHandler.handleError("Path '" + signFile.toString() + "' does not exist or is not a valid OS X application"
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
			exceptionHandler.handleError("Error occured while signing OS X application (" + Joiner.on(", ").join(pathMatchers) + ").", e);
		}

		return ret;
	}

    /**
     * Signs the file.
     * @param signer
     * @param appFolder
     * @throws MojoExecutionException
     */
    public boolean signApplication(FileProcessor signer, Path appFolder) throws MojoExecutionException {
    	boolean ret = false;
    	Path zippedApp = null;

    	try {
            zippedApp = Files.createTempFile(Paths.getParent(appFolder), appFolder.getFileName().toString() + "_", DOT_ZIP);
            Zips.packZip(appFolder, zippedApp, true);

            log.info("[" + new Date() + "] Signing OS X application '" + appFolder + "'...");
            if (!signer.process(zippedApp, maxRetry, retryInterval, retryIntervalUnit)) {
                exceptionHandler.handleError("Signing of OS X application '" + appFolder + "' failed. Activate debug (-X, --debug) to see why.");
            } else {
            	ret = true;
            }

            Zips.unpackZip(zippedApp, Paths.getParent(appFolder));
        } catch (IOException e) {
        	exceptionHandler.handleError("Signing of OS X application '" + appFolder + "' failed.", e);
        	ret = false;
        } finally {
        	if (zippedApp != null) {
        		Paths.deleteQuietly(zippedApp);
        	}
        }

    	return ret;
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
				if (signApplication(signer, dir)) {
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
	public static class Builder {

		private final FileProcessor signer;

		private boolean continueOnFail = false;

		private Log log = new SystemStreamLog();

		private int maxRetry = 0;

		private int waitTimer = 0;

		private TimeUnit waitTimerUnit = TimeUnit.SECONDS;

		Builder(FileProcessor signer) {
			this.signer = Objects.requireNonNull(signer);
		}

		/**
		 * Configure the {@link OSXAppSigner} to <strong>not</strong> throw
		 * {@link MojoExecutionException} if it can't sign the Jar. Instead, it
		 * will only log a warning.
		 *
		 * @return this builder for chained calls.
		 */
		public Builder continueOnFail() {
			this.continueOnFail = true;
			return this;
		}

		/**
		 * The {@link Log} onto which the feedback should be printed.
		 * @param log
		 * @return this builder for chained calls.
		 */
		public Builder logOn(Log log) {
			this.log = log;
			return this;
		}

		/**
		 * The maximum number of retry that will be passed to the {@link FileProcessor}.
		 * @param maxRetry
		 * @return this builder for chained calls.
		 */
		public Builder maxRetry(int maxRetry) {
			if (maxRetry < 0) {
				throw new IllegalArgumentException("'maxRetry' must be positive or snull");
			}
			this.maxRetry = maxRetry;
			return this;
		}

		/**
		 * The time to wait between each try (passed to the {@link FileProcessor}).
		 * @param waitTimer
		 * @param timeUnit
		 * @return this builder for chained calls.
		 */
		public Builder waitBeforeRetry(int waitTimer, TimeUnit timeUnit) {
			this.waitTimer = waitTimer;
			this.waitTimerUnit = timeUnit;
			return this;
		}

		/**
		 * Creates and returns a new JarSigner configured with the options
		 * specified to this builder.
		 *
		 * @return a new {@link OSXAppSigner}.
		 */
		public OSXAppSigner build() {
			return new OSXAppSigner(this.signer, this.continueOnFail,
					this.log, this.maxRetry, this.waitTimer, this.waitTimerUnit);
		}
	}
}
