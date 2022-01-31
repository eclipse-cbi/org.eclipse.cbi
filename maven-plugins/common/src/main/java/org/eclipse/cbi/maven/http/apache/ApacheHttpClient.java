/*******************************************************************************
 * Copyright (c) 2014, 2016 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.http.apache;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
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

/**
 * An HttpClient implementation based on Apache HttpClient.
 */
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
		this.log.debug("Will send HTTP request " + request);
		this.log.debug("HTTP request configuration is " + config);
		
		Stopwatch stopwatch = Stopwatch.createStarted();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (CloseableHttpClient apacheHttpClient = HttpClientBuilder.create().build()) {
			ResponseHandler<? extends Boolean> responseHandler = response -> doHandleResponse(completionListener, response);
			Future<Boolean> requestExec = executor.submit(() -> apacheHttpClient.execute(apacheRequest, responseHandler));
			final boolean ret;
			if (Duration.ZERO.equals(config.timeout())) {
				ret = requestExec.get().booleanValue();
			} else {
				ret = requestExec.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS).booleanValue();
			}
			return ret;
		} catch (@SuppressWarnings("unused") InterruptedException e) {
			apacheRequest.abort();
			executor.shutdownNow();
			// restore interrupted status
			Thread.currentThread().interrupt();
			return false;
		} catch (TimeoutException e) {
			apacheRequest.abort();
			executor.shutdownNow();
			this.log.debug("HTTP request and response handled in " + stopwatch);
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			apacheRequest.abort();
			executor.shutdownNow();
			this.log.debug("HTTP request and response handled in " + stopwatch);
			Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
			Throwables.throwIfUnchecked(e.getCause());
			throw new RuntimeException(e.getCause());
		}
	}
	
	private Boolean doHandleResponse(final CompletionListener completionListener, HttpResponse response) throws IOException {
		final StatusLine statusLine = Objects.requireNonNull(response.getStatusLine(), "Can't retrieve status line of the HttpResponse");
		final int statusCode = statusLine.getStatusCode();
		
		this.log.debug("HTTP status code = " + statusCode);
		this.log.debug("HTTP reason phrase = '" + statusLine.getReasonPhrase() + "'");
		
		final HttpEntity entity = response.getEntity();
		boolean success = statusCode >= HttpStatus.SC_OK && statusCode < HttpStatus.SC_MULTIPLE_CHOICES && entity != null;
		BasicHttpResult httpResult = new BasicHttpResult(statusCode, Strings.nullToEmpty(statusLine.getReasonPhrase()), entity);
		if (success) {
			completionListener.onSuccess(httpResult);
		} else {
			completionListener.onError(httpResult);
		}
		
		return Boolean.valueOf(success);
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
				// use same timeout for connection request as for connect
				.setConnectionRequestTimeout((int) config.readTimeout().toMillis())
				.setSocketTimeout((int) config.readTimeout().toMillis())
				.setConnectTimeout((int) config.connectTimeout().toMillis())
				// TODO: try expect continue after 1.1.5 release
				//.setExpectContinueEnabled(true)
				.build();
		
		post.setConfig(requestConfig);
		post.setEntity(builder.build());
		return post;
	}
}
