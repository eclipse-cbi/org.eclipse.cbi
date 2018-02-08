/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.flatpakaging;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;

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
			Files.createDirectories(packager().repository());

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

			packager().generateFlatpakRepo(facade.getBooleanParameter("sign"), packager().work().resolve(name));

			Path tarball = tarballRepository();
			responseFacade.replyWithFile(REPLY_MEDIA_TYPE, tarball.getFileName().toString(), tarball);
		} finally {
			deleteTemporaryResource(packager().work());
		}
	}

	private Path tarballRepository() throws IOException {
		Path tarball = packager().work().resolve("repo.tar.gz");
		try (TarArchiveOutputStream out = new TarArchiveOutputStream(
				new GzipCompressorOutputStream(new FileOutputStream(tarball.toFile())))) {
			out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			int strip = packager().repository().getParent().getNameCount();
			recursiveAddTarEntry(out, packager().repository(), strip);
			out.finish();
		}
		return tarball;
	}

	private void recursiveAddTarEntry(TarArchiveOutputStream out, Path path, int strip) throws IOException {
		out.putArchiveEntry(new TarArchiveEntry(path.toFile(), path.subpath(strip, path.getNameCount()).toString()));
		if (Files.isDirectory(path)) {
			out.closeArchiveEntry();
			try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
				for (Path child : dirStream) {
					recursiveAddTarEntry(out, child, strip);
				}
			}
		} else {
			Files.copy(path, out);
			out.closeArchiveEntry();
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
