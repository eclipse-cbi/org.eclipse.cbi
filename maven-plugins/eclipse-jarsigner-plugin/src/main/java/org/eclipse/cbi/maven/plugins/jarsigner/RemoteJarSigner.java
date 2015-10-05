/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.maven.Logger;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.AbstractCompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpResult;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class RemoteJarSigner extends FilteredJarSigner {

	/**
	 * The name of the part as it will be send to the signing server.
	 */
	private static final String PART_NAME = "file";
	
	abstract URI serverUri();
	
	abstract HttpClient httpClient();
	
	/**
	 * The log on which feedback will be provided.
	 */
	abstract Log log();
	
	@Override
	public int doSignJar(final Path jar, Options options) throws IOException {
		int ret = 0;
		final HttpRequest request = HttpRequest.on(serverUri())
				.withParam(PART_NAME, jar)
				.withParam("digestalg", options.digestAlgorithm().standardName())
				.build();
		
		OverwriteJarOnSuccess completionListener = new OverwriteJarOnSuccess(jar.getParent(), jar.getFileName().toString(), this.getClass().getSimpleName(), new MavenLogger(log()), jar);
		if (httpClient().send(request, completionListener)) {
			ret = 1;
		}
		return ret;
	}

	private static final class OverwriteJarOnSuccess extends AbstractCompletionListener {
		private final Path jar;

		private OverwriteJarOnSuccess(Path errorLogFolder, String errologPrefix, String errorLogSuffix, Logger log, Path jar) {
			super(errorLogFolder, errologPrefix, errorLogSuffix, log);
			this.jar = jar;
		}

		@Override
		public void onSuccess(HttpResult result) throws IOException {
			result.copyContent(jar, StandardCopyOption.REPLACE_EXISTING);
		}
	}
	
	public static Builder builder() {
		return new AutoValue_RemoteJarSigner.Builder().filter(Filters.ALWAYS_SIGN);
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder filter(Filter filter);
		public abstract Builder serverUri(URI serverUri);
		public abstract Builder httpClient(HttpClient client);
		public abstract Builder log(Log log);
		public abstract RemoteJarSigner build();
	}
}
