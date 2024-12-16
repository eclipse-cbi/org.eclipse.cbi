/*******************************************************************************
 * Copyright (c) 2024 Eclipse Foundation and others
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import org.eclipse.cbi.webservice.util.ProcessExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

@AutoValue
public abstract class RCodeSigner extends CodeSigner {

	public abstract Path tempFolder();
	public abstract ProcessExecutor processExecutor();
	public abstract long codeSignTimeout();

	abstract Path rCodeSign();

	abstract Path identityApplicationKeyChain();
	abstract Path identityApplicationKeyChainPasswordFile();
	abstract Path identityInstallerKeyChain();
	abstract Path identityInstallerKeyChainPasswordFile();

	abstract String timeStampAuthority();

	abstract ImmutableList<String> signWithApplicationCertificateCommandPrefix();
	abstract ImmutableList<String> signWithInstallerCertificateCommandPrefix();

	@Override
	protected void unlockKeychain() {}

	@Override
	protected ImmutableList<String> signApplicationCommand(Path path, Options options) {
		return ImmutableList.<String>builder()
			.addAll(signWithApplicationCertificateCommandPrefix())
			.addAll(toArgsList(options))
			.add(path.toString())
			.build();
	}

	private List<String> toArgsList(Options options) {
		ImmutableList.Builder<String> ret = ImmutableList.builder();
		if (!options.deep()) {
			ret.add("--shallow");
		}
		if (options.entitlements().isPresent()) {
			ret.add("--entitlements-xml-file", options.entitlements().get().toString());
		}
		return ret.build();
	}

	@Override
	protected ImmutableList<String> signInstallerCommand(Path input, Path output) {
		ImmutableList.Builder<String> command = ImmutableList.<String>builder().addAll(signWithInstallerCertificateCommandPrefix());
		return command
			.add(input.toString())
			.add(output.toString())
			.build();
	}

	public static Builder builder() {
		return new AutoValue_RCodeSigner.Builder();
	}

	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}

		public abstract Builder tempFolder(Path tempFolder);
		public abstract Builder processExecutor(ProcessExecutor executor);

		public abstract Builder rCodeSign(Path rCodeSign);
		public abstract Path rCodeSign();
		public abstract Builder identityApplicationKeyChain(Path keyChainPath);
		abstract Path identityApplicationKeyChain();
		public abstract Builder identityApplicationKeyChainPasswordFile(Path passwordFile);
		abstract Path identityApplicationKeyChainPasswordFile();
		public abstract Builder identityInstallerKeyChain(Path keyChainPath);
		abstract Path identityInstallerKeyChain();
		public abstract Builder identityInstallerKeyChainPasswordFile(Path passwordFile);
		abstract Path identityInstallerKeyChainPasswordFile();

		public abstract Builder codeSignTimeout(long codeSignTimeout);

		public abstract Builder timeStampAuthority(String timeStampAuthority);
		abstract String timeStampAuthority();

		abstract Builder signWithApplicationCertificateCommandPrefix(ImmutableList<String> commandPrefix);
		abstract Builder signWithInstallerCertificateCommandPrefix(ImmutableList<String> commandPrefix);

		abstract RCodeSigner autoBuild();

		/**
		 * Creates and returns a new instance of {@link RCodeSigner} as
		 * configured by this builder. The following checks are made:
		 * <ul>
		 * <li>The temporary folder must exist.</li>
		 * <li>The keychain files must exist.</li>
		 * <li>The keychain password files must exist.</li>
		 * </ul>
		 *
		 * @return a new instance of {@link RCodeSigner} as configured by this builder.
		 */
		public RCodeSigner build() {
			checkState(Files.exists(identityApplicationKeyChain()) && Files.isRegularFile(identityApplicationKeyChain()), "Application Identity Keychain file must exists");
			checkState(Files.exists(identityInstallerKeyChain()) && Files.isRegularFile(identityInstallerKeyChain()), "Installer Identity Keychain file must exists");

			ImmutableList.Builder<String> signApplicationCommandPrefix = ImmutableList.builder();
			signApplicationCommandPrefix
					.add(rCodeSign().toString(), "sign")
					.add("--p12-file", identityApplicationKeyChain().toString())
					.add("--p12-password-file", identityApplicationKeyChainPasswordFile().toString())
					.add("--code-signature-flags", "runtime")
			        // needed because of https://github.com/indygreg/apple-platform-rs/issues/170
					.add("--exclude", "*.class")
					.add("--for-notarization");

			if (!timeStampAuthority().trim().isEmpty()) {
				signApplicationCommandPrefix.add("--timestamp-url", timeStampAuthority().trim());
			}
			signWithApplicationCertificateCommandPrefix(signApplicationCommandPrefix.build());

			ImmutableList.Builder<String> signInstallerCommandPrefix = ImmutableList.builder();
			signInstallerCommandPrefix
					.add(rCodeSign().toString(), "sign")
					.add("--p12-file", identityInstallerKeyChain().toString())
					.add("--p12-password-file", identityInstallerKeyChainPasswordFile().toString())
					// needed because of https://github.com/indygreg/apple-platform-rs/issues/170
					.add("--exclude", "*.class");
			
			if (!timeStampAuthority().trim().isEmpty()) {
				signInstallerCommandPrefix.add("--timestamp-url", timeStampAuthority().trim());
			}
			signWithInstallerCertificateCommandPrefix(signInstallerCommandPrefix.build());

			RCodeSigner codeSigner = autoBuild();

			checkState(codeSigner.codeSignTimeout() > 0, "Codesign timeout must be strictly positive");
			checkState(Files.exists(codeSigner.tempFolder()), "Temporary folder must exist");
			checkState(Files.isDirectory(codeSigner.tempFolder()), "Temporary folder must be a directory");

			return codeSigner;
		}
	}
}
