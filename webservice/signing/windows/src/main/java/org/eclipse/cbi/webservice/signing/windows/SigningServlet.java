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
package org.eclipse.cbi.webservice.signing.windows;

import java.io.IOException;
import java.nio.file.Path;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;

import com.google.auto.value.AutoValue;

/**
 * Servlet that will serve the Windows executable signing service.
 */
@AutoValue
public abstract class SigningServlet extends HttpServlet {

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
				.session(req.getSession()).build();
		
		try (RequestFacade requestFacade = requestFacadeBuilder().request(req).build()) {
			doSign(requestFacade, responseFacade);
		} catch (Exception e) {
			responseFacade.internalServerError(e);
		}
	}

	private void doSign(RequestFacade requestFacade, ResponseFacade responseFacade) throws IOException, ServletException {
		if (requestFacade.hasPart(FILE_PART_NAME)) {
			String submittedFileName = requestFacade.getSubmittedFileName(FILE_PART_NAME).get();
			if (submittedFileName.endsWith(".exe")) {
				Path unsignedExe = requestFacade.getPartPath(FILE_PART_NAME, TEMP_FILE_PREFIX).get();
				Path signedFile = osslCodesigner().sign(unsignedExe);
				responseFacade.replyWithFile(PORTABLE_EXECUTABLE_MEDIA_TYPE, submittedFileName, signedFile);
			} else {
				responseFacade.replyPlain(HttpServletResponse.SC_BAD_REQUEST, "Submitted '" + FILE_PART_NAME + "' '" + submittedFileName + "' must ends with '.exe' ");
			}
		} else {
			responseFacade.replyPlain(HttpServletResponse.SC_BAD_REQUEST, "POST request must contain a part named '" + FILE_PART_NAME + "'");
		}
	}
	
	public static Builder builder() {
		return new AutoValue_SigningServlet.Builder();
	}
	
	abstract OSSLCodesigner osslCodesigner();
	abstract RequestFacade.Builder requestFacadeBuilder();
	
	@AutoValue.Builder
	public static abstract class Builder {
		public abstract SigningServlet build();

		public abstract Builder osslCodesigner(OSSLCodesigner codesigner);

		public abstract Builder requestFacadeBuilder(RequestFacade.Builder builder);
	}
}
