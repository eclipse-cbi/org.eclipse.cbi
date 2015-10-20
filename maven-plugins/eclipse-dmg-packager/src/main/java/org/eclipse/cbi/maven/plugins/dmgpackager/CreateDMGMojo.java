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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.AbstractCompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpRequest.Builder;
import org.eclipse.cbi.maven.http.HttpResult;
import org.eclipse.cbi.maven.http.RetryHttpClient;
import org.eclipse.cbi.maven.http.apache.ApacheHttpClient;

/**
 * Create a DMG file from the file specified as argument. This plug-in requires
 * access to the Eclipse DMG packager web service.
 */
@Mojo(name = "package-dmg", defaultPhase = LifecyclePhase.PACKAGE)
public class CreateDMGMojo extends AbstractMojo {

	/**
	 * The user readable name of the DMG created. It is displayed in the Finder
	 * sidebar and window title.
	 * 
	 * @since 1.1.3
	 */
	@Parameter
	private String volumeName; // --volname

	/**
	 * The icns file used for the DMG.
	 * 
	 * @since 1.1.3
	 */
	@Parameter
	private File volumeIcon; // --volicon

	/**
	 * A png file for background image for the installer.
	 * 
	 * @since 1.1.3
	 */
	@Parameter
	private File backgroundImage; // --background

	/**
	 * The size of the installer window. Coordinates are expressed as X Y (e.g.
	 * 480 300)
	 * 
	 * @since 1.1.3
	 */
	@Parameter(defaultValue = "480 300")
	private String windowSize; // --window-size

	/**
	 * The position of the installer window when it opens. Coordinates are
	 * expressed as X Y (e.g. 50 50).
	 * 
	 * @since 1.1.3
	 */
	@Parameter(defaultValue = "50 50")
	private String windowPosition; // --window-pos

	/**
	 * The size of the icon to display in the installer.
	 * 
	 * @since 1.1.3
	 */
	@Parameter(defaultValue = "125")
	private String iconSize; // --icon-size

	/**
	 * Name of the icon to display and position, e.g., {@code "Eclipse.app x y"}. Recommended
	 * size 100 125
	 * 
	 * @since 1.1.3
	 */
	@Parameter
	private String icon; // --icon

	/**
	 * Location of the drop link to Applications folder.
	 * 
	 * @since 1.1.3
	 */
	@Parameter(defaultValue = "375 125")
	private String dropLinkPosition; // --app-drop-link

	/**
	 * File to attach as a license.
	 * 
	 * @since 1.1.3
	 */
	@Parameter
	private File eulaFile;

	/**
	 * An {@code .tar.gz} or {@code .zip} file containing a single OS X application to create the DMG for.
	 * 
	 * @since 1.1.3
	 */
	@Parameter(required = true)
	private File source;

	/**
	 * The URL for creating DMG files
	 *
	 * <p>
	 * The signing service return a dmg file of the application passed as a
	 * source.
	 * </p>
	 *
	 * @since 1.1.3
	 */
	@Parameter(required = true, property="cbi.dmgpackager.serviceUrl", defaultValue = "http://build.eclipse.org:31338/dmg-packager")
	private String serviceUrl;

	/**
	 * Where the new DMG file should be saved. If it is not specified, the file is placed beside {@link #source}.
	 * 
	 * @since 1.1.3
	 */
	@Parameter
	private File target;

	/**
	 * Skips the execution of this plugin
	 * 
	 * @since 1.1.3
	 */
	@Parameter(property = "cbi.dmgpackager.skip", defaultValue = "false")
	private boolean skip;
	
	/**
	 * Whether the build should be stopped if the packaging process fails.
	 *
	 * @since 1.1.3
	 */
	@Parameter(property = "cbi.dmgpackager.continueOnFail", defaultValue = "false")
	private boolean continueOnFail;

	@Override
	public void execute() throws MojoExecutionException {
		if (skip) {
			getLog().info("Skipping packaging DMG file");
			return;
		}

		final ExceptionHandler exceptionHandler = new ExceptionHandler(getLog(), continueOnFail);
		try {
			callDMGService(exceptionHandler);
		} catch (IOException e) {
			exceptionHandler.handleError("Packaging of DMG file failed", e);
		}
	}

	private void callDMGService(ExceptionHandler exceptionHandler) throws IOException, MojoExecutionException {
		HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
				.maxRetries(3)
				.waitBeforeRetry(10, TimeUnit.SECONDS)
				.build();
		
		if (!source.exists()) {
			exceptionHandler.handleError("'source' file must exist");
			return;
		}
		if (!source.toPath().getFileName().toString().endsWith(".tar.gz") && !source.toPath().getFileName().toString().endsWith(".zip")) {
			exceptionHandler.handleError("'source' file name must ends with '.tar.gz' or '.zip'");
			return;
		}
		if (!volumeIcon.exists()) {
			exceptionHandler.handleError("'volumeIcon' file must exist");
			return;
		}
		
		Builder requestBuilder = HttpRequest.on(URI.create(serviceUrl))
			.withParam("source", source.toPath())
			.withParam("volumeName", volumeName)
			.withParam("volumeIcon", volumeIcon.toPath())
			.withParam("icon", icon)
			.withParam("iconSize", iconSize)
			.withParam("windowPosition", windowPosition)
			.withParam("windowSize", windowSize)
			.withParam("appDropLink", dropLinkPosition);
			
		if (eulaFile != null) {
			if (!eulaFile.exists()) {
				exceptionHandler.handleError("'eulaFile' file must exist");
				return;
			}
			requestBuilder.withParam("eula", eulaFile.toPath());
		}
		
		if (backgroundImage != null) {
			if (!backgroundImage.exists()) {
				exceptionHandler.handleError("'backgroundImage' file must exist");
				return;
			}
			requestBuilder.withParam("backgroundImage", backgroundImage.toPath());
		}
		
		processOnRemoteServer(httpClient, requestBuilder.build());
	}

	private void processOnRemoteServer(HttpClient httpClient, HttpRequest request) throws IOException {
		httpClient.send(request, new AbstractCompletionListener(source.toPath().getParent(), source.toPath().getFileName().toString(), CreateDMGMojo.class.getSimpleName(), new MavenLogger(getLog())) {
			@Override
			public void onSuccess(HttpResult result) throws IOException {
				if (target != null) {
					Path targetPath = target.toPath();
					Path parent = targetPath.getParent();
					if (parent != null) {
						Files.createDirectories(parent);
					}
					result.copyContent(targetPath, StandardCopyOption.REPLACE_EXISTING);
				} else {
					Path sourcePath = source.toPath();
					String filename = sourcePath.getFileName().toString();
					final String dmgFilename;
					if (filename.endsWith(".tar.gz")) {
						dmgFilename = filename.replace(".tar.gz", ".dmg");
					} else {
						dmgFilename = filename.replace(".zip", ".dmg");
					}
					Path parent = sourcePath.getParent();
					if (parent != null) {
						result.copyContent(parent.resolve(dmgFilename), StandardCopyOption.REPLACE_EXISTING);
					} else {
						result.copyContent(Paths.get(dmgFilename), StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		});
	}
}
