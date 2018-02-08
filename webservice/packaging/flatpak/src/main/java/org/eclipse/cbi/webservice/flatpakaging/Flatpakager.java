/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
	 * This method does the actual work of generating the Flatpak repository.
	 * 
	 * @param sign
	 *            true if the Flatpak repository should be signed
	 * @param manifest
	 *            path to the Flatpak application manifest file
	 * @throws IOException
	 *             if anything went wrong during the process
	 */
	public void generateFlatpakRepo(boolean sign, Path manifest) throws IOException {
		ImmutableList.Builder<String> builderArgs = ImmutableList.builder();
		builderArgs.add("flatpak-builder");
		builderArgs.add("--disable-cache");
		builderArgs.add("--disable-download");
		builderArgs.add("--disable-updates");
		builderArgs.add("--repo=" + repository().toString());
		if (sign) {
			builderArgs.add("--gpg-sign=" + gpgKey());
			builderArgs.add("--gpg-homedir=" + gpgHome());
		}
		builderArgs.add(work().resolve("build").toString());
		builderArgs.add(manifest.toString());
		executeProcess(builderArgs.build());

		ImmutableList.Builder<String> deltaArgs = ImmutableList.builder();
		deltaArgs.add("flatpak");
		deltaArgs.add("build-update-repo");
		deltaArgs.add("--generate-static-deltas");
		if (sign) {
			deltaArgs.add("--gpg-sign=" + gpgKey());
			deltaArgs.add("--gpg-homedir=" + gpgHome());
		}
		deltaArgs.add(repository().toString());
		executeProcess(deltaArgs.build());
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

	public abstract Path repository();

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

		public abstract Builder repository(Path repository);

		public abstract Builder work(Path work);

		public abstract Flatpakager build();
	}

}
