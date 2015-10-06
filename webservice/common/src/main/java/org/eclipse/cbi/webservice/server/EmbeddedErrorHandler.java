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
package org.eclipse.cbi.webservice.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class EmbeddedErrorHandler extends ErrorHandler {

	private static final Logger LOG = Log.getLogger(EmbeddedErrorHandler.class);
	
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
		String method = request.getMethod();
		if (!HttpMethod.GET.is(method) && !HttpMethod.POST.is(method) && !HttpMethod.HEAD.is(method)) {
			baseRequest.setHandled(true);
			return;
		}

		if (this instanceof ErrorPageMapper) {
			String error_page = ((ErrorPageMapper) this).getErrorPage(request);
			if (error_page != null && request.getServletContext() != null) {
				String old_error_page = (String) request.getAttribute(ERROR_PAGE);
				if (old_error_page == null || !old_error_page.equals(error_page)) {
					request.setAttribute(ERROR_PAGE, error_page);

					Dispatcher dispatcher = (Dispatcher) request.getServletContext().getRequestDispatcher(error_page);
					try {
						if (dispatcher != null) {
							dispatcher.error(request, response);
							return;
						}
						LOG.warn("No error page " + error_page);
					} catch (ServletException e) {
						LOG.warn(Log.EXCEPTION, e);
						return;
					}
				}
			}
		}

		baseRequest.setHandled(true);
		response.setContentType(MimeTypes.Type.TEXT_PLAIN_UTF_8.asString());
		if (getCacheControl() != null)
			response.setHeader(HttpHeader.CACHE_CONTROL.asString(), getCacheControl());
		StringWriter writer = new StringWriter();
		String reason = (response instanceof Response) ? ((Response) response).getReason() : null;
		handleErrorPage(request, writer, response.getStatus(), reason);
		byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
		response.setContentLength(bytes.length);
		response.getOutputStream().write(bytes);
	}

	@Override
	protected void writeErrorPage(HttpServletRequest request, Writer writer, int code, String message,
			boolean showStacks) throws IOException {
		if (message == null) {
			message = HttpStatus.getMessage(code);
		}

		writeErrorPageBody(request, writer, code, message, showStacks);
	}

	@Override
	protected void writeErrorPageBody(HttpServletRequest request, Writer writer, int code, String message,
			boolean showStacks) throws IOException {
		String uri = request.getRequestURI();
		writeErrorPageMessage(request, writer, code, message, uri);
		if (showStacks) {
			writeErrorPageStacks(request, writer);
		}
		writer.write("\n");
	}

	@Override
	protected void writeErrorPageMessage(HttpServletRequest request, Writer writer, int code, String message,
			String uri) throws IOException {
		writer.write("HTTP Error ");
		writer.write(Integer.toString(code));
		writer.write("\nProblem accessing '");
		writer.write(uri);
		writer.write("'\nReason: ");
		writer.write(message);
	}

	@Override
	protected void writeErrorPageStacks(HttpServletRequest request, Writer writer) throws IOException {
		Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception");
		while (th != null) {
			writer.write("\n\nCaused by: ");
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			th.printStackTrace(pw);
			pw.flush();
			writer.write(sw.getBuffer().toString());

			th = th.getCause();
		}
	}
}
