/*******************************************************************************
 * Copyright (c) 2014, 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Thanh Ha - initial implementation
 *   Mikael Barbero - code splitting
 *******************************************************************************/
package org.eclipse.cbi.common.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codehaus.plexus.util.IOUtil;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * A class that send a file to as a post request to an HTTP server adn replace 
 * the send file with the reply.
 */
public class ApacheHttpClientPostFileSender implements HttpPostFileSender {

	/**
	 * The URI of the server where the file will be send.
	 */
	private final URI serverURI;

	/**
	 * The log for providing {@code DEBUG} feedback about the signing process.
	 */
	private final Logger log;

	/**
	 * Default constructor.
	 * 
	 * @param serverURI
	 *            the URI of the server where the file will be send.
	 * @param log
	 *            the log for providing {@code DEBUG} feedback about the signing process
	 */
	public ApacheHttpClientPostFileSender(URI serverURI, Logger log) {
		this.serverURI = Objects.requireNonNull(serverURI);
		this.log = Objects.requireNonNull(log);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean post(Path path, String partName) throws IOException {
		return post(path, partName, 0, 0, TimeUnit.SECONDS); 
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean post(Path path, String partName, int maxRetries, int retryInterval, TimeUnit unit) throws IOException {
		if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
			throw new IllegalArgumentException("'source' must be an existing regular file.");
		}
		Preconditions.checkArgument(!Strings.isNullOrEmpty(partName), "'partName' must not be empty or null");
		checkPositive(maxRetries, "'maxRetries' must be positive");
		checkPositive(retryInterval, "'retryInterval' must be positive");
		Objects.requireNonNull(unit, "'unit' must not be null");
		
		try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
			boolean sucessfullySigned = false;
			
			Exception lastThrownException = null;
			for (int retryCount = 0; !sucessfullySigned && retryCount <= maxRetries; retryCount++) {
				if (!sucessfullySigned && retryCount > 0) {
					logDebug("Unable to sign '"+path+"' on '"+ serverURI +"'. Will retry ("+(retryCount)+" / "+maxRetries+") in "+ retryInterval +" "+unit.name()+"...");
					try {
						unit.sleep(retryInterval);
					} catch (InterruptedException e) {
						logDebug("Signing thread has been interrupted", e);
						Thread.currentThread().interrupt();
					}
				} 
				
				try {
					sucessfullySigned = sign(path, partName, httpClient);
				} catch (Exception e) {
					lastThrownException = e;
					logDebug("Error occured while communicating with '"+ serverURI +"'", e);
				}
			}
			
			if (lastThrownException != null) {
				propagate(lastThrownException);
			}
			
			return sucessfullySigned;
		}
	}

	private static void propagate(Exception exception) throws IOException {
		if (exception instanceof RuntimeException) {
			throw (RuntimeException)exception;
		} else if (exception instanceof IOException) {
			throw (IOException)exception;
		} else {
			throw new RuntimeException(exception);
		}
	}

	private static int checkPositive(int n, String msg) {
		if (n < 0) {
			throw new IllegalArgumentException(msg);
		} else {
			return n;
		}
	}

	private boolean sign(Path source, String partName, CloseableHttpClient httpClient) throws IOException {
		try (CloseableHttpResponse response = sendSigningRequest(source, partName, httpClient)) {
			final StatusLine statusLine = response.getStatusLine();
			final HttpEntity resEntity = response.getEntity();
		
			final boolean ret;
			if (statusLine != null && statusLine.getStatusCode() == HttpStatus.SC_OK && resEntity != null) {
				try (InputStream is = new BufferedInputStream(resEntity.getContent())) {
					Files.copy(is, source, StandardCopyOption.REPLACE_EXISTING);
				}
				ret = true;
			} else {
				handleError(statusLine, resEntity);
				ret = false;
			}
			
			return ret;
		}
	}

	/**
	 * Send the given file to the server and return its response.
	 * 
	 * @param filetoBeSigned
	 *            the file to be signed.
	 * @return the HTTP response of the server.
	 * @throws IOException
	 *             if something wrong happen during the request.
	 */
	private CloseableHttpResponse sendSigningRequest(Path filetoBeSigned, String partName, CloseableHttpClient client) throws IOException {
		logDebug("Sending '" + filetoBeSigned.toString() + "' for signing to '" + serverURI + "'");
		
		HttpPost post = new HttpPost(serverURI);

		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

		try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(filetoBeSigned, StandardOpenOption.READ))) {
			InputStreamBody inputStreamBody = new InputStreamBody(inputStream, ContentType.DEFAULT_BINARY, filetoBeSigned.getFileName().toString());
			builder.addPart(partName, inputStreamBody);
			post.setEntity(builder.build());
			return client.execute(post);
		}
	}

	/**
	 * Logs the most completely possible the response from the server.
	 * 
	 * @param statusLine
	 *            the status line of the response. Can be {@code null}
	 * @param resEntity
	 *            the entity of the response. Can be {@code null}
	 */
	private void handleError(final StatusLine statusLine, final HttpEntity resEntity) {
		if (statusLine != null) {
			logDebug("Signing server replied with: '" + statusLine.toString() + "'");
		} else {
			logDebug("Signing server did not replied OK.");
		}
		if (resEntity != null) {
			try (InputStream is = new BufferedInputStream(resEntity.getContent())) {
				String message = IOUtil.toString(is, "UTF-8");
				logDebug("Signing server failed by returning content '" + message + "'");
			} catch (IOException e) {
				logDebug("Error occurred while reading the content returned by the signing server", e);
			}
		}
	}
	
	private void logDebug(String msg) {
		log.debug("[" + new Date() + "] " + msg);
	}
	
	private void logDebug(String msg, Exception e) {
		log.debug("[" + new Date() + "] " + msg, e);
	}
}
