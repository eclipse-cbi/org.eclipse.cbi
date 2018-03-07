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
package org.eclipse.cbi.webservice.signing.jar;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.common.security.SignatureAlgorithm;
import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;

import com.google.auto.value.AutoValue;

/**
 * Servlet that will serve the jar signing service.
 */
@AutoValue
public abstract class SigningServlet extends HttpServlet {

	private static final long serialVersionUID = -4790172921268575018L;

	private static final String JAR_CONTENT_TYPE = "application/java-archive";
	private static final String TEMP_FILE_PREFIX = SigningServlet.class.getSimpleName() + "-";
	private static final String FILE_PART_NAME = "file";
	private static final String DIGEST_ALG_PARAMETER = "digestalg";
	private static final String SIGNATURE_ALG_PARAMETER = "sigalg";
	private static final String SIGFILE_PARAMETER = "sigfile";

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final ResponseFacade responseFacade = ResponseFacade.builder()
				.servletResponse(resp)
				.build();
		
		try(RequestFacade requestFacade = RequestFacade.builder(tempFolder()).request(req).build()) {
			doSign(requestFacade, responseFacade);
		}
	}

	private void doSign(final RequestFacade requestFacade, final ResponseFacade responseFacade) throws IOException, ServletException {
		if (requestFacade.hasPart(FILE_PART_NAME)) {
			String submittedFileName = requestFacade.getSubmittedFileName(FILE_PART_NAME).get();
			if (submittedFileName.endsWith(".jar")) {
				Path unsignedJar = requestFacade.getPartPath(FILE_PART_NAME, TEMP_FILE_PREFIX).get();
				SignatureAlgorithm signatureAlgorithm = getSignatureAlgorithm(requestFacade);
				MessageDigestAlgorithm digestAlgorithm = getDigestAlgorithm(requestFacade);
				Optional<String> sigfile = requestFacade.getParameter(SIGFILE_PARAMETER);
				Path signedJar = jarSigner().signJar(unsignedJar, signatureAlgorithm, digestAlgorithm, sigfile.orElse(null));
				responseFacade.replyWithFile(JAR_CONTENT_TYPE, submittedFileName, signedJar);
			} else {
				responseFacade.replyError(HttpServletResponse.SC_BAD_REQUEST, "Submitted '" + FILE_PART_NAME + "' '" + submittedFileName + "' must ends with '.jar' ");
			}
		} else {
			responseFacade.replyError(HttpServletResponse.SC_BAD_REQUEST, "POST request must contain a part named '" + FILE_PART_NAME + "'");
		}
	}

	private MessageDigestAlgorithm getDigestAlgorithm(final RequestFacade requestFacade) throws IOException {
		Optional<String> digestAlgorithmParameter = requestFacade.getParameter(DIGEST_ALG_PARAMETER);
		final MessageDigestAlgorithm digestAlgorihtm;
		if (digestAlgorithmParameter.isPresent()) {
			digestAlgorihtm = MessageDigestAlgorithm.fromStandardName(digestAlgorithmParameter.get());
		} else {
			digestAlgorihtm = MessageDigestAlgorithm.DEFAULT;
		}
		return digestAlgorihtm;
	}
	
	private SignatureAlgorithm getSignatureAlgorithm(final RequestFacade requestFacade) throws IOException {
		Optional<String> signatureAlgorithmParameter = requestFacade.getParameter(SIGNATURE_ALG_PARAMETER);
		final SignatureAlgorithm signatureAlgorihtm;
		if (signatureAlgorithmParameter.isPresent()) {
			signatureAlgorihtm = SignatureAlgorithm.fromStandardName(signatureAlgorithmParameter.get());
		} else {
			signatureAlgorihtm = SignatureAlgorithm.DEFAULT;
		}
		return signatureAlgorihtm;
	}
	
	abstract Path tempFolder();
	abstract JarSigner jarSigner();
	
	public static Builder builder() {
		return new AutoValue_SigningServlet.Builder();
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder tempFolder(Path tempFolder);
		public abstract Builder jarSigner(JarSigner jarSigner);
		public abstract SigningServlet build();
	}
}
