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
import javax.servlet.http.HttpSession;

import org.eclipse.cbi.webservice.servlet.AutoValue_ResponseFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.net.HttpHeaders;

/**
 * An utility class to fill {@link HttpServletResponse} fields.
 */
@AutoValue
public abstract class ResponseFacade {

	private static final String TEXT_PLAIN_CONTENT_TYPE = "text/plain";
	
	private final static Logger logger = LoggerFactory.getLogger(ResponseFacade.class);
	
	ResponseFacade() {} // prevents instantiation and subclassing outside the package
	
	/**
	 * Returns the response that will be filled.
	 * @return the response that will be filled.
	 */
	abstract HttpServletResponse servletResponse();
	
	/**
	 * Returns the current HTTP session (used for logging).
	 * @return
	 */
	abstract HttpSession session();
	
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
		 * Sets the {@link HttpSession} to be used by the to-be build
		 * {@link ResponseFacade}.
		 * 
		 * @param response
		 *            the {@link HttpSession} to be used by the to-be
		 *            build {@link ResponseFacade}. Must not be null.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder session(HttpSession session);
		
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
		
		servletResponse().addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);
		servletResponse().addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0, no-cache, no-store");
		servletResponse().addHeader(HttpHeaders.PRAGMA, "no-cache");
		
		servletResponse().setStatus(HttpServletResponse.SC_OK);
		
		Files.copy(file, servletResponse().getOutputStream());
		servletResponse().flushBuffer();
	}
	
	/**
	 * Sets the {@link HttpServletResponse} with the appropriate headers to
	 * notify an error during the processing of the request. It will dump the
	 * messages and the exception stack trace as the body of the response.
	 * 
	 * @param e
	 *            the exception that causes the error.
	 * @param messages
	 *            additional messages
	 * @throws IOException
	 *             if error occurs during the transmission of the response.
	 */
	public void internalServerError(Exception e, String... messages) throws IOException {
		internalServerError(messages);
		e.printStackTrace(servletResponse().getWriter());
		logger.error("Error Stacktrace", e);
	}
	
	/**
	 * Sets the {@link HttpServletResponse} with the appropriate headers to
	 * notify an error during the processing of the request. It will dump the
	 * messages as the body of the response.
	 * 
	 * @param messages
	 *            additional messages
	 * @throws IOException
	 *             if error occurs during the transmission of the response.
	 */
	public void internalServerError(String... messages) throws IOException {
		replyPlain(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, messages);
	}
	
	/**
	 * Sets the {@link HttpServletResponse} with the appropriate headers to
	 * reply with a plain text messages.
	 * 
	 * @param statusCode
	 *            the status of the response.
	 * @param messages
	 *            the messages to send.
	 * @throws IOException
	 *             if error occurs during the transmission of the response.
	 */
	public void replyPlain(int statusCode, String... messages) throws IOException {
		servletResponse().setStatus(statusCode);
		servletResponse().setContentType(TEXT_PLAIN_CONTENT_TYPE);
		if (messages != null && messages.length > 0) {
			final String joinedMessage = Joiner.on("\n").join(messages);
			servletResponse().getWriter().println(joinedMessage + " " + "[SESSION="+session().getId()+"]");
			
			if (statusCode < 400) {
				logger.info("Replied '" + statusCode + "': " + joinedMessage);
			} else {
				logger.error("Replied '" + statusCode + "': " + joinedMessage);
			}
		}
	}
}
