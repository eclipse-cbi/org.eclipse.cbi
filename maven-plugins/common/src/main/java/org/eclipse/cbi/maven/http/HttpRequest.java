/*******************************************************************************
 * Copyright (c) 2015, 2016 Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.http;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public final class HttpRequest {
	
	@AutoValue
	public static abstract class Config {
		private static final int CONNECT_TIMEOUT_MS__DEFAULT = 20000;
		
		Config() {
			// prevents instantiation
		}
		public abstract int connectTimeoutMillis();
		
		public static Builder builder() {
			return new AutoValue_HttpRequest_Config.Builder();
		}
		
		public static Config defaultConfig() {
			return builder().connectTimeoutMillis(CONNECT_TIMEOUT_MS__DEFAULT).build();
		}
		
		@AutoValue.Builder
		public static abstract class Builder {
			public abstract Builder connectTimeoutMillis(int timeout);
			public abstract Config build();
		}
	}

	private final URI serverUri;
	private final ImmutableMap<String, String> stringParams;
	private final ImmutableMap<String, Path> pathParams;

	HttpRequest(URI serverUri, ImmutableMap<String, String> stringParams, ImmutableMap<String, Path> pathParams) {
		this.serverUri = Objects.requireNonNull(serverUri);
		this.stringParams = Objects.requireNonNull(stringParams);
		this.pathParams = Objects.requireNonNull(pathParams);
	}
	
	public Map<String, Path> pathParameters() {
		return pathParams;
	}
	
	public Map<String, String> stringParameters() {
		return stringParams;
	}
	
	public URI serverUri() {
		return serverUri;
	}
	
	@Override
	public String toString() {
		final ToStringHelper toStringHelper = MoreObjects.toStringHelper(this)
				.add("serverUri", serverUri());
		
		for (Map.Entry<String, Path> e : pathParameters().entrySet()) {
			toStringHelper.add(e.getKey(), "@" + e.getValue().toString());
		}
		for (Entry<String, String> e : stringParameters().entrySet()) {
			toStringHelper.add(e.getKey(), e.getValue());
		}
		
		return toStringHelper.toString();
	}
	
	public static Builder on(URI serverUri) {
		return new Builder().serverUri(serverUri);
	}
	
	public static class Builder {
		
		private URI serverUri;

		private final ImmutableMap.Builder<String, String> stringParams;
		
		private final ImmutableMap.Builder<String, Path> pathParams;

		Builder() {
			stringParams = ImmutableMap.builder();
			pathParams = ImmutableMap.builder();
		}

		Builder serverUri(URI serverUri) {
			this.serverUri = Objects.requireNonNull(serverUri);
			return this;
		}

		public Builder withParam(String name, Path path) {
			Preconditions.checkArgument(!Objects.requireNonNull(name).isEmpty());
			Preconditions.checkArgument(Files.isRegularFile(path));
			pathParams.put(name, path);
			return this;
		}
		
		public Builder withParam(String name, String value) {
			Preconditions.checkArgument(!Objects.requireNonNull(name).isEmpty());
			stringParams.put(name, value);
			return this;
		}
		
		public HttpRequest build() {
			return new HttpRequest(serverUri, stringParams.build(), pathParams.build());
		}
	}
}
