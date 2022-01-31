/*******************************************************************************
 * Copyright (c) 2018, 2022 Red Hat, Inc. and others.
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
package org.eclipse.cbi.webservice.flatpakaging;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class Flatpakager {

	private static final Logger logger = LoggerFactory.getLogger(Flatpakager.class);

	/**
	 * This method does the actual work of building the Flatpak application and
	 * creating a single-file bundle for sending back to the client.
	 *
	 * @param flatpakId ID of the Flatpak application
	 * @param branch    the ostree repo branch of the Flatpak application
	 * @param sign      true if the Flatpak application should be signed
	 * @param manifest  path to the Flatpak application manifest file
	 * @throws IOException if anything went wrong during the process
	 */
	public void generateFlatpakBundle(String flatpakId, String branch, boolean sign, Path manifest) throws IOException {
		// Build application and generate a ostree repo
		ImmutableList.Builder<String> builderArgs = ImmutableList.builder();
		builderArgs.add("flatpak-builder");
		builderArgs.add("--force-clean");
		builderArgs.add("--disable-cache");
		builderArgs.add("--disable-download");
		builderArgs.add("--disable-updates");
		builderArgs.add("--default-branch=" + branch);
		builderArgs.add("--state-dir=" + work().resolve(".flatpak-builder").toString());
		builderArgs.add("--repo=" + work().resolve("repo").toString());
		if (sign) {
			builderArgs.add("--gpg-sign=" + gpgKey());
			builderArgs.add("--gpg-homedir=" + gpgHome());
		}
		builderArgs.add(work().resolve("build").toString());
		builderArgs.add(manifest.toString());
		executeProcess(builderArgs.build());

		// Create single-file bundle from the application in the ostree repo
		ImmutableList.Builder<String> bundleArgs = ImmutableList.builder();
		bundleArgs.add("flatpak");
		bundleArgs.add("build-bundle");
		if (sign) {
			builderArgs.add("--gpg-sign=" + gpgKey());
			builderArgs.add("--gpg-homedir=" + gpgHome());
		}
		bundleArgs.add(work().resolve("repo").toString());
		bundleArgs.add(work().resolve(flatpakId + ".flatpak").toString());
		bundleArgs.add(flatpakId);
		bundleArgs.add(branch);
		executeProcess(bundleArgs.build());
	}

	private void executeProcess(ImmutableList<String> args) throws IOException {
		logger.info("The following 'flatpak' command will be executed: '" + String.join(" ", args) + "'");
		final StringBuilder output = new StringBuilder();
		int exitCode = processExecutor().exec(args, output, timeout(), TimeUnit.SECONDS);
		if (exitCode != 0) {
			throw new IOException(String.join("\n", "The 'flatpak' command exited with value '" + exitCode + "'",
					"Command output:", output));
		}
	}

	public abstract ProcessExecutor processExecutor();

	public abstract long timeout();

	public abstract String gpgKey();

	public abstract Path gpgHome();

	public abstract Path work();

	public static Builder builder() {
		return new AutoValue_Flatpakager.Builder();
	}

	@AutoValue.Builder
	public abstract static class Builder {
		public abstract Builder processExecutor(ProcessExecutor executor);

		public abstract Builder timeout(long timeout);

		public abstract Builder gpgKey(String gpgKey);

		public abstract Builder gpgHome(Path gpgHome);

		public abstract Builder work(Path work);

		public abstract Flatpakager build();
	}

}
