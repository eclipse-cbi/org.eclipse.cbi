/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.flatpakager.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

@AutoValue
public abstract class Manifest {
	public static final String DEFAULT_BRANCH = "master";
	public static final String DEFAULT_RUNTIME = "org.gnome.Platform";
	public static final String DEFAULT_RUNTIMEVERSION = "3.34";
	public static final String DEFAULT_SDK = "org.gnome.Sdk";
	public static final String DEFAULT_COMMAND = "eclipse";
	public static final String DEFAULT_FLATPAKVERSION = "1.0.2";

	// Probably always want JDK
	public static final List<String> DEFAULT_SDK_EXTENSIONS = Collections.unmodifiableList(
			Arrays.asList("org.freedesktop.Sdk.Extension.openjdk11", "org.freedesktop.Sdk.Extension.node12"));

	// Fairly liberal by default: Access to host file system, windowing system and
	// network connection. Allow communication with the host DBus session bus.
	public static final List<String> DEFAULT_FINISH_ARGS = Collections.unmodifiableList(Arrays.asList(
			"--filesystem=host", "--share=network", "--share=ipc", "--socket=x11", "--socket=wayland", "--allow=devel",
			"--socket=session-bus", "--device=dri", "--env=PATH=/app/bin:/app/jdk/bin:/app/node/bin:/usr/bin"));

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

	@JsonProperty("finish-args")
	public abstract ImmutableSet<String> finishArgs();

	@JsonProperty("modules")
	public abstract ImmutableSet<Module> modules();

	public static Builder builder() {
		Manifest.Builder builder = new AutoValue_Manifest.Builder().branch(DEFAULT_BRANCH)
				.runtime(DEFAULT_RUNTIME)
				.runtimeVersion(DEFAULT_RUNTIMEVERSION)
				.sdk(DEFAULT_SDK)
				.command(DEFAULT_COMMAND);
		for (String ext : DEFAULT_SDK_EXTENSIONS) {
			builder.addSdkExtension(ext);
		}
		return builder;
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

		abstract ImmutableSet.Builder<String> finishArgsBuilder();

		public Builder addFinishArg(String... value) {
			finishArgsBuilder().add(value);
			return this;
		}

		abstract ImmutableSet.Builder<Module> modulesBuilder();

		public Builder addModule(Module... value) {
			modulesBuilder().add(value);
			return this;
		}

		public abstract Manifest build();
	}
}
