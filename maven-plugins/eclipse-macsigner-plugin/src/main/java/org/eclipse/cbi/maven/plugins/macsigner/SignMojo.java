/*******************************************************************************
 * Copyright (c) 2013, 2015 Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Caroline McQuatt, Mike Lim - initial implementation
 *   Mikael Barbero - refactoring with OSXAppSigner
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.macsigner;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.RetryHttpClient;
import org.eclipse.cbi.maven.http.apache.ApacheHttpClient;

/**
 * Signs project main and attached artifact using
 * <a href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">Eclipse macsigner webservice</a>.
 *
 * @goal sign
 * @phase package
 * @requiresProject
 * @description runs the eclipse signing process
 */
public class SignMojo extends AbstractMojo {

	private static final String DOT_APP = ".app";

	/**
     * The signing service URL for signing Mac binaries
     *
     * <p>The signing service should return a signed zip file. Containing
     * the Mac *.app directory.</p>
     *
     * <p>The Official Eclipse signer service URL as described in the
     * <a href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">
     * wiki</a>.</p>
     *
     * <p><b>Configuration via Maven commandline</b></p>
     * <pre>-Dcbi.macsigner.signerUrl=http://localhost/macsign.php</pre>
     *
     * <p><b>Configuration via pom.xml</b></p>
     * <pre>{@code
     * <configuration>
     *   <signerUrl>http://localhost/macsign</signerUrl>
     * </configuration>
     * }</pre>
     *
     * @parameter property="cbi.macsigner.signerUrl" default-value="http://build.eclipse.org:31338/macsign.php"
     * @required
     * @since 1.0.4
     */
    private String signerUrl;

    /**
     * Maven build directory
     *
     * @parameter property="project.build.directory"
     * @readonly
     * @since 1.0.4
     * @deprecated not used anymore. Use {@code java.io.tmpdir} property instead.
     */
    @SuppressWarnings("unused")
	@Deprecated
    private File workdir;

    /**
     * A list of full paths to the Mac directory *.app
     *
     * <p>If configured only these executables will be signed.</p>
     * <p><b><i>
     *    NOTE: If this is configured "baseSearchDir" and "fileNames"
     *    do NOT need to be configured.
     * </i></b></p>
     *
     * <p><b>Configuration via pom.xml</b></p>
     * <pre>{@code
     * <configuration>
     *   <signFiles>
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/macosx/cocoa/x86/eclipse/Eclipse.app{@code</signFile>
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/macosx/cocoa/x86_64/eclipse/Eclipse.app{@code</signFile>
     *   </signFiles>
     * </configuration>
     * }</pre>
     *
     * @parameter property="signFiles"
     * @since 1.0.4
     */
    private Set<String> signFiles;

    /**
     * The base directory to search for executables to sign
     *
     * <p>If NOT configured baseSearchDir is ${project.build.directory}/products/</p>
     *
     * @parameter property="baseSearchDir" default-value="${project.build.directory}/products/"
     * @since 1.0.4
     */
    private String baseSearchDir;

    /**
     * A list of *.app filenames to sign
     *
     * <p>If NOT configured {@value #ECLIPSE_APP} is signed.</p>
     *
     * @parameter property="fileNames"
     * @since 1.0.4
     */
    private Set<String> fileNames;

    /**
     * Continue the build even if signing fails
     *
     * <p><b>Configuration via Maven commandline</b></p>
     * <pre>-DcontinueOnFail=true</pre>
     *
     * <p><b>Configuration via pom.xml</b></p>
     * <pre>{@code
     * <configuration>
     *   <continueOnFail>true</continueOnFail>
     * </configuration>
     * }</pre>
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

    @Override
    public void execute() throws MojoExecutionException {
    	HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
    			.maxRetries(retryLimit)
    			.waitBeforeRetry(retryTimer, TimeUnit.SECONDS)
    			.log(new MavenLogger(getLog()))
    			.build();
    	OSXAppSigner osxAppSigner = OSXAppSigner.builder()
    		.serverUri(URI.create(signerUrl))
    		.httpClient(httpClient)
    		.exceptionHandler(new ExceptionHandler(getLog(), continueOnFail))
    		.log(getLog())
    		.build();

        if (signFiles != null && !signFiles.isEmpty()) {
        	//app paths are configured
        	Set<Path> filesToSign = new LinkedHashSet<>();
        	for (String pathString : signFiles) {
				filesToSign.add(FileSystems.getDefault().getPath(pathString));
			}
            osxAppSigner.signApplications(filesToSign);
        } else {
        	//perform search
        	osxAppSigner.signApplications(FileSystems.getDefault().getPath(baseSearchDir), getPathMatchers(FileSystems.getDefault(), fileNames, getLog()));
        }
    }

	static Set<PathMatcher> getPathMatchers(FileSystem fs, Set<String> fileNames, Log log) {
		final Set<PathMatcher> pathMatchers = new LinkedHashSet<>();

		if (fileNames == null || fileNames.isEmpty()) {
			pathMatchers.add(fs.getPathMatcher("glob:**" + fs.getSeparator() + "Eclipse.app"));
		} else {
			for (String filename : fileNames) {
				if (!filename.endsWith(DOT_APP)) {
					log.warn("The given file name '" + filename + "' does not end with '.app' extension. No corresponding application will be signed.");
				} else {
					pathMatchers.add(fs.getPathMatcher("glob:**" + filename));
				}
			}
		}
		return pathMatchers;
	}
}
