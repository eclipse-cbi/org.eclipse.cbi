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
public abstract class Manifest {
	public static final String DEFAULT_BRANCH = "master";
	public static final String DEFAULT_RUNTIME = "org.gnome.Platform";
	public static final String DEFAULT_RUNTIMEVERSION = "3.28";
	public static final String DEFAULT_SDK = "org.gnome.Sdk";
	public static final String DEFAULT_COMMAND = "eclipse";

	// Probably always want JDK
	public static final String[] DEFAULT_SDK_EXTENSIONS = { "org.freedesktop.Sdk.Extension.openjdk9" };

	// Fairly liberal by default: Access to host file system, windowing system and
	// network connection. Allow communication with the Flatpak DBus API.
	public static final String[] DEFAULT_FINISH_ARGS = { "--env=PATH=/app/bin:/usr/bin", "--filesystem=host",
			"--share=ipc", "--socket=x11", "--socket=wayland", "--share=network", "--allow=devel",
			"--talk-name=org.freedesktop.Flatpak" };

	@JsonProperty("id")
	public abstract String id();

	@JsonProperty("branch")
	public abstract String branch();

	@JsonProperty("runtime")
	public abstract String runtime();

	@JsonProperty("runtime-version")
	public abstract String runtimeVersion();

	@JsonProperty("sdk")
	public abstract String sdk();

	@JsonProperty("sdk-extensions")
	public abstract ImmutableSet<String> sdkExtensions();

	@JsonProperty("command")
	public abstract String command();

	@JsonProperty("modules")
	public abstract ImmutableSet<Module> modules();

	@JsonProperty("finish-args")
	public abstract ImmutableSet<String> finishArgs();

	public static Builder builder() {
		return new AutoValue_Manifest.Builder().branch(DEFAULT_BRANCH).addSdkExtension(DEFAULT_SDK_EXTENSIONS)
				.runtime(DEFAULT_RUNTIME).runtimeVersion(DEFAULT_RUNTIMEVERSION).sdk(DEFAULT_SDK)
				.command(DEFAULT_COMMAND).addFinishArg(DEFAULT_FINISH_ARGS);
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder id(String value);

		public abstract Builder branch(String value);

		public abstract Builder runtime(String value);

		public abstract Builder runtimeVersion(String value);

		public abstract Builder sdk(String value);

		abstract ImmutableSet.Builder<String> sdkExtensionsBuilder();

		public Builder addSdkExtension(String... value) {
			sdkExtensionsBuilder().add(value);
			return this;
		}

		public abstract Builder command(String value);

		abstract ImmutableSet.Builder<Module> modulesBuilder();

		public Builder addModule(Module... value) {
			modulesBuilder().add(value);
			return this;
		}

		abstract ImmutableSet.Builder<String> finishArgsBuilder();

		public Builder addFinishArg(String... value) {
			finishArgsBuilder().add(value);
			return this;
		}

		public abstract Manifest build();
	}
}
