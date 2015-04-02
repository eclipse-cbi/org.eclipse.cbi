/*******************************************************************************
 * Copyright (c) 2012, 2015 Eclipse Foundation and others. 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
 *   Eclipse Foundation - initial API and implementation 
 *   Thanh Ha (Eclipse Foundation) - Add support for signing inner jars
 *   Mikael Barbero - Use of "try with resource"
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.cbi.common.signing.ApacheHttpClientSigner;
import org.eclipse.cbi.common.signing.Signer;

/**
 * Signs project main and attached artifact using <a href=
 * "http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">
 * Eclipse jarsigner webservice</a>. Only artifacts that have extension
 * ``.jar'', other artifacts are not signed with a debug log message.
 *
 * @goal sign
 * @phase package
 * @requiresProject
 * @description runs the eclipse signing process
 */
public class SignMojo extends AbstractMojo {

	/**
     * Maven Project
     *
     * @parameter property="project"
     * @readonly
     */
    private MavenProject project;

    /**
     * The signing service URL for signing Jar files
     *
     * <p>
     * This service should return a signed jar file.
     * </p>
     *
     * <p>
     * The Official Eclipse signer service URL as described in the <a href=
     * "http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">
     * wiki</a>.
     * </p>
     *
     * <p>
     * <b>Configuration via Maven commandline</b>
     * </p>
     * 
     * <pre>
     * -Dcbi.jarsigner.signerUrl=http://localhost/sign.php
     * </pre>
     *
     * <p>
     * <b>Configuration via pom.xml</b>
     * </p>
     * 
     * <pre>
     * {@code
     * <configuration>
     *   <signerUrl>http://localhost/sign</signerUrl>
     * </configuration>
     * }
     * </pre>
     *
     * @parameter property="cbi.jarsigner.signerUrl"
     *            default-value="http://build.eclipse.org:31338/sign"
     * @required
     * @since 1.0.4
     */
    private String signerUrl;

    /**
     * Maven build directory
     *
     * @parameter property="project.build.directory"
     * @readonly
     * @deprecated not used anymore. Use {@code java.io.tmpdir} property instead. 
     */
    @SuppressWarnings("unused")
	@Deprecated
    private File workdir;

    /**
     * Skips the execution of this plugin
     *
     * @parameter property="cbi.jarsigner.skip" default-value="false"
     * @since 1.0.4
     */
    private boolean skip;

    /**
     * Continue the build even if signing fails
     *
     * <p>
     * <b>Configuration via Maven commandline</b>
     * </p>
     * 
     * <pre>
     * -DcontinueOnFail=true
     * </pre>
     *
     * <p>
     * <b>Configuration via pom.xml</b>
     * </p>
     * 
     * <pre>
     * {@code
     * <configuration>
     *   <continueOnFail>true</continueOnFail>
     * </configuration>
     * }
     * </pre>
     *
     * @parameter property="continueOnFail" default-value="false"
     * @since 1.0.5
     */
    private boolean continueOnFail;

    /**
     * Number of times to retry signing if server fails to sign
     *
     * @parameter property="retryLimit" default-value="3"
     * @since 1.1.0
     */
    private int retryLimit;

    /**
     * Number of seconds to wait before retrying to sign
     *
     * @parameter property="retryTimer" default-value="30"
     * @since 1.1.0
     */
    private int retryTimer;

    /**
     * Excludes signing inner jars
     *
     * <p>
     * <b>Configuration via pom.xml</b>
     * </p>
     * 
     * <pre>
     * {@code
     * <configuration>
     *   <excludeInnerJars>true</excludeInnerJars>
     * </configuration>
     * }
     * </pre>
     *
     * @parameter default-value="false"
     * @since 1.0.5
     */
    private boolean excludeInnerJars;

    /**
     * Project types which this plugin supports
     *
     * <p>
     * <b>Default Types Supported</b>
     * </p>
     * 
     * <pre>
     * jar                 : standard jars
     * bundle              : felix/bnd bundles
     * maven-plugin        : maven plugins
     * eclipse-plugin      : Tycho eclipse-plugin
     * eclipse-test-plugin : Tycho eclipse-test-plugin
     * eclipse-feature     : Tycho eclipse-feature
     * </pre>
     *
     * @parameter
     */
    private List<String> supportedProjectTypes = Arrays.asList("jar", // standard
                                                                      // jars
            "war", // java war files
            "bundle", // felix/bnd bundles
            "maven-plugin", // maven plugins
            // tycho
            "eclipse-plugin", "eclipse-test-plugin", "eclipse-feature");

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping artifact signing");
            return;
        }

        final String packaging = project.getPackaging();
		if (!supportedProjectTypes.contains(packaging)) {
            getLog().debug("Packaging type '" + packaging + "' of project '" + project + "' is not supported");
            return;
        }

        final JarSigner jarSigner = createJarSigner();

        final Artifact mainArtifact = project.getArtifact();
        if (mainArtifact != null) {
        	signArtifact(jarSigner, mainArtifact);
        }

        for (Artifact artifact : project.getAttachedArtifacts()) {
        	signArtifact(jarSigner, artifact);
        }
    }

	private static void signArtifact(final JarSigner jarSigner, final Artifact artifact) throws MojoExecutionException {
		File artifactFile = artifact.getFile();
		if (artifactFile != null) {
			jarSigner.signJar(artifactFile.toPath());
		}
	}

    /**
     * Creates and returns the {@link JarSigner} according to the injected Mojo parameter.
     * @return the {@link JarSigner} according to the injected Mojo parameter.
     */
	private JarSigner createJarSigner() {
		URI signerURI = URI.create(signerUrl);
		final Signer signer = new ApacheHttpClientSigner(signerURI, getLog());
        JarSigner.Builder jarSignerBuilder = JarSigner.builder(signer);
        jarSignerBuilder.logOn(getLog()).maxRetry(retryLimit).waitBeforeRetry(retryTimer, TimeUnit.SECONDS);
        
        if (continueOnFail) {
        	jarSignerBuilder.continueOnFail();
        }
        
        if (excludeInnerJars) {
        	jarSignerBuilder.maxDepth(0);
        } else {
        	jarSignerBuilder.maxDepth(1);
        }
        
        return jarSignerBuilder.build();
	}
}
