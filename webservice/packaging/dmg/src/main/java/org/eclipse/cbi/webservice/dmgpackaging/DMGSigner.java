/*******************************************************************************
 * Copyright (c) 2017 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.dmgpackaging;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.ProcessExecutor;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class DMGSigner {

	private static final String DOT_DMG_GLOB_PATTERN = "glob:**.dmg";
	
	DMGSigner() {}
	
	/**
	 * Returns the keychain password for the {@link #keychain()}.
	 *
	 * @return the keychain password for the {@link #keychain()}.
	 */
	abstract String keychainPassword();

	/**
	 * Returns the path to the keychain to be unlocked.
	 *
	 * @return the path to the keychain to be unlocked.
	 */
	abstract Path keychain();

	/**
	 * Returns the name of the certificate to use to sign OS X applications.
	 *
	 * @return the name of the certificate to use to sign OS X applications.
	 */
	abstract String certificateName();
	
	abstract String timeStampAuthority();

	abstract ProcessExecutor processExecutor();

	abstract long codesignTimeout();

	abstract long securityUnlockTimeout();

	abstract ImmutableList<String> codesignCommandPrefix();

	abstract ImmutableList<String> securityUnlockCommand();

	public static Builder builder() {
		return new AutoValue_DMGSigner.Builder()
			.securityUnlockTimeout(20)
			.codesignTimeout(TimeUnit.MINUTES.toSeconds(10));
	}

	public boolean sign(Path dmg) throws IOException {
		requireNonNull(dmg);
		checkArgument(dmg.getFileSystem().getPathMatcher(DOT_DMG_GLOB_PATTERN).matches(dmg), "Path must ends with '.dmg");

		unlockKeychain();

		final StringBuilder output = new StringBuilder();
		final int codesignExitValue = processExecutor().exec(codesignCommand(dmg), output, codesignTimeout(), TimeUnit.SECONDS);
		if (codesignExitValue == 0) {
			return true;
		} else {
			throw new IOException(Joiner.on('\n').join(
					"The 'codesign' command on '" + dmg.getFileName() + "' exited with value '" + codesignExitValue + "'",
					"'codesign' command output:",
					output));
		}
	}
	
	private ImmutableList<String> codesignCommand(Path path) {
		return ImmutableList.<String>builder().addAll(codesignCommandPrefix()).add(path.toString()).build();
	}
	
	private void unlockKeychain() throws IOException {
		final StringBuilder output = new StringBuilder();
		final int securityExitValue = processExecutor().exec(securityUnlockCommand(), output , securityUnlockTimeout(), TimeUnit.SECONDS);
		if (securityExitValue != 0) {
			throw new IOException(Joiner.on('\n').join(
					"The 'security unlock' command exited with value '" + securityExitValue + "'",
					"'security unlock' output:",
					output));
		}
	}
	
	@AutoValue.Builder
	public abstract static class Builder {
		Builder() {}
		
		/**
		 * Sets the password of the {@link #keychain(Path) specified keychain}.
		 *
		 * @param keychainPassword
		 *            the password
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder keychainPassword(String keychainPassword);
		abstract String keychainPassword();

		/**
		 * Sets keychain to be unlocked.
		 *
		 * @param keychain
		 *            the keychain to be unlocked
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder keychain(Path keychain);
		abstract Path keychain();

		/**
		 * Sets the name of the certificate to be used for signing.
		 *
		 * @param certificateName
		 *            the name of the certificate to be used for signing.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder certificateName(String certificateName);
		abstract String certificateName();
		
		public abstract Builder timeStampAuthority(String timeStampAuthority);
		abstract String timeStampAuthority();

		public abstract Builder processExecutor(ProcessExecutor executor);

		public abstract Builder codesignTimeout(long codesignTimeout);

		public abstract Builder securityUnlockTimeout(long securityUnlockTimeout);

		abstract Builder codesignCommandPrefix(ImmutableList<String> commandPrefix);

		abstract Builder securityUnlockCommand(ImmutableList<String> command);

		abstract DMGSigner autoBuild();

		/**
		 * Creates and returns a new instance of {@link DMGSigner} as
		 * configured by this builder. The following checks are made:
		 * <ul>
		 * <li>The temporary folder must exists.</li>
		 * <li>The keychain file must exists.</li>
		 * <li>The certificate name must not be empty.</li>
		 * </ul>
		 *
		 * @return a new instance of {@link DMGSigner} as configured by this
		 *         builder.
		 */
		public DMGSigner build() {
			checkState(!certificateName().isEmpty(), "Certificate name must not be empty");
			checkState(Files.exists(keychain()) && Files.isRegularFile(keychain()), "Keychain file must exists");
			ImmutableList.Builder<String> commandPrefix = ImmutableList.builder();
			commandPrefix.add("codesign", "-s", certificateName(), "-f", "--verbose=4",  "--keychain", keychain().toString());
			if (!timeStampAuthority().trim().isEmpty()) {
				commandPrefix.add("--timestamp=\""+timeStampAuthority().trim()+"\"");
			} else {
				commandPrefix.add("--timestamp");
			}
			codesignCommandPrefix(commandPrefix.build());
			securityUnlockCommand(ImmutableList.of("security", "unlock", "-p", keychainPassword(), keychain().toString()));

			DMGSigner dmgSigner = autoBuild();
			checkState(dmgSigner.codesignTimeout() > 0, "Codesign timeout must be strictly positive");
			checkState(dmgSigner.securityUnlockTimeout() > 0, "Security unlock timeout must be strictly positive");
			return dmgSigner;
		}
	}
}
