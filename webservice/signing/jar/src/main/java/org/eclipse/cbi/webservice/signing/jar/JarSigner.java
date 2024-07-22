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
package org.eclipse.cbi.webservice.signing.jar;

import static com.google.common.base.Preconditions.checkState;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Splitter;
import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.common.security.SignatureAlgorithm;
import org.eclipse.cbi.webservice.util.ProcessExecutor;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * Abstraction of a call to jarsigner command.
 */
@AutoValue
public abstract class JarSigner {

	/**
	 * The credential holder in case Google KMS is being used as signing backend.
	 */
	private GoogleCredentials kmsCredentials = null;

	/**
	 * Returns the configuration object for this JarSigner instance.
	 *
	 * @return the configuration for this JarSigner instance
	 */
	abstract JarSignerConfiguration configuration();

	/**
	 * Returns the executor that will execute the native command
	 * 
	 * @return the executor that will execute the native command
	 */
	abstract ProcessExecutor processExecutor();

	/**
	 * Creates and returns a new builder for this class.
	 * 
	 * @return a new builder for this class.
	 */
	public static Builder builder() {
		return new AutoValue_JarSigner.Builder();
	}
	

	/**
	 * Sign the given jar file with the configured jarsigner command.
	 * 
	 * @param jar
	 *            the jar to be sign
	 * @return the path to the signed jar file (the same as the one given in
	 *         parameter).
	 * @throws IOException
	 *             if the execution of the command did not end properly.
	 */
	public Path signJar(Path jar) throws IOException {
		return signJar(jar, SignatureAlgorithm.DEFAULT, MessageDigestAlgorithm.DEFAULT, "");
	}

	public Path signJar(Path jar, SignatureAlgorithm sigAlg, MessageDigestAlgorithm digestAlg, String sigFile)
			throws IOException {
		Objects.requireNonNull(sigAlg);
		Objects.requireNonNull(digestAlg);
		final StringBuilder output = new StringBuilder();
		int jarSignerExitValue =
				processExecutor().exec(createCommand(jar, sigAlg, digestAlg, sigFile), output, configuration().getTimeout(), TimeUnit.SECONDS);
		if (jarSignerExitValue != 0) {
			throw new IOException(Joiner.on('\n').join(
				"The '" + configuration().getJarSigner().toString() + "' command exited with value '" + jarSignerExitValue + "'",
				"'" + configuration().getJarSigner().toString() + "' output:",
				output));
	}
		return jar;
	}
	
	/**
	 * Creates the jarsigner command with proper options.
	 * 
	 * @param jar
	 *            the path of the file to be signed.
	 * @param sigAlg
	 *            the signature algorithm to use when digesting the entries
	 *            of a JAR file. Must not be <code>null</code>.
	 * @param digestAlg
	 *            the message digest algorithm to use when digesting the entries
	 *            of a JAR file. Must not be <code>null</code>.
	 * @param sigFile 
	 *            then the base file name for the signatures files.
	 * @return a list of string composing the command (see
	 *         {@link ProcessBuilder} for format).
	 */
	private ImmutableList<String> createCommand(Path jar, SignatureAlgorithm sigAlg, MessageDigestAlgorithm digestAlg, String sigFile) {
		ImmutableList.Builder<String> command = ImmutableList.<String>builder().add(configuration().getJarSigner().toString());

		if (!Strings.isNullOrEmpty(configuration().getJavaArgs())) {
			Iterable<String> arguments =
				Splitter.on(' ')
						.trimResults()
						.omitEmptyStrings()
						.split(configuration().getJavaArgs());

			for (String arg : arguments) {
				command.add("-J" + arg);
			}
		}

		if (!Strings.isNullOrEmpty(configuration().getHttpProxyHost())) {
			command.add("-J-Dhttp.proxyHost=" + configuration().getHttpProxyHost())
				   .add("-J-Dhttp.proxyPort=" + configuration().getHttpProxyPort());
		}
		
		if (!Strings.isNullOrEmpty(configuration().getHttpsProxyHost())) {
			command.add("-J-Dhttps.proxyHost=" + configuration().getHttpsProxyHost())
				   .add("-J-Dhttps.proxyPort=" + configuration().getHttpsProxyPort());
		}
		
		if (sigAlg != SignatureAlgorithm.DEFAULT) {
			command.add("-sigalg", sigAlg.standardName());
		}
		
		if (digestAlg != MessageDigestAlgorithm.DEFAULT) {
			command.add("-digestalg", digestAlg.standardName());
		}
		
		String tsa = configuration().getTimeStampingAuthority().toString();
		if (!Strings.isNullOrEmpty(tsa)) {
			command.add("-tsa", tsa);
		}

		if (!Strings.isNullOrEmpty(configuration().getStoreType())) {
			command.add("-storetype", configuration().getStoreType());
		}

		if (!Strings.isNullOrEmpty(configuration().getProviderClass())) {
			command.add("-providerClass", configuration().getProviderClass());
		}

		if (!Strings.isNullOrEmpty(configuration().getProviderArg())) {
			command.add("-providerArg", configuration().getProviderArg());
		}

		if (configuration().getCertificateChain() != null) {
			command.add("-certchain", configuration().getCertificateChain().toString());
		}

		if (!Strings.isNullOrEmpty(sigFile)) {
			command.add("-sigfile", sigFile);
		}

		if (configuration().getKeystore() == null) {
			command.add("-keystore", "NONE");
		} else {
			command.add("-keystore", configuration().getKeystore().toString());
		}

		if (configuration().getKeystorePassword() != null) {
			command.add("-storepass", configuration().getKeystorePassword());
		} else if ("GOOGLECLOUD".equals(configuration().getStoreType())) {
			command.add("-storepass", googleAccessToken());
		}

		command
			.add(jar.toString())
			.add(configuration().getKeystoreAlias());
		
		return command.build();
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
			throw new RuntimeException("Tried to retrieve a Google Cloud Access Token while no credentials have been provided");
		}
	}

	/**
	 * A builder of JarSigner.
	 */
	@AutoValue.Builder
	public static abstract class Builder {

		/**
		 * Sets the configuration to use.
		 *
		 * @return this builder for daisy-chaining.
		 */
		public abstract Builder configuration(JarSignerConfiguration configuration);

		/**
		 * Sets the process executor that will execute the native jarsigner command.
		 *
		 * @return this builder for daisy-chaining.
		 */
		public abstract Builder processExecutor(ProcessExecutor executor);

		abstract JarSigner autoBuild();

		/**
		 * Creates and returns a new JarSigner object with the state of this
		 * builder.
		 *
		 * @return a new JarSigner object with the state of this builder.
		 */
		public JarSigner build() {
			JarSigner jarSigner = autoBuild();

			JarSignerConfiguration configuration = jarSigner.configuration();

			checkState(configuration.getTimeout() > 0, "The timeout must be strictly positive");
			if (!Strings.isNullOrEmpty(configuration.getHttpProxyHost())) {
				checkState(configuration.getHttpProxyPort() > 0, "The HTTP proxy port must be specified and stricly positive when HTTP proxy host is");
			}
			if (!Strings.isNullOrEmpty(configuration.getHttpsProxyHost())) {
				checkState(configuration.getHttpsProxyPort() > 0, "The HTTPS proxy port must be specified and stricly positive when HTTPS proxy host is");
			}

			jarSigner.initKmsCredentialsIfNeeded();
			return jarSigner;
		}
	}

}
