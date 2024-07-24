/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.windows;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Path;

import com.google.auto.value.AutoValue;

import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet that will serve the Windows executable signing service.
 */
@AutoValue
public abstract class SigningServlet extends HttpServlet {

	@Serial
	private static final long serialVersionUID = -7811488782781658819L;

	private static final String FILE_PART_NAME = "file";
	private static final String PORTABLE_EXECUTABLE_MEDIA_TYPE = "application/vnd.microsoft.portable-executable";
	private static final String TEMP_FILE_PREFIX = SigningServlet.class.getSimpleName() + "-";

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ResponseFacade responseFacade = ResponseFacade.builder()
				.servletResponse(resp)
				.build();
		
		try (RequestFacade requestFacade = RequestFacade.builder(tempFolder()).request(req).build()) {
			doSign(requestFacade, responseFacade);
		}
	}

	private void doSign(RequestFacade requestFacade, ResponseFacade responseFacade) throws IOException, ServletException {
		if (requestFacade.hasPart(FILE_PART_NAME)) {
			String submittedFileName = requestFacade.getSubmittedFileName(FILE_PART_NAME).get();
			if (submittedFileName.endsWith(".exe") || submittedFileName.endsWith(".dll") || submittedFileName.endsWith(".msi")) {
				Path unsignedExe = requestFacade.getPartPath(FILE_PART_NAME, TEMP_FILE_PREFIX).get();
				Path signedFile = codesigner().sign(unsignedExe);
				responseFacade.replyWithFile(PORTABLE_EXECUTABLE_MEDIA_TYPE, submittedFileName, signedFile);
			} else {
				responseFacade.replyError(HttpServletResponse.SC_BAD_REQUEST, "Submitted '" + FILE_PART_NAME + "' '" + submittedFileName + "' must ends with '.exe' ");
			}
		} else {
			responseFacade.replyError(HttpServletResponse.SC_BAD_REQUEST, "POST request must contain a part named '" + FILE_PART_NAME + "'");
		}
	}
	
	public static Builder builder() {
		return new AutoValue_SigningServlet.Builder();
	}
	
	abstract CodeSigner codesigner();
	abstract Path tempFolder();
	
	@AutoValue.Builder
	public static abstract class Builder {
		public abstract SigningServlet build();

		public abstract Builder codesigner(CodeSigner codesigner);

		public abstract Builder tempFolder(Path tempFolder);
	}
}
