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
package org.eclipse.cbi.webservice.signing.macosx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;

/**
 * Serves OS X code signing service through POST request. It requires a "file"
 * part which must be a ZIP file with one or more .app It will reply a new ZIP
 * file with all the .app in the original ZIP file signed accordingly.
 */
@AutoValue
public abstract class SigningServlet extends HttpServlet {

	private static final String TEMP_FILE_PREFIX = SigningServlet.class.getSimpleName() + "-";
	
	private static final String ZIP_CONTENT_TYPE = "application/zip";
	private static final String OCTET_STREAM__CONTENT_TYPE = "application/octet-stream";

	private static final String FILE_PART_NAME = "file";

	private static final String ENTITLEMENTS_PART_NAME = "entitlements";

	private static final long serialVersionUID = 523028904959736808L;

	SigningServlet() {}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		final ResponseFacade responseFacade = ResponseFacade.builder()
			.servletResponse(response)
			.build();

		try(RequestFacade requestFacade = RequestFacade.builder(tempFolder()).request(req).build()) {
			if (requestFacade.hasPart(FILE_PART_NAME)) {
				doSign(requestFacade, responseFacade);
			} else {
				responseFacade.replyError(HttpServletResponse.SC_BAD_REQUEST, "POST request must contain a part named '" + FILE_PART_NAME + "'");
			}
		}
	}

	private void doSign(RequestFacade requestFacade, final ResponseFacade answeringMachine) throws IOException, ServletException {
		Path fileToBeSigned = requestFacade.getPartPath(FILE_PART_NAME, TEMP_FILE_PREFIX).get();
		Optional<Path> entitlements = requestFacade.getPartPath(ENTITLEMENTS_PART_NAME, TEMP_FILE_PREFIX);
		if ("zip".equals(com.google.common.io.Files.getFileExtension(requestFacade.getSubmittedFileName(FILE_PART_NAME).get()))) {
			signFilesInZip(requestFacade, answeringMachine, fileToBeSigned, entitlements);
		} else {
			if (codesigner().signFile(fileToBeSigned, entitlements) > 0) {
				answeringMachine.replyWithFile(OCTET_STREAM__CONTENT_TYPE, requestFacade.getSubmittedFileName(FILE_PART_NAME).get(), fileToBeSigned);
			} else {
				answeringMachine.replyError(HttpServletResponse.SC_BAD_REQUEST, "Unable to sign provided file");
			}
		}
	}

	private void signFilesInZip(RequestFacade requestFacade, final ResponseFacade answeringMachine, Path zipFileWithFilesToBeSigned, Optional<Path> entitlements) throws IOException, ServletException {
		final String submittedFilename = requestFacade.getSubmittedFileName(FILE_PART_NAME).get();
		final Path signedFile = Files.createTempFile(tempFolder(), TEMP_FILE_PREFIX, "signed." + com.google.common.io.Files.getFileExtension(submittedFilename));
		try {
			if (codesigner().signZippedApplications(zipFileWithFilesToBeSigned, signedFile, entitlements) > 0) {
				answeringMachine.replyWithFile(ZIP_CONTENT_TYPE, requestFacade.getSubmittedFileName(FILE_PART_NAME).get(), signedFile);
			} else {
				answeringMachine.replyError(HttpServletResponse.SC_BAD_REQUEST, "No '.app' folder can be found in the provided zip file");
			}
		} finally {
			Codesigner.cleanTemporaryResource(signedFile);
		}
	}

	abstract Codesigner codesigner();

	/**
	 * Returns the temporary folder to use during intermediate step of
	 * application signing.
	 *
	 * @return the temporary folder to use during intermediate step of
	 *         application signing.
	 */
	abstract Path tempFolder();

	public static Builder builder() {
		return new AutoValue_SigningServlet.Builder();
	}

	/**
	 * Builder class for {@link SigningServlet} objects.
	 */
	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}

		public abstract Builder codesigner(Codesigner codesigner);

		/**
		 * Sets the temporary folder for intermediate step during signing.
		 *
		 * @param tempFolder
		 *            the temporary folder for intermediate step during signing.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder tempFolder(Path tempFolder);

		abstract SigningServlet autoBuild();

		/**
		 * Creates and returns a new instance of {@link SigningServlet} as
		 * configured by this builder. The following checks are made:
		 * <ul>
		 * <li>The temporary folder must exists.</li>
		 * </ul>
		 *
		 * @return a new instance of {@link Codesigner} as configured by this
		 *         builder.
		 */
		public SigningServlet build() {
			SigningServlet codesignerServlet = autoBuild();
			Preconditions.checkState(Files.exists(codesignerServlet.tempFolder()), "Temporary folder must exists");
			Preconditions.checkState(Files.isDirectory(codesignerServlet.tempFolder()), "Temporary folder must be a directory");
			return codesignerServlet;
		}
	}
}