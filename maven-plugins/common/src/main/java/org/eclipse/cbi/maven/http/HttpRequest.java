/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others.
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
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public final class HttpRequest {
	
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
		final StringBuilder parameters = new StringBuilder();
		parameters.append(Joiner.on(", ").withKeyValueSeparator("=@").join(pathParameters()));
		if (!pathParameters().isEmpty() && !stringParameters().isEmpty()) {
			parameters.append(", ");
		}
		parameters.append(Joiner.on(", ").withKeyValueSeparator("=").join(stringParameters()));
		
		return MoreObjects.toStringHelper(this)
			.add("serverUri", serverUri())
			.add("request-parameters", parameters.toString())
			.toString();
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
