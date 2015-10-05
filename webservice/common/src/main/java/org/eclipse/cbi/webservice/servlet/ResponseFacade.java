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
package org.eclipse.cbi.webservice.servlet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.http.HttpServletResponse;

import com.google.auto.value.AutoValue;
import com.google.common.net.HttpHeaders;

/**
 * An utility class to fill {@link HttpServletResponse} fields.
 */
@AutoValue
public abstract class ResponseFacade {

	ResponseFacade() {} // prevents instantiation and subclassing outside the package
	
	/**
	 * Returns the response that will be filled.
	 * @return the response that will be filled.
	 */
	abstract HttpServletResponse servletResponse();
	
	/**
	 * Creates and returns a new builder for this class.
	 * @return a new builder for this class.
	 */
	public static Builder builder() {
		return new AutoValue_ResponseFacade.Builder();
	}
	
	/**
	 * A builder for {@link ResponseFacade}.
	 */
	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}
		
		/**
		 * Sets the {@link HttpServletResponse} to be used by the to-be build
		 * {@link ResponseFacade}. Must not be null.
		 * 
		 * @param response
		 *            the {@link HttpServletResponse} to be used by the to-be
		 *            build {@link ResponseFacade}.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder servletResponse(HttpServletResponse response);
		
		/**
		 * Creates and returns a new {@link ResponseFacade}.
		 */
		public abstract ResponseFacade build();
	}
	
	/**
	 * Sets the {@link HttpServletResponse} with the appropriate headers to send
	 * back the given file to the client.
	 * 
	 * @param contentType
	 *            the content type of the file to be send
	 * @param fileName
	 *            the filename of the file to be send
	 * @param file
	 *            the path of the file to be send
	 * @throws IOException
	 *             if the file can't be read or sent.
	 */
	public void replyWithFile(String contentType, String fileName, Path file) throws IOException {
		servletResponse().setContentType(contentType);
		servletResponse().setContentLengthLong(Files.size(file));
		
		servletResponse().addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName+ "\"");
		servletResponse().addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0,must-revalidate,no-cache,no-store");
		servletResponse().addHeader(HttpHeaders.PRAGMA, "no-cache");
		
		servletResponse().setStatus(HttpServletResponse.SC_OK);
		
		Files.copy(file, servletResponse().getOutputStream());
		servletResponse().flushBuffer();
	}
	
	/**
	 * Sets the {@link HttpServletResponse} with the appropriate headers to
	 * notify an error during the processing of the request. It will dump the
	 * reason as the body of the response.
	 * 
	 * @param reason
	 *            additional messages
	 * @throws IOException
	 *             if error occurs during the transmission of the response.
	 */
	public void replyError(int statusCode, String reason) throws IOException {
		servletResponse().sendError(statusCode, reason);
	}
}
