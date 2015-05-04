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
package org.eclipse.cbi.maven.plugins.dmgcreator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

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
		HttpClient client = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(dmgCreatorServiceURL);

		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

		builder.addPart("source", new FileBody(source));
		post.setEntity(builder.build());
		builder.addPart("volumeName", new StringBody(volumeName, ContentType.TEXT_PLAIN));
		builder.addPart("volumeIcon", new FileBody(volumeIcon));
		builder.addPart("icon", new StringBody(icon, ContentType.TEXT_PLAIN));
		builder.addPart("iconSize", new StringBody(iconSize, ContentType.TEXT_PLAIN));
		builder.addPart("windowPosition", new StringBody(windowPosition, ContentType.TEXT_PLAIN));
		builder.addPart("windowSize", new StringBody(windowSize, ContentType.TEXT_PLAIN));
		builder.addPart("appDropLink", new StringBody(dropLinkPosition, ContentType.TEXT_PLAIN));
		if (eulaFile != null) {
			builder.addPart("eula", new FileBody(eulaFile));
		}
		if (backgroundImage != null) {
			builder.addPart("backgroundImage", new FileBody(backgroundImage));
		}

		HttpResponse response = client.execute(post);
		int statusCode = response.getStatusLine().getStatusCode();

		HttpEntity resEntity = response.getEntity();

		if (statusCode >= 200 && statusCode <= 299 && resEntity != null) {
			try (InputStream is = resEntity.getContent()) {
				FileUtils.copyStreamToFile(new RawInputStreamFacade(is), new File(target, dmgFilename));
			}
		} else if (statusCode >= 500 && statusCode <= 599) {
			try (InputStream is = resEntity.getContent()) {
				String message = IOUtil.toString(is, "UTF-8");
				throw new NoHttpResponseException("Server failed with " + message);
			}
		} else {
			throw new MojoExecutionException("DMG creator replied " + response.getStatusLine());
		}

	}
}
