/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.dmgpackaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.webservice.dmgpackaging.DMGPackager.Options;
import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DMGPackagerServlet extends HttpServlet {

	private static final String APPLE_DISKIMAGE_MEDIA_TYPE = "application/x-apple-diskimage";

	private static final String DOT_APP = ".app";
	private static final String DOT_DMG = ".dmg";

	private final static Logger logger = LoggerFactory.getLogger(DMGPackagerServlet.class);
	
	private static final long serialVersionUID = 7717817265007907435L;
	
	DMGPackagerServlet() {}
	
	abstract Path tempFolder();
	abstract DMGPackager dmgPackager();
	abstract DMGSigner dmgSigner();
	
	public static Builder builder() {
		return new AutoValue_DMGPackagerServlet.Builder();
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}
		public abstract Builder dmgPackager(DMGPackager dmgPackager);
		public abstract Builder dmgSigner(DMGSigner dmgSigner);
		public abstract Builder tempFolder(Path tempFolder);
		public abstract DMGPackagerServlet build();
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final ResponseFacade responseFacade = ResponseFacade.builder()
				.servletResponse(resp)
				.build();
		
		Path targetImageFile = null;
		try(RequestFacade requestFacade = RequestFacade.builder(tempFolder()).request(req).build();
				DMGPackagerServletRequestParser parser = DMGPackagerServletRequestParser.builder(tempFolder()).requestFacade(requestFacade).build()) {
			
			final Options options = Options.builder()
				.appDropLink(parser.getAppDropLink())
				.backgroundImage(parser.getBackgroundImage())
				.eula(parser.getEula())
				.icon(parser.getIcon())
				.iconSize(parser.getIconSize())
				.volumeIcon(parser.getVolumeIcon())
				.volumeName(parser.getVolumeName())
				.windowPosition(parser.getWindowPosition())
				.windowSize(parser.getWindowSize())
				.build();
			
			Path source = parser.getSource();
			targetImageFile = dmgPackager().packageImageFile(source, source.normalize().getParent().resolve(source.getFileName().toString().replace(DOT_APP, DOT_DMG)), options);
			
			if (parser.getSign().isPresent() && parser.getSign().get().booleanValue()) { 
				try {
					dmgSigner().sign(targetImageFile);
				} catch (IOException e) {
					logger.error("Error occured while signing '"+targetImageFile.toString()+"'", e);
				}
			}
			
			responseFacade.replyWithFile(APPLE_DISKIMAGE_MEDIA_TYPE, targetImageFile.getFileName().toString(), targetImageFile);
		} finally {
			deleteTemporaryResource(targetImageFile);
		}
	}

	private void deleteTemporaryResource(Path tempResource) {
		if (tempResource != null && Files.exists(tempResource)) {
			try {
				Paths.delete(tempResource);
			} catch (IOException e) {
				logger.error("Error occured while deleting temporary resource '"+tempResource.toString()+"'", e);
			}
		}
	}
}
