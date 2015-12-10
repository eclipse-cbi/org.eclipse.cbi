/*******************************************************************************
 * Copyright (c) 2014, 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.http.apache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.cbi.maven.Logger;
import org.eclipse.cbi.maven.http.CompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;

public class ApacheHttpClient implements HttpClient {

	/**
	 * The log for providing {@code DEBUG} feedback about the process.
	 */
	private final Logger log;
	
	ApacheHttpClient(Logger log) {
		this.log = Objects.requireNonNull(log);
	}
	
	public static HttpClient create(Logger log) {
		return new ApacheHttpClient(log);
	}
	
	@Override
	public boolean send(HttpRequest request, final CompletionListener completionListener) throws IOException {
		Objects.requireNonNull(request);
		HttpUriRequest apacheRequest = toApacheRequest(request);
		log.debug("Will send request to '" + apacheRequest.getURI() + "'");
		
		Stopwatch stopwatch = Stopwatch.createStarted();
		try (CloseableHttpClient apacheHttpClient = HttpClientBuilder.create().build()) {
			return apacheHttpClient.execute(apacheRequest, new ResponseHandler<Boolean>() {
				@Override
				public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
					return doHandleResponse(completionListener, response);
				}
			});
		} catch (Exception e) {
			log.debug("HTTP request and response handled in " + stopwatch);
			Throwables.propagateIfInstanceOf(e, IOException.class);
			throw Throwables.propagate(e);
		}
	}
	
	private boolean doHandleResponse(final CompletionListener completionListener, HttpResponse response) throws IOException {
		final StatusLine statusLine = response.getStatusLine();
		final HttpEntity entity = response.getEntity();
		if (statusLine != null) {
			final int statusCode = statusLine.getStatusCode();
			if (statusCode >= HttpStatus.SC_OK && statusCode < HttpStatus.SC_MULTIPLE_CHOICES && entity != null) {
				completionListener.onSuccess(new BasicHttpResult(statusCode, Strings.nullToEmpty(statusLine.getReasonPhrase()), entity));
				return true;
			} else {
				completionListener.onError(new BasicHttpResult(statusCode, Strings.nullToEmpty(statusLine.getReasonPhrase()), entity));
				return false;
			}
		} else {
			throw new IllegalStateException("Can't retrieve status line of the HttpResponse");
		}
	}
	
	@VisibleForTesting static HttpUriRequest toApacheRequest(HttpRequest request) {
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setStrictMode();
		
		for(Map.Entry<String, String> param : request.stringParameters().entrySet()) {
			builder.addTextBody(param.getKey(), param.getValue());
		}
		
		for(Map.Entry<String, Path> param : request.pathParameters().entrySet()) {
			builder.addPart(param.getKey(), new PathBody(param.getValue()));
		}
		
		HttpPost post = new HttpPost(request.serverUri());
		post.setEntity(builder.build());
		return post;
	}
}
