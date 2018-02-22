/*******************************************************************************
 * Copyright (c) 2014, 2016 Eclipse Foundation and others
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
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Map;
import java.util.Objects;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.cbi.maven.Logger;
import org.eclipse.cbi.maven.http.CompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpRequest.Config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;

import tech.barbero.http.message.signing.HttpMessageSigner;

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
	public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
		return send(request, HttpRequest.Config.defaultConfig(), completionListener);
	}
	
	@Override
	public boolean send(HttpRequest request, Config config, final CompletionListener completionListener) throws IOException {
		Objects.requireNonNull(request);
		HttpUriRequest apacheRequest = toApacheRequest(request, config);
		log.debug("Will send HTTP request " + request);
		log.debug("HTTP request configuration is " + config);
		
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
			Throwables.throwIfInstanceOf(e, IOException.class);
			Throwables.throwIfUnchecked(e);
			throw new RuntimeException(e);
		}
	}
	
	private boolean doHandleResponse(final CompletionListener completionListener, HttpResponse response) throws IOException {
		final StatusLine statusLine = response.getStatusLine();
		final HttpEntity entity = response.getEntity();
		if (statusLine != null) {
			final int statusCode = statusLine.getStatusCode();
			log.debug("HTTP status code = " + statusCode);
			log.debug("HTTP reason phrase = '" + statusLine.getReasonPhrase() + "'");
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
	
	@VisibleForTesting static HttpUriRequest toApacheRequest(HttpRequest request, Config config) {
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setStrictMode();
		
		for(Map.Entry<String, String> param : request.stringParameters().entrySet()) {
			builder.addTextBody(param.getKey(), param.getValue());
		}
		
		for(Map.Entry<String, Path> param : request.pathParameters().entrySet()) {
			builder.addPart(param.getKey(), new PathBody(param.getValue()));
		}
		
		HttpPost post = new HttpPost(request.serverUri());
		
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(config.connectTimeoutMillis())
				.setSocketTimeout(config.connectTimeoutMillis())
				.setConnectTimeout(config.connectTimeoutMillis())
				.build();
		
		post.setConfig(requestConfig);
		post.setEntity(builder.build());

		if (request.privateKey() != null) {
			post = signRequest(post, request.privateKey());
		}

		return post;
	}

	private static HttpPost signRequest(HttpPost post, PrivateKey privateKey) {
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey);
			return HttpMessageSigner.builder(signature)
				.addHeaderToSign(HttpMessageSigner.REQUEST_TARGET)
				.addHeaderToSign("Host")
				.addHeaderToSign("Date")
				.addHeaderToSign("Digest")
				.addHeaderToSign("Content-Length")
				.build().sign(AHCRequest.from(post)).delegate();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}
}
