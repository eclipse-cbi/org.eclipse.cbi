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
import org.eclipse.cbi.common.signing.ExceptionHandler;
import org.eclipse.cbi.common.signing.MojoExecutionExceptionWrapper;
import org.eclipse.cbi.common.signing.Signer;
import org.eclipse.cbi.common.utils.Strings;

public class WindowsExeSigner {

	private final Signer signer;
	private final int maxRetry;
	private final int retryInterval;
	private final TimeUnit retryIntervalUnit;
	private final Log log;
	private final ExceptionHandler exceptionHandler;

	private WindowsExeSigner(Signer signer, boolean continueOnFail, Log log, int maxRetry, int retryInterval, TimeUnit retryIntervalUnit) {
		this.signer = signer;
		this.log = log;
		this.maxRetry = maxRetry;
		this.retryInterval = retryInterval;
		this.retryIntervalUnit = retryIntervalUnit;
		this.exceptionHandler = new ExceptionHandler(log, continueOnFail);
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
		} catch (MojoExecutionExceptionWrapper e) {
			throw e.getCause();
		} catch (IOException e) {
			exceptionHandler.handleError("Error occured while signing Windows binary (" + Strings.join(", ", pathMatchers) + ")", e);
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
    		exceptionHandler.handleError("Path '" + file.toString() + "' does not exist or is not a file. It won't be signed.");
    		return false;
    	} else if (!Files.isWritable(file)) {
    		exceptionHandler.handleError("Path '" + file.toString() + "' is not writable. It won't be signed.");
    		return false;
    	}
    	
    	boolean ret = false; 
        try {
        	log.info("[" + new Date() + "] Signing Windows executable '" + file + "'...");
            if (!signer.sign(file, maxRetry, retryInterval, retryIntervalUnit)) {
            	exceptionHandler.handleError("Signing of Windows executable '" + file + "' failed. Activate debug (-X, --debug) to see why.");
            } else {
            	ret = true;
            }
        } catch (IOException e) {
        	exceptionHandler.handleError("Signing of Windows executable '" + file + "' failed.", e);
        }
        return ret;
    }
    
    public static Builder builder(Signer signer) {
    	return new Builder(signer);
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
						throw new MojoExecutionExceptionWrapper(e);
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
	public static class Builder {

		private final Signer signer;
		
		private boolean continueOnFail = false;

		private Log log = new SystemStreamLog();

		private int maxRetry = 0;

		private int waitTimer = 0;

		private TimeUnit waitTimerUnit = TimeUnit.SECONDS;

		Builder(Signer signer) {
			this.signer = Objects.requireNonNull(signer);
		}
		
		/**
		 * Configure the {@link WindowsExeSigner} to <strong>not</strong> throw
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
		 * The maximum number of retry that will be passed to the {@link Signer}.
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
		 * The time to wait between each try (passed to the {@link Signer}).
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
		 * @return a new {@link WindowsExeSigner}.
		 */
		public WindowsExeSigner build() {
			return new WindowsExeSigner(this.signer, this.continueOnFail, 
					this.log, this.maxRetry, this.waitTimer, this.waitTimerUnit);
		}
	}
}
