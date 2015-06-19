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
import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DMGPackagerServlet extends HttpServlet {

	private static final String DOT_TAR_GZ = ".tar.gz";

	private static final String APPLE_DISKIMAGE_MEDIA_TYPE = "application/x-apple-diskimage";

	private static final String DOT_DMG = ".dmg";

	private final static Logger logger = LoggerFactory.getLogger(DMGPackagerServlet.class);
	
	private static final long serialVersionUID = 7717817265007907435L;
	
	DMGPackagerServlet() {}
	
	abstract DMGPackagerServletRequestParser.Builder requestParserBuilder();
	abstract RequestFacade.Builder requestFacadeBuilder();
	abstract DMGPackager.Builder dmgPackagerBuilder();
	
	public static Builder builder() {
		return new AutoValue_DMGPackagerServlet.Builder();
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}
		public abstract Builder requestParserBuilder(DMGPackagerServletRequestParser.Builder parserBuilder);
		public abstract Builder requestFacadeBuilder(RequestFacade.Builder requestFacadeBuilder);
		public abstract Builder dmgPackagerBuilder(DMGPackager.Builder dmgPackageBuilder);
		public abstract DMGPackagerServlet build();
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final ResponseFacade responseFacade = ResponseFacade.builder()
				.servletResponse(resp)
				.session(req.getSession())
				.build();
		
		Path targetImageFile = null;
		try(RequestFacade requestFacade = requestFacadeBuilder().request(req).build();
				DMGPackagerServletRequestParser parser = requestParserBuilder().requestFacade(requestFacade).build()) {
			final DMGPackager packager = dmgPackagerBuilder()
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
			targetImageFile = packager.packageImageFile(source, source.normalize().getParent().resolve(source.getFileName().toString() + DOT_DMG));
			String filename = requestFacade.getSubmittedFileName(DMGPackagerServletRequestParser.SOURCE_PART_NAME).toString().replace(DOT_TAR_GZ, DOT_DMG);
			responseFacade.replyWithFile(APPLE_DISKIMAGE_MEDIA_TYPE, filename, targetImageFile);
		} catch (Exception e) {
			responseFacade.internalServerError(e);
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
