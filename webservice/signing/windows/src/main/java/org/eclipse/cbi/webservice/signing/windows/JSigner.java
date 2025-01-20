/*******************************************************************************
 * Copyright (c) 2015, 2022 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.windows;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auto.value.AutoValue;
import net.jsign.AuthenticodeSigner;
import net.jsign.KeyStoreBuilder;
import net.jsign.Signable;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;

/**
 * Using jsign to sign windows executables.
 */
@AutoValue
public abstract class JSigner implements CodeSigner {

	/**
	 * The credential holder in case Google KMS is being used as signing backend.
	 */
	private GoogleCredentials kmsCredentials = null;

	/**
	 * Returns the configuration object for this JarSigner instance.
	 *
	 * @return the configuration for this JarSigner instance
	 */
	abstract JSignerProperties configuration();

	abstract Path tempFolder();

	/**
	 * Creates and returns a new builder for this class.
	 * 
	 * @return a new builder for this class.
	 */
	public static Builder builder() {
		return new AutoValue_JSigner.Builder();
	}

	@Override
	public Path sign(Path file) {
		try {
			KeyStoreBuilder keyStoreBuilder = new KeyStoreBuilder()
					.storetype(configuration().getStoreType())
					.keystore(configuration().getKeystore());
			if (kmsCredentials!=null) {
				keyStoreBuilder.storepass(googleAccessToken());
			} else if (configuration().getStorePass() != null) {
				keyStoreBuilder.storepass(configuration().getStorePass());
			}
			try {
				if (configuration().getCertificateChain() != null) {
					keyStoreBuilder.certfile(configuration().getCertificateChain().toFile());
				}
			} catch(IllegalArgumentException e){
				// Ignore missing certficate chain;could be stored in keystore
			}
			KeyStore keystore =keyStoreBuilder.build();

			AuthenticodeSigner signer =
					new AuthenticodeSigner(keystore, configuration().getKeyAlias(), null)
							.withProgramURL(configuration().getURI().toString())
							.withProgramName(configuration().getDescription())
							.withTimestamping(true)
							.withTimestampingAuthority(configuration().getTimestampURIs().stream().map(URI::toString).toList().toArray(new String[0]))
							.withTimestampingRetries(3);

			try (Signable signable = Signable.of(file.toFile())) {
				signer.sign(signable);
			}

			return file;
		} catch (Exception ex) {
			throw new RuntimeException("Failed signing of '" + file.getFileName() + "'", ex);
		}
	}

	private void initKmsCredentialsIfNeeded() {
		if (configuration().getGoogleCloudCredentials() != null) {
			try {
				kmsCredentials =
					GoogleCredentials.fromStream(new FileInputStream(configuration().getGoogleCloudCredentials().toString()))
								     .createScoped(List.of("https://www.googleapis.com/auth/cloudkms"));
			} catch (IOException ex) {
				// should not happen
				throw new RuntimeException(ex);
			}
		}
	}

	private String googleAccessToken() {
		if (kmsCredentials != null) {
			try {
				kmsCredentials.refreshIfExpired();
				return kmsCredentials.getAccessToken().getTokenValue();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		} else {
			return "NONE";
		}
	}

	/**
	 * A builder of JSigner.
	 */
	@AutoValue.Builder
	public static abstract class Builder {

		/**
		 * Sets the configuration to use.
		 *
		 * @return this builder for daisy-chaining.
		 */
		abstract Builder configuration(JSignerProperties configuration);

		abstract Builder tempFolder(Path tempFolder);

		abstract JSigner autoBuild();

		/**
		 * Creates and returns a new JarSigner object with the state of this
		 * builder.
		 *
		 * @return a new JarSigner object with the state of this builder.
		 */
		public JSigner build() {
			JSigner jSigner = autoBuild();
			jSigner.initKmsCredentialsIfNeeded();
			return jSigner;
		}
	}

}
