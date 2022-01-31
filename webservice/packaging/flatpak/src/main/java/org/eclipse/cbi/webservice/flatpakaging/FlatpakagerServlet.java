/*******************************************************************************
 * Copyright (c) 2018, 2022 Red Hat, Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.flatpakaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@AutoValue
public abstract class FlatpakagerServlet extends HttpServlet {

	private static final String REPLY_MEDIA_TYPE = "application/gzip";

	private final static Logger logger = LoggerFactory.getLogger(FlatpakagerServlet.class);

	abstract Path tempFolder();

	abstract Flatpakager packager();

	public static Builder builder() {
		return new AutoValue_FlatpakagerServlet.Builder();
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder packager(Flatpakager packager);

		public abstract Builder tempFolder(Path tempFolder);

		public abstract FlatpakagerServlet build();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final ResponseFacade responseFacade = ResponseFacade.builder().servletResponse(resp).build();

		try (RequestFacade facade = RequestFacade.builder(tempFolder()).request(req).build()) {
			Files.createDirectories(packager().work());

			// Create a directory with all of our source files with their original filenames
			// restored, because they'll be named that way in the Flatpak manifest file
			String name = facade.getSubmittedFileName("source").get();
			Path path = facade.getPartPath("source").get();
			Files.createSymbolicLink(packager().work().resolve(name), path);
			for (int i = 0; i < Integer.parseInt(facade.getParameter("additionalSources").get()); i++) {
				name = facade.getSubmittedFileName("additionalSource" + i).get();
				path = facade.getPartPath("additionalSource" + i).get();
				Files.createSymbolicLink(packager().work().resolve(name), path);
			}
			// And also put the manifest file in the same directory
			name = facade.getSubmittedFileName("manifest").get();
			path = facade.getPartPath("manifest").get();
			Files.createSymbolicLink(packager().work().resolve(name), path);

			String flatpakId = facade.getParameter("flatpakId").get();
			packager().generateFlatpakBundle(flatpakId, facade.getParameter("branch").get(),
					facade.getBooleanParameter("sign"), packager().work().resolve(name));

			Path bundle = packager().work().resolve(flatpakId + ".flatpak");
			long bytes = Files.size(bundle);
			logger.info("Reply size: " + bytes + " bytes");
			responseFacade.replyWithFile(REPLY_MEDIA_TYPE, bundle.getFileName().toString(), bundle);
		} finally {
			deleteTemporaryResource(packager().work());
		}
	}

	private void deleteTemporaryResource(Path tempResource) {
		if (tempResource != null && Files.exists(tempResource)) {
			try {
				Paths.delete(tempResource);
			} catch (IOException e) {
				logger.error("Error occured while deleting temporary resource '" + tempResource.toString() + "'", e);
			}
		}
	}
}
