/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
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
package org.eclipse.cbi.webservice.signing.macosx;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import org.eclipse.cbi.webservice.util.ProcessExecutor;

@AutoValue
public abstract class AppleCodeSigner extends CodeSigner {

	public abstract Path tempFolder();
	public abstract ProcessExecutor processExecutor();
	public abstract long codeSignTimeout();
	abstract long securityUnlockTimeout();

	abstract Path keyChain();
	abstract String keyChainPassword();
	abstract String identityApplication();
	abstract String identityInstaller();
	abstract String timeStampAuthority();

	abstract ImmutableList<String> codeSignCommandPrefix();
	abstract ImmutableList<String> productSignCommandPrefix();
	abstract ImmutableList<String> securityUnlockCommand();

	protected void unlockKeychain() throws IOException {
		final StringBuilder output = new StringBuilder();
		final int securityExitValue =
			processExecutor().exec(securityUnlockCommand(), output, securityUnlockTimeout(), TimeUnit.SECONDS);
		if (securityExitValue != 0) {
			throw new IOException(Joiner.on('\n').join(
				"The 'security unlock' command exited with value '" + securityExitValue + "'",
				"'security unlock' output:",
				output));
		}
	}

	@Override
	protected ImmutableList<String> signApplicationCommand(Path path, Options options) {
		return ImmutableList.<String>builder()
			.addAll(codeSignCommandPrefix())
			.addAll(toArgsList(options))
			.add(path.toString())
			.build();
	}

	private List<String> toArgsList(Options options) {
		ImmutableList.Builder<String> ret = ImmutableList.builder();
		if (options.deep()) {
			ret.add("--deep");
		}
		if (options.force()) {
			ret.add("--force");
		}
		if (options.entitlements().isPresent()) {
			ret.add("--entitlements", options.entitlements().get().toString());
		}
		return ret.build();
	}

	@Override
	protected ImmutableList<String> signInstallerCommand(Path input, Path output) {
		return ImmutableList.<String>builder()
			.addAll(productSignCommandPrefix())
			.add(input.toString())
			.add(output.toString())
			.build();
	}

	public static Builder builder() {
		return new AutoValue_AppleCodeSigner.Builder();
	}

	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}

		public abstract Builder tempFolder(Path tempFolder);
		public abstract Builder processExecutor(ProcessExecutor executor);

		public abstract Builder codeSignTimeout(long codeSignTimeout);
		public abstract Builder securityUnlockTimeout(long securityUnlockTimeout);

		public abstract Builder keyChain(Path keyChain);
		abstract Path keyChain();
		public abstract Builder keyChainPassword(String keyChainPassword);
		abstract String keyChainPassword();
		public abstract Builder identityApplication(String identityApplication);
		abstract String identityApplication();
		public abstract Builder identityInstaller(String identityInstaller);
		abstract String identityInstaller();
		public abstract Builder timeStampAuthority(String timeStampAuthority);
		abstract String timeStampAuthority();

		abstract Builder codeSignCommandPrefix(ImmutableList<String> codeSignCommandPrefix);
		abstract Builder productSignCommandPrefix(ImmutableList<String> productSignCommandPrefix);
		abstract Builder securityUnlockCommand(ImmutableList<String> securityUnlockCommand);

		abstract AppleCodeSigner autoBuild();

		/**
		 * Creates and returns a new instance of {@link AppleCodeSigner} as
		 * configured by this builder. The following checks are made:
		 * <ul>
		 * <li>The temporary folder must exist.</li>
		 * <li>The keychain file must exist.</li>
		 * <li>The certificate name must not be empty.</li>
		 * </ul>
		 *
		 * @return a new instance of {@link AppleCodeSigner} as configured by this
		 *         builder.
		 */
		public AppleCodeSigner build() {
			checkState(!identityApplication().isEmpty(), "Certificate name must not be empty");
			checkState(Files.exists(keyChain()) && Files.isRegularFile(keyChain()), "Keychain file must exists");

			ImmutableList.Builder<String> codeSignCommandPrefix = ImmutableList.builder();
			codeSignCommandPrefix
				.add("codesign", "-s", identityApplication())
				.add("--options", "runtime")
				.add("-f", "--verbose=4")
				.add("--keychain", keyChain().toString());

			if (!timeStampAuthority().trim().isEmpty()) {
				codeSignCommandPrefix.add("--timestamp=\"" + timeStampAuthority().trim() + "\"");
			} else {
				codeSignCommandPrefix.add("--timestamp");
			}
			codeSignCommandPrefix(codeSignCommandPrefix.build());

			ImmutableList.Builder<String> productSignCommandPrefix = ImmutableList.builder();
			productSignCommandPrefix
				.add("productsign", "--sign", identityInstaller())
				.add("--keychain", keyChain().toString());

			if (!timeStampAuthority().trim().isEmpty()) {
				productSignCommandPrefix.add("--timestamp=\"" + timeStampAuthority().trim() + "\"");
			} else {
				productSignCommandPrefix.add("--timestamp");
			}
			productSignCommandPrefix(productSignCommandPrefix.build());

			securityUnlockCommand(ImmutableList.of("security", "unlock", "-p", keyChainPassword(), keyChain().toString()));

			AppleCodeSigner codeSigner = autoBuild();

			checkState(codeSigner.codeSignTimeout() > 0, "Codesign timeout must be strictly positive");
			checkState(codeSigner.securityUnlockTimeout() > 0, "Security unlock timeout must be strictly positive");
			checkState(Files.exists(codeSigner.tempFolder()), "Temporary folder must exist");
			checkState(Files.isDirectory(codeSigner.tempFolder()), "Temporary folder must be a directory");

			return codeSigner;
		}
	}
}
