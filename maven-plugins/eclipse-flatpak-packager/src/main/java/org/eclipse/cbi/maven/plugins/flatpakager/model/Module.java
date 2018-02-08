/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.flatpakager.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

@AutoValue
public abstract class Module {

	@JsonProperty("name")
	public abstract String name();

	@JsonProperty("no-autogen")
	public abstract boolean noAutogen();

	@JsonProperty("sources")
	public abstract ImmutableSet<Source> sources();

	public static Builder builder() {
		return new AutoValue_Module.Builder();
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder name(String value);

		public abstract Builder noAutogen(boolean value);

		abstract ImmutableSet.Builder<Source> sourcesBuilder();

		public Builder addSource(Source... value) {
			sourcesBuilder().add(value);
			return this;
		}

		public abstract Module build();
	}
}
