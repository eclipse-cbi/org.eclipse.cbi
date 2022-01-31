/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat, Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.flatpakager.model;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

@AutoValue
public abstract class Module {

	@JsonProperty("name")
	public abstract String name();

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("buildsystem")
	@Nullable
	public abstract String buildSystem();

	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	@JsonProperty("no-autogen")
	public abstract boolean noAutogen();

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@JsonProperty("build-commands")
	public abstract ImmutableSet<String> buildCommands();

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@JsonProperty("sources")
	public abstract ImmutableSet<Source> sources();

	public static Builder builder() {
		return new AutoValue_Module.Builder().noAutogen(false);
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder name(String value);

		public abstract Builder buildSystem(String value);

		public abstract Builder noAutogen(boolean value);

		abstract ImmutableSet.Builder<String> buildCommandsBuilder();

		public Builder addbuildCommand(String... value) {
			buildCommandsBuilder().add(value);
			return this;
		}

		abstract ImmutableSet.Builder<Source> sourcesBuilder();

		public Builder addSource(Source... value) {
			sourcesBuilder().add(value);
			return this;
		}

		public abstract Module build();
	}
}
