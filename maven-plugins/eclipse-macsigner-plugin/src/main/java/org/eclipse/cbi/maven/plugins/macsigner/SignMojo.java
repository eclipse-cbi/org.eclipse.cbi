/*******************************************************************************
 * Copyright (c) 2013, 2015 Eclipse Foundation and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Caroline McQuatt, Mike Lim - initial implementation
 *   Mikael Barbero - refactoring with OSXAppSigner
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.macsigner;

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
 * Signs OS X applications found in the project build directory using the
 * Eclipse OS X application signer webservice.
 */
@Mojo(name = "sign", defaultPhase = LifecyclePhase.PACKAGE)
public class SignMojo extends AbstractMojo {

	/**
	 * The default number of seconds the process will wait if
	 */
	private static final String DEFAULT_RETRY_TIMER_STRING = "10";
	private static final int DEFAULT_RETRY_TIMER = Integer.parseInt(DEFAULT_RETRY_TIMER_STRING);

	private static final String DEFAULT_RETRY_LIMIT_STRING = "3";
	private static final int DEFAULT_RETRY_LIMIT = Integer.parseInt(DEFAULT_RETRY_LIMIT_STRING);

	private static final String ECLIPSE_APP = "Eclipse.app";

	private static final String DOT_APP = ".app";

	/**
	 * The signing service URL for signing OS X applications. The signing service
	 * should return a signed zip file containing the Mac *.app directory.
	 * 
	 * @since 1.0.4
	 */
	@Parameter(required = true, property = "cbi.macsigner.signerUrl", defaultValue = "https://cbi.eclipse.org/macos/codesign/sign")
	private String signerUrl;

	/**
	 * A list of absolute paths to OS X application directories ({@code *.app})
	 * (e.g.,
	 * <code>${project.build.directory}/products/myProduct/macosx/cocoa/x86/eclipse/Eclipse.app</code>
	 * ). If configured only these executables will be signed and
	 * {@code baseSearchDir} and {@code fileNames} will be ignored.
	 *
	 * @since 1.0.4 (for the parameter, since 1.1.3 for the qualified user property)
	 */
	@Parameter(property = "cbi.macsigner.signFiles")
	private Set<String> signFiles;

	/**
	 * A list of absolute paths to the OS X directory {@code *.app} (e.g.,
	 * <code>${project.build.directory}/products/myProduct/macosx/cocoa/x86/eclipse/Eclipse.app</code>
	 * ). If configured only these executables will be signed and
	 * {@code baseSearchDir} and {@code fileNames} will be ignored.
	 *
	 * @deprecated The user property {@code signFiles} is deprecated. You should use
	 *             the qualified property {@code cbi.macsigner.signFiles} instead.
	 *             The {@code deprecatedSignFiles} parameter has been introduced to
	 *             support this deprecated user property for backward compatibility
	 *             only.
	 * @since 1.0.4 (for the user property, since 1.1.3 for the parameter).
	 */
	@Deprecated
	@Parameter(property = "signFiles")
	private Set<String> deprecatedSignFiles;

	@Parameter(property = "cbi.macsigner.entitlements")
	private String entitlements;

	/**
	 * The base directory to search for applications to sign.
	 *
	 * @since 1.0.4 (for the parameter, since 1.1.3 for the qualified user property)
	 */
	@Parameter(property = "cbi.macsigner.baseSearchDir", defaultValue = "${project.build.directory}/products/")
	private String baseSearchDir;

	/**
	 * The base directory to search for applications to sign.
	 *
	 * @deprecated The user property {@code baseSearchDir} is deprecated. You should
	 *             use the qualified property {@code cbi.macsigner.baseSearchDir}
	 *             instead. The {@code deprecatedBaseSearchDir} parameter has been
	 *             introduced to support this deprecated user property for backward
	 *             compatibility only.
	 * @since 1.0.4 (for the user property, since 1.1.3 for the parameter).
	 */
	@Deprecated
	@Parameter(property = "baseSearchDir", defaultValue = "${project.build.directory}/products/")
	private String deprecatedBaseSearchDir;

	/**
	 * A list of {@code *.app} application folder to sign.
	 *
	 * <p>
	 * Default value is <code>{{@value #ECLIPSE_APP}}</code> is signed.
	 * </p>
	 *
	 * @since 1.0.4 (for the parameter, since 1.1.3 for the qualified user property)
	 */
	@Parameter(property = "cbi.macsigner.fileNames")
	private Set<String> fileNames;

	/**
	 * A list of {@code *.app} application folder name to sign. Default value is
	 * <code>{"Eclipse.app"}</code>.
	 * </p>
	 *
	 * @deprecated The user property {@code fileNames} is deprecated. You should use
	 *             the qualified property {@code cbi.macsigner.fileNames} instead.
	 *             The {@code deprecatedFileNames} parameter has been introduced to
	 *             support this deprecated user property for backward compatibility
	 *             only.
	 * @since 1.0.4 (for the user property, since 1.1.3 for the parameter).
	 */
	@Deprecated
	@Parameter(property = "fileNames")
	private Set<String> deprecatedFileNames;

	/**
	 * Whether the build should be stopped if the signing process fails.
	 *
	 * @since 1.0.5 (for the parameter, since 1.1.3 for the qualified user property)
	 */
	@Parameter(property = "cbi.macsigner.continueOnFail")
	private boolean continueOnFail;

	/**
	 * Whether the build should be stopped if the signing process fails.
	 * 
	 * @deprecated The user property {@code continueOnFail} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.macsigner.continueOnFail} instead. The
	 *             {@code deprecatedContinueOnFail} parameter has been introduced to
	 *             support this deprecated user property for backward compatibility
	 *             only.
	 * @since 1.0.5 (for the user property, since 1.1.3 for the parameter).
	 */
	@Deprecated
	@Parameter(property = "continueOnFail", defaultValue = "false")
	private boolean deprecatedContinueOnFail;

	/**
	 * Number of times to retry signing if server fails to sign
	 *
	 * @since 1.1.0 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.macsigner.retryLimit", defaultValue = "3")
	private int retryLimit;

	/**
	 * Number of times to retry signing if server fails to sign
	 *
	 * @deprecated The user property {@code retryLimit} is deprecated. You should
	 *             use the qualified property {@code cbi.macsigner.retryLimit}
	 *             instead. The {@code deprecatedRetryLimit} parameter has been
	 *             introduced to support this deprecated user property for backward
	 *             compatibility only.
	 * @since 1.1.0 (for the user property, since 1.1.3 for the parameter).
	 */
	@Parameter(property = "retryLimit", defaultValue = "3")
	private int deprecatedRetryLimit;

	/**
	 * Number of seconds to wait before retrying to sign
	 *
	 * @since 1.1.0 (for the parameter, since 1.1.3 for the qualified user
	 *        property).
	 */
	@Parameter(property = "cbi.macsigner.retryTimer", defaultValue = "10")
	private int retryTimer;

	/**
	 * Number of seconds to wait before retrying to sign
	 *
	 * @deprecated The user property {@code retryTimer} is deprecated. You should
	 *             use the qualified property {@code cbi.macsigner.retryTimer}
	 *             instead. The {@code deprecatedRetryTimer} parameter has been
	 *             introduced to support this deprecated user property for backward
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
	@Parameter(property = "cbi.macsigner.timeoutMillis", defaultValue = "0")
	private int timeoutMillis;

	@Override
	public void execute() throws MojoExecutionException {
		HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
				.maxRetries(retryLimit()).waitBeforeRetry(retryTimer(), TimeUnit.SECONDS).log(new MavenLogger(getLog()))
				.build();
		ExceptionHandler exceptionHandler = new ExceptionHandler(getLog(), continueOnFail());
		OSXAppSigner osxAppSigner = OSXAppSigner.builder().serverUri(URI.create(signerUrl)).httpClient(httpClient)
				.timeout(Duration.ofMillis(timeoutMillis)).exceptionHandler(exceptionHandler)
				.log(getLog()).build();

		Path entitlementsFile = entitlements == null ? null : FileSystems.getDefault().getPath(entitlements);

		Set<Path> filesToSign = Collections.emptySet();
		if (signFiles() != null && !signFiles().isEmpty()) {
			// app paths are configured
			filesToSign = signFiles().stream().map(FileSystems.getDefault()::getPath).collect(Collectors.toSet());
		} else {
			// perform search
			Set<PathMatcher> pathMatchers = createPathMatchers(FileSystems.getDefault(), fileNames(), getLog());
			try (Stream<Path> walk = Files.walk(FileSystems.getDefault().getPath(baseSearchDir()))) {
				filesToSign = walk.filter(path -> pathMatchers.stream()
					.anyMatch(matcher -> matcher.matches(path)))
					.collect(Collectors.toSet()
				);
			} catch (IOException e) {
				exceptionHandler.handleError("An error happened while searching for app to be signed in " + baseSearchDir(), e);
			}
		}

		int signedApps = osxAppSigner.signApplications(filesToSign, entitlementsFile);
		if (signedApps < filesToSign.size()) {
			exceptionHandler.handleError(signedApps + " app(s) were signed, while " + filesToSign.size() + "  have been found and were supposed to be signed");
		}
	}

	private Set<String> fileNames() {
		return Sets.union(fileNames, deprecatedFileNames);
	}

	private String baseSearchDir() {
		return baseSearchDir != null ? baseSearchDir : deprecatedBaseSearchDir;
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
			pathMatchers.add(fs.getPathMatcher("glob:**" + fs.getSeparator() + ECLIPSE_APP));
		} else {
			for (String filename : fileNames) {
				if (!filename.endsWith(DOT_APP)) {
					log.warn("The given file name '" + filename
							+ "' does not end with '.app' extension. No corresponding application will be signed.");
				} else {
					pathMatchers.add(fs.getPathMatcher("glob:**" + filename));
				}
			}
		}
		return pathMatchers;
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
}
