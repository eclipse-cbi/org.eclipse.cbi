/*******************************************************************************
 * Copyright (c) 2013, 2015 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Caroline McQuatt, Mike Lim - initial implementation
 *   Mikael Barbero - refactoring with WindowsExeSigner
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.winsigner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Sets;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.RetryHttpClient;
import org.eclipse.cbi.maven.http.apache.ApacheHttpClient;

/**
 * Signs executables found in the project build directory using the
 * Eclipse Windows executable signer webservice.
 */
@Mojo(name = "sign", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class SignMojo extends AbstractMojo {

	/**
	 * The default number of seconds the process will wait if
	 */
	private static final String DEFAULT_RETRY_TIMER_STRING = "10";
	private static final int DEFAULT_RETRY_TIMER = Integer.parseInt(DEFAULT_RETRY_TIMER_STRING);

	private static final String DEFAULT_RETRY_LIMIT_STRING = "3";
	private static final int DEFAULT_RETRY_LIMIT = Integer.parseInt(DEFAULT_RETRY_LIMIT_STRING);
	
	/**
	 * Default eclipsec executable name
	 */
	private static final String ECLIPSEC_EXE = "eclipsec.exe";

	/**
	 * Default eclipse executable name
	 */
	private static final String ECLIPSE_EXE = "eclipse.exe";
	
	/**
	 * The signing service URL for signing Windows binaries. The signing service
	 * should return a signed exe file.
	 * 
	 * @since 1.0.4
	 */
	@Parameter(required = true, property = "cbi.winsigner.signerUrl", defaultValue = "https://cbi.eclipse.org/authenticode/sign")
	private String signerUrl;

	/**
	 * The list of <b>absolute</b> paths of executables to be signed. If
	 * configured, only these executables will be signed and the parameters
	 * {@code baseSearchDir} and {@code fileNames} will be ignored.
	 * 
	 * @deprecated The user property {@code signFiles} is deprecated. You should
	 *             use the qualified property {@code cbi.winsigner.signFiles}
	 *             instead. The {@code deprecatedSignFiles} parameter has been
	 *             introduced to support this deprecated user property for
	 *             backward compatibility only.
	 * @since 1.0.4 (for the user property, since 1.1.3 for the parameter)
	 */
	@Deprecated
	@Parameter(property = "signFiles")
	private Set<String> deprecatedSignFiles;
	
	/**
	 * The list of <b>absolute</b> paths of executables to be signed. If
	 * configured, only these executables will be signed and the parameters
	 * {@code baseSearchDir} and {@code fileNames} will be ignored.
	 * 
	 * @since 1.0.4 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.winsigner.signFiles")
	private Set<String> signFiles;

	/**
	 * The base directory to search for executables to sign. The executable name
	 * to search for can be configured with parameter {@code fileNames}. This
	 * parameter is ignored if {@link #signFiles} is set.
	 * 
	 * @deprecated The user property {@code baseSearchDir} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.winsigner.baseSearchDir} instead. The
	 *             {@code deprecatedBaseSearchDir} parameter has been
	 *             introduced to support this deprecated user property for
	 *             backward compatibility only.
	 * @since 1.0.4 (for the user property, since 1.1.3 for the parameter)
	 */
	@Deprecated
	@Parameter(property = "baseSearchDir")
	private String deprecatedBaseSearchDir;

	/**
	 * The base directory to search for executables to sign. The executable name
	 * to search for can be configured with parameter {@code fileNames}. This
	 * parameter is ignored if {@link #signFiles} is set.
	 *
	 * @since 1.0.4 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.winsigner.baseSearchDir", defaultValue = "${project.build.directory}/products/")
	private String baseSearchDir;
	
	/**
	 * List of file names to sign. These file names will be searched in
	 * {@code baseSearchDir}. This parameter is ignored if {@link #signFiles} is
	 * set.
	 * <p>
	 * Default value is <code>{eclipse.exe, eclipsec.exe}</code>.
	 * 
	 * @deprecated The user property {@code fileNames} is deprecated. You should
	 *             use the qualified property {@code cbi.winsigner.fileNames}
	 *             instead. The {@code deprecatedFileNames} parameter has been
	 *             introduced to support this deprecated user property for
	 *             backward compatibility only.
	 * @since 1.0.4 (for the user property, since 1.1.3 for the parameter).
	 */
	@Deprecated
	@Parameter(property = "fileNames")
	private Set<String> deprecatedFileNames;
	
	/**
	 * List of file names to sign. These file names will be searched in
	 * {@code baseSearchDir}. This parameter is ignored if {@link #signFiles} is
	 * set.
	 *<p>
	 * Default value is <code>{"eclipse.exe", "eclipsec.exe"}}</code>.
	 * 
	 * @since 1.0.4 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.winsigner.fileNames")
	private Set<String> fileNames;

	/**
	 * Whether the build should be stopped if the signing process fails.
	 * 
	 * @deprecated The user property {@code continueOnFail} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.winsigner.continueOnFail} instead. The
	 *             {@code deprecatedContinueOnFail} parameter has been
	 *             introduced to support this deprecated user property for
	 *             backward compatibility only.
	 * @since 1.0.5 (for the user property, since 1.1.3 for the parameter).
	 */
	@Deprecated
	@Parameter(property = "continueOnFail", defaultValue = "false")
	private boolean deprecatedContinueOnFail;

	/**
	 * Whether the build should be stopped if the signing process fails.
	 *
	 * @since 1.0.5 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.winsigner.continueOnFail", defaultValue = "false")
	private boolean continueOnFail;

	/**
	 * Number of times to retry signing if server fails to sign
	 *
	 * @since 1.1.0 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.winsigner.retryLimit", defaultValue = "3")
	private int retryLimit;
	
	/**
	 * Number of times to retry signing if server fails to sign
	 *
	 * @deprecated The user property {@code retryLimit} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.winsigner.retryLimit} instead. The
	 *             {@code deprecatedRetryLimit} parameter has been introduced
	 *             to support this deprecated user property for backward
	 *             compatibility only.
	 * @since 1.1.0 (for the user property, since 1.1.3 for the parameter).
	 */
	@Parameter(property = "retryLimit", defaultValue = DEFAULT_RETRY_LIMIT_STRING)
	private int deprecatedRetryLimit;


	/**
	 * Number of seconds to wait before retrying to sign
	 *
	 * @since 1.1.0 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.winsigner.retryTimer", defaultValue = DEFAULT_RETRY_TIMER_STRING)
	private int retryTimer;
	
	/**
	 * Number of seconds to wait before retrying to sign
	 *
	 * @deprecated The user property {@code retryTimer} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.winsigner.retryTimer} instead. The
	 *             {@code deprecatedRetryTimer} parameter has been introduced
	 *             to support this deprecated user property for backward
	 *             compatibility only.
	 * @since 1.1.0 (for the user property, since 1.1.3 for the parameter).
	 */
	@Parameter(property = "retryTimer", defaultValue = "10")
	private int deprecatedRetryTimer;
	
	/**
	 * Defines the wall timeout in milliseconds for performing the remote request.
	 * 
	 * A timeout value of zero is interpreted as an infinite timeout.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.winsigner.timeoutMillis", defaultValue = "0")
	private int timeoutMillis;

	/**
	 * Skips the execution of this plugin.
	 * 
	 * @since 1.5.4
	 */
	@Parameter(property = "cbi.winsigner.skip", defaultValue = "false")
	private boolean skip;

	@Override
	public void execute() throws MojoExecutionException {
		if (skip) {
			getLog().info("Skip Windows signing");
			return;
		}
		HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
			.maxRetries(retryLimit())
			.waitBeforeRetry(retryTimer(), TimeUnit.SECONDS)
			.log(new MavenLogger(getLog()))
			.build();
		ExceptionHandler exceptionHandler = new ExceptionHandler(getLog(), continueOnFail());
		WindowsExeSigner exeSigner = WindowsExeSigner.builder()
			.serverUri(URI.create(signerUrl))
			.httpClient(httpClient)
			.timeout(Duration.ofMillis(timeoutMillis))
			.exceptionHandler(exceptionHandler)
			.log(getLog())
			.build();
		
		Set<Path> exePaths = Collections.emptySet();
		if (signFiles() != null && !signFiles().isEmpty()) {
			//exe paths are configured
			exePaths = signFiles().stream().map(FileSystems.getDefault()::getPath).collect(Collectors.toSet());
		} else { 
			//perform search
			Set<PathMatcher> pathMatchers = createPathMatchers(FileSystems.getDefault(), fileNames(), getLog());
			try (Stream<Path> walk = Files.walk(FileSystems.getDefault().getPath(baseSearchDir()))) {
				exePaths = walk.filter(path -> pathMatchers.stream()
					.anyMatch(matcher -> matcher.matches(path)))
					.collect(Collectors.toSet()
				);
			} catch (IOException e) {
				exceptionHandler.handleError("An error happened while searching for executable to be signed in " + baseSearchDir(), e);
			}
		}

		int signedExecutables = exeSigner.signExecutables(exePaths);
		if (signedExecutables != exePaths.size()) {
			exceptionHandler.handleError(signedExecutables + " executable(s) were signed while we were requesting the signature of " + exePaths.size());
		}
	}

	private String baseSearchDir() {
		return baseSearchDir != null ? baseSearchDir : deprecatedBaseSearchDir;
	}

	private int retryLimit() {
		if (deprecatedRetryLimit != DEFAULT_RETRY_LIMIT && retryLimit == DEFAULT_RETRY_LIMIT) {
			return deprecatedRetryLimit;
		} else {
			return retryLimit;
		}
	}

	private int retryTimer() {
		if (deprecatedRetryTimer != DEFAULT_RETRY_TIMER && retryTimer == DEFAULT_RETRY_TIMER) {
			return deprecatedRetryTimer;
		} else {
			return retryTimer;
		}
	}

	private Set<String> fileNames() {
		return Sets.union(fileNames, deprecatedFileNames);
	}

	private Set<String> signFiles() {
		return Sets.union(signFiles, deprecatedSignFiles);
	}

	private boolean continueOnFail() {
		return continueOnFail || deprecatedContinueOnFail;
	}

    static Set<PathMatcher> createPathMatchers(FileSystem fs, Set<String> fileNames, Log log) {
		final Set<PathMatcher> pathMatchers = new LinkedHashSet<>();
		
		if (fileNames == null || fileNames.isEmpty()) {
			pathMatchers.add(fs.getPathMatcher("glob:**" + fs.getSeparator() + ECLIPSE_EXE));
			pathMatchers.add(fs.getPathMatcher("glob:**" + fs.getSeparator() + ECLIPSEC_EXE));
		} else {
			for (String filename : fileNames) {
				pathMatchers.add(fs.getPathMatcher("glob:**" + filename));
			}
		}
		return pathMatchers;
	}
}
