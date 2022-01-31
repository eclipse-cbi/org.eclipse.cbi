/*******************************************************************************
 * Copyright (c) 2015, 2016 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.common.security.SignatureAlgorithm;

import com.google.auto.value.AutoValue;

public interface JarSigner {

	/**
	 * Sign the given Jar file.
	 *
	 * @param jarfile
	 *            the file to sign.
	 * @return the number of Jar that has been signed.
	 * @throws MojoExecutionException
	 */
	int sign(Path jarfile, Options options) throws IOException;

	@AutoValue
	abstract class Options {
		
		private static final Duration CONNECT_TIMEOUT__DEFAULT = Duration.ofSeconds(5);
		private static final Duration TIMEOUT__DEFAULT = Duration.ZERO;
		private static final String SIGFILE__DEFAULT = "";

		public abstract MessageDigestAlgorithm digestAlgorithm();
		public abstract SignatureAlgorithm signatureAlgorithm();
		@Deprecated
		public abstract Duration connectTimeout();
		public abstract Duration timeout();
		public abstract String sigFile();
		
		public static Builder builder() {
			return new AutoValue_JarSigner_Options.Builder()
					.signatureAlgorithm(SignatureAlgorithm.DEFAULT)
					.digestAlgorithm(MessageDigestAlgorithm.DEFAULT)
					.connectTimeout(CONNECT_TIMEOUT__DEFAULT)
					.timeout(TIMEOUT__DEFAULT)
					.sigFile(SIGFILE__DEFAULT);
		}
		
		public static Builder copy(Options option) {
			return builder()
					.digestAlgorithm(option.digestAlgorithm())
					.signatureAlgorithm(option.signatureAlgorithm())
					.connectTimeout(option.connectTimeout())
					.timeout(option.timeout())
					.sigFile(option.sigFile());
		}
		
		@AutoValue.Builder
		public static abstract class Builder {
			public abstract Builder digestAlgorithm(MessageDigestAlgorithm digestAlgorithm);
			public abstract Builder signatureAlgorithm(SignatureAlgorithm signatureAlgorithm);
			@Deprecated
			public abstract Builder connectTimeout(Duration connectTimeout);
			public abstract Builder timeout(Duration timeout);
			public abstract Builder sigFile(String sigFile);
			public abstract Options build();
		}
	}
}