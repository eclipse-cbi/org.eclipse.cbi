/*******************************************************************************
 * Copyright (c) 2015, 2016 Eclipse Foundation and others
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
import java.nio.file.Path;

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
		
		private static final int CONNECT_TIMEOUT_MS__DEFAULT = 20000;
		private static final String SIGFILE__DEFAULT = "";

		public abstract MessageDigestAlgorithm digestAlgorithm();
		public abstract SignatureAlgorithm signatureAlgorithm();
		public abstract int connectTimeoutMillis();
		public abstract String sigFile();
		
		public static Builder builder() {
			return new AutoValue_JarSigner_Options.Builder()
					.signatureAlgorithm(SignatureAlgorithm.DEFAULT)
					.digestAlgorithm(MessageDigestAlgorithm.DEFAULT)
					.connectTimeoutMillis(CONNECT_TIMEOUT_MS__DEFAULT)
					.sigFile(SIGFILE__DEFAULT);
		}
		
		public static Builder copy(Options option) {
			return builder()
					.digestAlgorithm(option.digestAlgorithm())
					.signatureAlgorithm(option.signatureAlgorithm())
					.connectTimeoutMillis(option.connectTimeoutMillis())
					.sigFile(option.sigFile());
		}
		
		@AutoValue.Builder
		public static abstract class Builder {
			public abstract Builder digestAlgorithm(MessageDigestAlgorithm digestAlgorithm);
			public abstract Builder signatureAlgorithm(SignatureAlgorithm signatureAlgorithm);
			public abstract Builder connectTimeoutMillis(int connectTimeoutMillis);
			public abstract Builder sigFile(String sigFile);
			public abstract Options build();
		}
	}
}