/*******************************************************************************
 * Copyright (c) 2015 Rapicorp, inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Rapicorp, inc. - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.dmgpackager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.AbstractCompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpResult;
import org.eclipse.cbi.maven.http.RetryHttpClient;
import org.eclipse.cbi.maven.http.HttpRequest.Builder;
import org.eclipse.cbi.maven.http.apache.ApacheHttpClient;

/**
 * Create a DMG file from the file specified as argument. This plug-in requires
 * access to the Eclipse Foundation DMG service
 *
 * @goal createDMG
 * @description Create an eclipse DMG
 */
public class CreateDMGMojo extends AbstractMojo {
	/**
	 * The user readable name of the DMG created.
	 * 
	 * @parameter
	 * @required
	 */
	private String volumeName; // --volname

	/**
	 * The icns file used for the DMG.
	 * 
	 * @parameter
	 * @required
	 */
	private File volumeIcon; // --volicon

	/**
	 * A png file for background image for the installer.
	 * 
	 * @parameter
	 */
	private File backgroundImage; // --background

	/**
	 * The size of the installer window. Coordinates are expressed as X Y (e.g.
	 * 480 300)
	 * 
	 * @parameter default-value="480 300"
	 * @required 
	 */
	private String windowSize; // --window-size

	/**
	 * The position of the installer window when it opens. Coordinates are
	 * expressed as X Y (e.g. 50 50).
	 * 
	 * @parameter default-value="50 50"
	 * @required 
	 */
	private String windowPosition; // --window-pos

	/**
	 * The size of the icon to display in the installer.
	 * 
	 * @parameter default-value="125"
	 * @required 
	 */
	private String iconSize; // --icon-size

	/**
	 * Name of the icon to display, and position. "<folderName> x y" Recommended
	 * size 100 125
	 * 
	 * @parameter
	 * @required
	 */
	private String icon; // --icon

	/**
	 * Location of the drop link.
	 * 
	 * @parameter default-value="375 125"
	 * @required 
	 */
	private String dropLinkPosition; // --app-drop-link

	/**
	 * File to attach as a license.
	 * 
	 * @parameter
	 */
	private File eulaFile;

	/**
	 * An tar.gz file of the content to create the DMG for.
	 * 
	 * @parameter
	 * @required
	 */
	private File source;

	/**
	 * The URL for creating DMG files
	 *
	 * <p>
	 * The signing service return a dmg file of the application passed as a
	 * source.
	 * </p>
	 *
	 * @parameter
	 * @default-value="http://build.eclipse.org:31338/createDMG.php"
	 * 
	 * @required
	 * @since 1.0.0
	 */
	private String dmgCreatorServiceURL;

	/**
	 * Maven build directory
	 *
	 * @parameter property="project.build.directory/dmg"
	 */
	private File target;

	/**
	 * The name of the dmg file to be created.
	 * 
	 * @parameter
	 * @required
	 */
	private String dmgFilename;

	/**
	 * Skips the execution of this plugin
	 *
	 * @parameter property="cbi.dmgcreation.skip" default-value="false"
	 */
	private boolean skip;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			getLog().info("Skipping dmg creation");
			return;
		}
		try {
			callDMGService();
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to create DMG", e);
		}
	}

	private void callDMGService() throws IOException, MojoExecutionException {
		HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
				.maxRetries(3)
				.waitBeforeRetry(30, TimeUnit.SECONDS)
				.build();
		Builder requestBuilder = HttpRequest.on(URI.create(dmgCreatorServiceURL))
			.withParam("source", source.toPath())
			.withParam("volumeName", volumeName)
			.withParam("volumeIcon", volumeIcon.toPath())
			.withParam("icon", icon)
			.withParam("iconSize", iconSize)
			.withParam("windowPosition", windowPosition)
			.withParam("windowSize", windowSize)
			.withParam("appDropLink", dropLinkPosition);
			
		if (eulaFile != null) {
			requestBuilder.withParam("eula", eulaFile.toPath());
		}
		if (backgroundImage != null) {
			requestBuilder.withParam("backgroundImage", backgroundImage.toPath());
		}
		httpClient.send(requestBuilder.build(), new AbstractCompletionListener(source.toPath().getParent(), source.toPath().getFileName().toString(), CreateDMGMojo.class.getSimpleName(), new MavenLogger(getLog())) {
			@Override
			public void onSuccess(HttpResult result) throws IOException {
				result.copyContent(target.toPath().resolve(dmgFilename), StandardCopyOption.REPLACE_EXISTING);
			}
		});
	}
}
