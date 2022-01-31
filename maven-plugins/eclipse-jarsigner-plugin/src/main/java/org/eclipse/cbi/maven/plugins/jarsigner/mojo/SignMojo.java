/*******************************************************************************
 * Copyright (c) 2012, 2021 Eclipse Foundation and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Eclipse Foundation - initial API and implementation
 *   Thanh Ha (Eclipse Foundation) - Add support for signing inner jars
 *   Mikael Barbero - Use of "try with resource"
 *   Christoph Läubrich - support signing a file/directory of files instead of attached artifacts 
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.jarsigner.mojo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Strings;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;
import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.common.security.SignatureAlgorithm;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.RetryHttpClient;
import org.eclipse.cbi.maven.http.apache.ApacheHttpClient;
import org.eclipse.cbi.maven.plugins.jarsigner.EclipseJarSignerFilter;
import org.eclipse.cbi.maven.plugins.jarsigner.JarResigner;
import org.eclipse.cbi.maven.plugins.jarsigner.JarResigner.Strategy;
import org.eclipse.cbi.maven.plugins.jarsigner.JarSigner;
import org.eclipse.cbi.maven.plugins.jarsigner.JarSigner.Options;
import org.eclipse.cbi.maven.plugins.jarsigner.RecursiveJarSigner;
import org.eclipse.cbi.maven.plugins.jarsigner.RemoteJarSigner;

/**
 * Signs project main and attached artifacts using the Eclipse jarsigner
 * webservice. Only artifacts with {@code .jar} extension are signed, other
 * artifacts are not signed but a warning message is logged.
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

	/**
	 * The Maven project.
	 */
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	/**
	 * The signing service URL for signing Jar files. This service should return
	 * a signed jar file.
	 *
	 * @since 1.0.4
	 */
	@Parameter(required = true, property = "cbi.jarsigner.signerUrl", defaultValue = "https://cbi.eclipse.org/jarsigner/sign")
	private String signerUrl;

	/**
	 * Whether the execution of this plugin should be skipped.
	 *
	 * @since 1.0.4
	 */
	@Parameter(property = "cbi.jarsigner.skip", defaultValue = "false")
	private boolean skip;

	/**
	 * Whether the build should be stopped if the signing process fails.
	 *
	 * @since 1.0.5 (for the user property, since 1.1.3 for the parameter).
	 * @deprecated The user property {@code continueOnFail} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.jarsigner.continueOnFail} instead. The
	 *             {@code deprecatedContinueOnFail} parameter has been
	 *             introduced to support this deprecated user property for
	 *             backward compatibility only.
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
	@Parameter(property = "cbi.jarsigner.continueOnFail", defaultValue = "false")
	private boolean continueOnFail;

	/**
	 * Number of times to retry signing if the server fails to sign.
	 *
	 * @since 1.1.0 (for the property, since 1.1.3 for the parameter)
	 * @deprecated The user property {@code retryLimit} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.jarsigner.retryLimit} instead. The
	 *             {@code deprecatedRetryLimit} parameter has been introduced to
	 *             support this deprecated user property for backward
	 *             compatibility only.
	 */
	@Deprecated
	@Parameter(property = "retryLimit", defaultValue = DEFAULT_RETRY_LIMIT_STRING)
	private int deprecatedRetryLimit;

	/**
	 * Number of times to retry signing if the server fails to sign.
	 *
	 * @since 1.1.0 (for the parameter, since 1.1.3 for the qualified user user
	 *        property)
	 */
	@Parameter(property = "cbi.jarsigner.retryLimit", defaultValue = DEFAULT_RETRY_LIMIT_STRING)
	private int retryLimit;

	/**
	 * Number of seconds to wait before retrying to sign.
	 *
	 * @since 1.1.0 (for the user property, since 1.1.3 for the parameter).
	 * @deprecated The user property {@code retryTimer} is deprecated. You
	 *             should use the qualified property
	 *             {@code cbi.jarsigner.retryTimer} instead. The
	 *             {@code deprecatedRetryTimer} parameter has been introduced to
	 *             support this deprecated user property for backward
	 *             compatibility only.
	 */
	@Deprecated
	@Parameter(property = "retryTimer", defaultValue = DEFAULT_RETRY_TIMER_STRING)
	private int deprecatedRetryTimer;

	/**
	 * Number of seconds to wait before retrying to sign.
	 *
	 * @since 1.1.0 (for the parameter, since 1.1.3 for the qualified user user
	 *        property)
	 */
	@Parameter(property = "cbi.jarsigner.retryTimer", defaultValue = DEFAULT_RETRY_TIMER_STRING)
	private int retryTimer;

	/**
	 * Whether to excludes signing inner jars (not recursive, only apply to
	 * first level Jars inside the build Jar file; deeper jars are ignored in
	 * all cases).
	 *
	 * @since 1.0.5
	 */
	@Parameter(defaultValue = "false")
	private boolean excludeInnerJars;

	/**
	 * Project types which this plugin supports.
	 *
	 * @deprecated Not used anymore.
	 */
	@Deprecated
	@Parameter
	private List<String> supportedProjectTypes = Arrays.asList("jar", "war", "bundle", "maven-plugin",
			"eclipse-plugin", "eclipse-test-plugin", "eclipse-feature");

	/**
	 * The strategy to be used if the artifacts of the current project are
	 * already signed (e.g., when
	 * <a href="https://wiki.eclipse.org/Tycho/Reproducible_Version_Qualifiers">
	 * replaced with a baseline version</a>). Valid values are:
	 * <ul>
	 * <li><strong>DO_NOT_RESIGN</strong>, do nothing with the jar file</li>
	 * <li><strong>THROW_EXCEPTION</strong>, throws an exception and stop the
	 * build if {@link #continueOnFail} property is not set</li>
	 * <li><strong>RESIGN</strong>, resigns the jar with the same parameter as
	 * if it was not already signed (in particular the configured
	 * {@link #digestAlgorithm})</li>
	 * <li><strong>RESIGN_WITH_SAME_DIGEST_ALGORITHM</strong>, resigns the jar
	 * with the same digest algorithm as the one used when it has been
	 * previously signed. Thus, the {@link #digestAlgorithm} is ignored for the
	 * already signed jars.</li>
	 * <li><strong>OVERWRITE</strong>, removes every signatures from the jar and
	 * resigned it with the same parameter as if it was not already signed (in
	 * particular the configured {@link #digestAlgorithm})</li>
	 * <li><strong>OVERWRITE_WITH_SAME_DIGEST_ALGORITHM</strong>, removes every
	 * signatures from the jar and resign it with the same digest algorithm as
	 * the one used when it has been previously signed. Thus, the
	 * {@link #digestAlgorithm} is ignored for the already signed jars.</li>
	 * </ul>
	 * 
	 * @since 1.1.3
	 */
	@Parameter(property = "cbi.jarsigner.resigningStrategy", defaultValue = "RESIGN")
	private Strategy resigningStrategy;

	/**
	 * The digest algorithm to use for signing the jar file. Supported values
	 * depends on the remote signing web services. Values recognized by this
	 * plugin are:
	 * <ul>
	 * <li><strong>DEFAULT</strong>, tells to the remote signing webservice to use its default
	 * digest algorithm to sign the jar</li>
	 * <li><strong>MD2</strong></li>
	 * <li><strong>MD5</strong></li>
	 * <li><strong>SHA_1</strong></li>
	 * <li><strong>SHA1</strong> Use this value if you need to be compatible with some old frameworks (e.g., Eclipse Equinox 3.7 / Indigo). Use SHA_1 otherwise.</li>
	 * <li><strong>SHA_224</strong></li>
	 * <li><strong>SHA_256</strong></li>
	 * <li><strong>SHA_384</strong></li>
	 * <li><strong>SHA_512</strong></li>
	 * </ul>
	 * 
	 * @since 1.1.3
	 */
	@Parameter(property = "cbi.jarsigner.digestAlgorithm", defaultValue = "DEFAULT")
	private MessageDigestAlgorithm digestAlgorithm;
	
	/**
	 * The signature algorithm to use for signing the jar file. Supported values
	 * depends on the remote signing web services. Values recognized by this
	 * plugin are:
	 * <ul>
	 * <li><strong>DEFAULT</strong>, tells to the remote signing webservice to use its default
	 * digest algorithm to sign the jar</li>
	 * 
	 * <li><strong>NONEwithRSA</strong></li>
	 * <li><strong>MD2withRSA</strong></li>
	 * <li><strong>MD5withRSA</strong></li>
	 * 
	 * <li><strong>SHA1withRSA</strong></li>
	 * <li><strong>SHA224withRSA</strong></li>
	 * <li><strong>SHA256withRSA</strong></li>
	 * <li><strong>SHA384withRSA</strong></li>
	 * <li><strong>SHA512withRSA</strong></li>
	 * 
	 * <li><strong>SHA1withDSA</strong></li>
	 * <li><strong>SHA224withDSA</strong></li>
	 * <li><strong>SHA256withDSA</strong></li>
	 * 
	 * <li><strong>NONEwithECDSA</strong></li>
	 * <li><strong>SHA1withECDSA</strong></li>
	 * <li><strong>SHA224withECDSA</strong></li>
	 * <li><strong>SHA256withECDSA</strong></li>
	 * <li><strong>SHA384withECDSA</strong></li>
	 * <li><strong>SHA512withECDSA</strong></li>
	 * </ul>
	 * 
	 * @since 1.1.3
	 */
	@Parameter(property = "cbi.jarsigner.signatureAlgorithm", defaultValue = "DEFAULT")
	private SignatureAlgorithm signatureAlgorithm;

	/**
	 * Defines the timeout in milliseconds for establishing a TCP connection with the signing server.
   * 
   * A timeout value of zero is interpreted as an infinite timeout.
	 * 
	 * @since 1.1.4
	 * @deprecated Use timeoutMillis instead. This one is for establishing the TCP connection only, you may 
	 * be looking for a wall timeout instead.
	 */
	@Deprecated
	@Parameter(property = "cbi.jarsigner.connectTimeoutMillis", defaultValue = "5000")
	private int connectTimeoutMillis;
	
	/**
	 * Defines the wall timeout in milliseconds for performing the remote request.
   * 
   * A timeout value of zero is interpreted as an infinite timeout.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.jarsigner.timeoutMillis", defaultValue = "0")
	private int timeoutMillis;
	
	/**
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.jarsigner.sigFile", defaultValue = "")
	private String sigFile;

	@Parameter(property = "cbi.jarsigner.archiveDirectory")
	private File archiveDirectory;

	@Parameter
	private String[] includes = { "**/*.?ar" };

	@Parameter(property = "cbi.jarsigner.processMainArtifact", defaultValue = "true")
	private boolean processMainArtifact;

	@Parameter(property = "cbi.jarsigner.processAttachedArtifacts", defaultValue = "true")
	private boolean processAttachedArtifacts;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute() throws MojoExecutionException {
		if (skip) {
			getLog().info("Skip jar signing");
			return;
		}

		if (project.getArtifact() == null && project.getAttachedArtifacts().isEmpty()) {
			getLog().info("No jars to sign");
		} else {
			final JarSigner jarSigner = createJarSigner();
			if (processMainArtifact) {
				final Artifact mainArtifact = project.getArtifact();
				if (mainArtifact != null) {
					signArtifact(jarSigner, mainArtifact);
				}
			}
			if (processAttachedArtifacts) {
				for (Artifact artifact : project.getAttachedArtifacts()) {
					signArtifact(jarSigner, artifact);
				}
			}
			if (archiveDirectory != null && includes != null && includes.length > 0) {
				try {
					List<File> jarFiles = FileUtils.getFiles(archiveDirectory, String.join(",", includes), "");
					for (File jarFile : jarFiles) {
						signArtifact(jarSigner, jarFile);
					}
				} catch (IOException e) {
					throw new MojoExecutionException("Failed to scan archive directory for JARs: " + e.getMessage(), e);
				}

			}
		}
	}

	private void signArtifact(final JarSigner jarSigner, final Artifact artifact) throws MojoExecutionException {
		File artifactFile = artifact.getFile();
		if (artifactFile != null) {
			signArtifact(jarSigner, artifactFile);
		} else {
			getLog().debug("No file is associated with artifact '" + artifact.toString() + "'");
		}
	}

	private void signArtifact(final JarSigner jarSigner, final File jarFile) throws MojoExecutionException {

			try {
				if (new EclipseJarSignerFilter(getLog()).shouldBeSigned(jarFile.toPath())) {
					Options options = Options.builder()
							.signatureAlgorithm(signatureAlgorithm)
							.digestAlgorithm(digestAlgorithm)
							.connectTimeout(Duration.ofMillis(connectTimeoutMillis))
							.timeout(Duration.ofMillis(timeoutMillis))
							.sigFile(Strings.nullToEmpty(sigFile))
							.build();
					if (jarSigner.sign(jarFile.toPath(), options) == 0) {
						new ExceptionHandler(getLog(), continueOnFail())
						.handleError("Failed to sign jar file '" + jarFile.toString());
					} 
				}
			} catch (IOException e) {
				new ExceptionHandler(getLog(), continueOnFail())
						.handleError("Unable to sign jar '" + jarFile.toString() + "'", e);
			}

	}

	private boolean continueOnFail() {
		return deprecatedContinueOnFail || continueOnFail;
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

	/**
	 * Creates and returns the {@link JarSigner} according to the injected Mojo
	 * parameter.
	 *
	 * @return the {@link JarSigner} according to the injected Mojo parameter.
	 */
	private JarSigner createJarSigner() {
		HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
				.log(new MavenLogger(getLog())).maxRetries(retryLimit()).waitBeforeRetry(retryTimer(), TimeUnit.SECONDS)
				.build();

		return RecursiveJarSigner.builder().filter(new EclipseJarSignerFilter(getLog())).log(getLog())
				.maxDepth(excludeInnerJars ? 0 : 1)
				.delegate(
						JarResigner
								.create(resigningStrategy,
										RemoteJarSigner.builder().httpClient(httpClient)
												.serverUri(URI.create(signerUrl)).log(getLog()).build(),
										getLog()))
				.build();
	}
}
