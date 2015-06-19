/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.cbi.common.http.HttpPostFileSender;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.maven.common.ExceptionHandler;
import org.eclipse.cbi.maven.common.MojoExecutionExceptionWrapper;

/**
 * Utility class that signs a Jar and the nested Jar.
 */
public class JarSigner {

	private static final String PART_NAME = "file";

	/**
	 * {@code eclispe.inf} property to exclude this Jar from signing.
	 */
    private static final String JARPROCESSOR_EXCLUDE_SIGN = "jarprocessor.exclude.sign";

    /**
     * {@code eclispe.inf} property to exclude this Jar from processing (and thus signing).
     */
	private static final String JARPROCESSOR_EXCLUDE = "jarprocessor.exclude";

	/**
	 * Path of the {@code eclispe.inf} entry in a Jar
	 */
	private static final String META_INF_ECLIPSE_INF = "META-INF/eclipse.inf";

	/**
	 * Jar file extension.
	 */
	private static final String DOT_JAR_GLOB_PATTERN = "glob:**.jar";
	
	/**
	 * The log on which feedback will be provided.
	 */
	private final Log log;
	
	/**
	 * The signer to which the Jar will be send for signing.
	 */
	private final HttpPostFileSender signer;

	/**
	 * The retry limit that will be passed to the {@link HttpPostFileSender#trySign(Path, int, int, TimeUnit)}.
	 */
	private final int retryLimit;

	/**
	 * The retry timer that will be passed to the {@link HttpPostFileSender#trySign(Path, int, int, TimeUnit)}.
	 */
	private final int retryTimer;

	/**
	 * The retry timer time unit that will be passed to the {@link HttpPostFileSender#trySign(Path, int, int, TimeUnit)}.
	 */
	private final TimeUnit retryTimerUnit;

	/**
	 * Whether the nested Jars should be signed (when >= 1). Signs all nested Jars recursively 
	 * when {@link Integer#MAX_VALUE}. Set to 0 if you want to avoid signing nested Jars.
	 */
	private final int maxDepth;

	private final ExceptionHandler exceptionHandler;

	/**
	 * Default constructor. 
	 * 
	 * @param signer
	 * @param maxdepth
	 * @param continueOnFail Whether exceptions should be thrown when the signing process of this Jar or one of its nested Jar (if {@code maxDepth} is >= 1) fails.
	 * @param log
	 * @param retryLimit
	 * @param retryTimer
	 * @param retryTimerUnit
	 */
	private JarSigner(HttpPostFileSender signer, int maxdepth, boolean continueOnFail, Log log, int retryLimit, int retryTimer, TimeUnit retryTimerUnit) {
		this.signer = signer;
		this.maxDepth = maxdepth;
		this.log = log;
		this.retryLimit = retryLimit;
		this.retryTimer = retryTimer;
		this.retryTimerUnit = retryTimerUnit;
		this.exceptionHandler = new ExceptionHandler(log, continueOnFail);
	}
	
	/**
	 * Sign the given Jar file.
	 * 
	 * @param jarfile
	 *            the file to sign.
	 * @return the number of Jar that has been signed.
	 * @throws MojoExecutionException
	 */
	public int signJar(Path jarfile) throws MojoExecutionException {
		return signJar(jarfile, 0);
	}
	
	/**
	 * Sign the given Jar and its nested Jar if it {@link #shouldBeSigned(Path)}.
	 * 
	 * @param file
	 *            the file to sign
	 * @param currentDepth
	 *            the current nesting depth of this Jar
	 * @return the number of Jar that has been signed. 
	 * @throws MojoExecutionException
	 */
	private int signJar(Path file, int currentDepth) throws MojoExecutionException {
		int ret = 0;
		try {
			if (shouldBeSigned(file, currentDepth)) {
				if (currentDepth == 0) {
					log.info("[" + new Date() + "] Signing JAR '" + file + "'...");
				}
				ret = signJarRecursively(file, currentDepth);
			}
		} catch (IOException e) {
			exceptionHandler.handleError("Signing of file '" + file + "' failed.", e);
		}
		return ret;
	}

	/**
	 * Checks and returns whether the given file should be signed. The condition are:
	 * <ul>
	 * <li>the file is a readable file with the {@code .jar} file extension.</li>
	 * <li>the file Jar does not have a entry {@value #META_INF_ECLIPSE_INF} with the either
	 * the properties {@value #JARPROCESSOR_EXCLUDE_SIGN} or
	 * {@value #JARPROCESSOR_EXCLUDE}</li>
	 * </ul>
	 * 
	 * @param file the file to check
	 * @param currentDepth 
	 * @return true if the file should be signed, false otherwise.
	 * @throws IOException
	 */
	private boolean shouldBeSigned(Path file, int currentDepth) throws IOException {
		final boolean ret;

		if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
			log.debug("Could not read file '" + file + "', it will not be signed");
			ret = false;
		} else if (!file.getFileSystem().getPathMatcher(DOT_JAR_GLOB_PATTERN).matches(file)) {
			if (currentDepth == 0) {
				log.debug("Extension of file '" + file + "' is not 'jar', it will not be signed");
			}
			ret = false;
		} else if (isDisabledInEclipseInf(file)) {
			log.info("Signing of file '" + file + "' is disabled in '" + META_INF_ECLIPSE_INF + "', it will not be signed.");
			ret = false;
		} else {
			ret = true;
		}

		return ret;
	}

	/**
	 * Checks and returns whether the given Jar file has signing disabled by an
	 * file {@value #META_INF_ECLIPSE_INF} with the either the properties
	 * {@value #JARPROCESSOR_EXCLUDE_SIGN} or {@value #JARPROCESSOR_EXCLUDE}.
	 * 
	 * @param file
	 * @return true if it finds a property that excludes this file for signing.
	 * @throws IOException
	 */
	@SuppressWarnings("static-method")
	private boolean isDisabledInEclipseInf(final Path file) throws IOException {
		boolean isDisabled = false;

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file))) {
			boolean found = false;
			for(ZipEntry entry = zis.getNextEntry(); !found && entry != null; entry = zis.getNextEntry()) {
				if (META_INF_ECLIPSE_INF.equals(entry.getName())) {
					found = true;
					Properties eclipseInf = new Properties();
					eclipseInf.load(zis);
					
					isDisabled = Boolean.parseBoolean(eclipseInf.getProperty(JARPROCESSOR_EXCLUDE))
							|| Boolean.parseBoolean(eclipseInf.getProperty(JARPROCESSOR_EXCLUDE_SIGN));
				}
			}
		}

		return isDisabled;
	}

	/**
	 * Sign this Jar and its nested Jar.
	 * 
	 * @param file
	 *            the file to sign
	 * @param currentDepth
	 *            the current nesting depth of the current file.
	 * @param signedFile 
	 * @return the number of Jar that has been signed.
	 * @throws IOException
	 * @throws MojoExecutionException
	 */
	private int signJarRecursively(Path file, int currentDepth) throws IOException, MojoExecutionException {
		int nestedJarsSigned = 0;
		if (currentDepth >= maxDepth) {
			log.info("Signing of nested jars of '" + file + "' is disabled.");
		} else {
			nestedJarsSigned = signNestedJars(file, currentDepth);
		}

		if (!signer.post(file, PART_NAME, retryLimit, retryTimer, retryTimerUnit)) {
			exceptionHandler.handleError("Signing of jar '" + file + "' failed. Activate debug (-X, --debug) to see why.");
			return nestedJarsSigned;
		} else {
			return nestedJarsSigned  + 1;
		}
	}

	/**
	 * Signs the inner jars in the given jar file
	 *
	 * @param file
	 *            jar file containing inner jars to be signed
	 * @param currentDepth 
	 * @param innerJars
	 *            A list of inner jars that needs to be signed
	 * @return the number of Jar that has been signed.
	 */
	private int signNestedJars(Path file, int currentDepth) throws MojoExecutionException {
		int numberOfSignedNestedJar = 0;
		Path jarUnpackFolder = null;
		Path tmpSignedJar = null;
		try {
			jarUnpackFolder = Files.createTempDirectory(Paths.getParent(file), file.getFileName().toString() + "_unpacked_");
			Zips.unpackJar(file, jarUnpackFolder);

			// sign inner jars
			NestedJarSigner nestedJarSigner = new NestedJarSigner(currentDepth);
			Files.walkFileTree(jarUnpackFolder, nestedJarSigner);
			
			// rejaring with the signed inner jars
			Zips.packJar(jarUnpackFolder, file, false);
			
			numberOfSignedNestedJar = nestedJarSigner.getNumberOfSignedNestedJar();
		} catch (MojoExecutionExceptionWrapper e) {
			throw e.getCause();
		} catch (IOException e) {
			exceptionHandler.handleError("Signing of nested jar '" + file + "' failed.", e);
		} finally {
			if (jarUnpackFolder != null) {
				Paths.deleteQuietly(jarUnpackFolder);
			}
			
			if (tmpSignedJar != null) {
				Paths.deleteQuietly(tmpSignedJar);
			}
		}
		return numberOfSignedNestedJar;
	}
	
	private final class NestedJarSigner extends SimpleFileVisitor<Path> {

		private final int currentDepth;

		private int numberOfSignedNestedJar;
		
		NestedJarSigner(int currentDepth) {
			this.currentDepth = currentDepth;
			this.numberOfSignedNestedJar = 0;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			try {
				numberOfSignedNestedJar += signJar(file, currentDepth+1);
			} catch (MojoExecutionException e) {
				// wrap exception (to be unwrapped later).
				throw new MojoExecutionExceptionWrapper(e);
			}
			return FileVisitResult.CONTINUE;
		}
		
		public int getNumberOfSignedNestedJar() {
			return numberOfSignedNestedJar;
		}
	}
	
	/**
	 * Creates a builder of {@link JarSigner}.
	 * 
	 * @param signer
	 *            the signer to delegate to.
	 * @return the builder of {@link JarSigner}.
	 */
	public static JarSigner.Builder builder(HttpPostFileSender signer) {
		return new Builder(signer);
	}
	
	/**
	 * A builder of {@link JarSigner}. Default value for options are:
	 * <ul>
	 * <li>{@link #continueOnFail()}: false</li>
	 * <li>{@link #logOn(Log)}: {@link SystemStreamLog}</li>
	 * <li>{@link #maxDepth(int)}: 0</li>
	 * <li>{@link #maxRetry(int)}: 0</li>
	 * <li>{@link #waitBeforeRetry(int, TimeUnit)}: 0 {@link TimeUnit#SECONDS seconds}</li>
	 * <ul> 
	 */
	public static class Builder {

		private final HttpPostFileSender signer;
		
		private boolean continueOnFail = false;

		private Log log = new SystemStreamLog();

		private int maxRetry = 0;

		private int waitTimer = 0;

		private TimeUnit waitTimerUnit = TimeUnit.SECONDS;

		private int maxDepth = 0;

		Builder(HttpPostFileSender signer) {
			this.signer = Objects.requireNonNull(signer);
		}
		
		/**
		 * Configure the {@link JarSigner} to <strong>not</strong> throw
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
		 * The maximum depth of nested Jars that should be signed. If 0 is passed, only the given Jar will signed.
		 * @param maxDepth
		 * @return this builder for chained calls.
		 */
		public Builder maxDepth(int maxDepth) {
			if (maxDepth < 0) {
				throw new IllegalArgumentException("'maxDepth' must be positive or snull");
			}
			this.maxDepth = maxDepth;
			return this;
		}
		
		/**
		 * The maximum number of retry that will be passed to the {@link HttpPostFileSender}.
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
		 * The time to wait between each try (passed to the {@link HttpPostFileSender}).
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
		 * @return a new JarSigner.
		 */
		public JarSigner build() {
			return new JarSigner(this.signer, this.maxDepth, this.continueOnFail, 
					this.log, this.maxRetry, this.waitTimer, this.waitTimerUnit);
		}
	}
}
