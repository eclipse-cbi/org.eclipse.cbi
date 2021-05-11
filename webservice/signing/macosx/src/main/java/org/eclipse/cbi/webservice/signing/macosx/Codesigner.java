/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.macosx;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;
import static org.eclipse.cbi.webservice.util.function.UnsafePredicate.safePredicate;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.eclipse.cbi.webservice.util.function.WrappedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoValue
public abstract class Codesigner {

	private final static Logger logger = LoggerFactory.getLogger(Codesigner.class);

	private static final String TEMP_FILE_PREFIX = Codesigner.class.getSimpleName() + "-";

	private static final String SIGNABLE_GLOB_PATTERN = "glob:**.{app,plugin,framework,dylib}";

	private static final String DOT_PKG_GLOB_PATTERN = "glob:**.{pkg,mpkg}";

	Codesigner() {}

	public long signZippedApplications(Path source, Path target, Options options) throws IOException {
		requireNonNull(source);
		requireNonNull(target);
		checkArgument(Files.isRegularFile(source), "Source zip must be an existing regular file");
		checkArgument(source.getFileName().toString().endsWith(".zip"), "Source path must end with zip extension");
		checkArgument(target.getFileName().toString().endsWith(".zip"), "Target path must end with zip extension");

		Path unzipDirectory = null;
		try {
			unzipDirectory = Files.createTempDirectory(tempFolder(), TEMP_FILE_PREFIX);
			// unzip the part in temp folder.
			if (Zips.unpackZip(source, unzipDirectory) > 0) {
				return signAndRezip(unzipDirectory, target, options);
			} else {
				throw new IOException("The provided Zip file is invalid");
			}
		} finally {
			// clean up temp folder if used
			cleanTemporaryResource(unzipDirectory);
		}
	}

	private long signAndRezip(Path unzipDirectory, Path signedFile, Options options) throws IOException {
		final long nbSignedApps = signAll(unzipDirectory, options);
		if (nbSignedApps > 0) {
			if (Zips.packZip(unzipDirectory, signedFile, false) <= 0) {
				throw new IOException("The signing was succesfull, but something wrong happened when trying to zip it back");
			}
		}
		return nbSignedApps;
	}

	public long signFile(Path source, Options options) throws IOException {
		requireNonNull(source);
		if (doSign(source, options, true)) {
			return 1;
		}
		return 0;
	}

	/**
	 * Use the {@code codesign} command line utility to sign all .app in the
	 * {@code tempDirectory}.
	 *
	 * @return true if all found apps has been signed properly, false if no apps
	 *         is signed or an error occured with at least one.
	 * @throws IOException  if an I/O error occurs when signing
	 */
	private long signAll(Path directory, Options options) throws IOException {
		requireNonNull(directory);
		checkArgument(Files.isDirectory(directory), "Path must reference an existing directory");

		unlockKeychain();

		try (Stream<Path> pathStream = Files.list(directory)) {
			try {
				return pathStream
					.filter(Codesigner::signable)
					.filter(safePredicate(p -> doSign(p, options, false)))
					.count();
			} catch (WrappedException e) {
				throwIfInstanceOf(e.getCause(), IOException.class);
				throwIfUnchecked(e.getCause());
				throw new RuntimeException(e.getCause());
			}
		}
	}

	private static boolean signable(Path path) {
		final FileSystem fs = path.getFileSystem();
		return fs.getPathMatcher(SIGNABLE_GLOB_PATTERN).matches(path) || fs.getPathMatcher(DOT_PKG_GLOB_PATTERN).matches(path);
	}

	private boolean doSign(Path file, Options options, boolean needUnlock) throws IOException {
		if (needUnlock) {
			unlockKeychain();
		}

		final FileSystem fs = file.getFileSystem();
		if (Files.isDirectory(file)) {
			if (fs.getPathMatcher(SIGNABLE_GLOB_PATTERN).matches(file)) {
				return codesign(file, options);
			} else {
				logger.warn("Folder '"+file+"' does not match pattern "+SIGNABLE_GLOB_PATTERN+" so it won't be signed");
			}
		} else if (Files.isRegularFile(file)) {
			if (fs.getPathMatcher(DOT_PKG_GLOB_PATTERN).matches(file)) {
				return productsign(file);
			} else {
				return codesign(file, options);
			}
		}

		return false;
	}

	private boolean codesign(Path file, Options options) throws IOException {
		requireNonNull(file);

		final StringBuilder output = new StringBuilder();
		final int codesignExitValue = processExecutor().exec(codesignCommand(file, options), output, codesignTimeout(), TimeUnit.SECONDS);
		if (codesignExitValue == 0) {
			return true;
		} else {
			throw new IOException(Joiner.on('\n').join(
					"The 'codesign' command on '" + file.getFileName() + "' exited with value '" + codesignExitValue + "'",
					"'codesign' command output:",
					output));
		}
	}

	private boolean productsign(Path file) throws IOException {
		requireNonNull(file);
		checkArgument(file.getFileSystem().getPathMatcher(DOT_PKG_GLOB_PATTERN).matches(file), "Path must ends with '.pkg' or '.mpkg'");
		checkArgument(Files.isRegularFile(file), "Path must reference an existing regular file");

		final StringBuilder output = new StringBuilder();
		Path signedProduct = Files.createTempFile(file.getParent(), com.google.common.io.Files.getNameWithoutExtension(file.getFileName().toString()), com.google.common.io.Files.getFileExtension(file.getFileName().toString()));
		try {
			final int productsignExitValue = processExecutor().exec(productsignCommand(file, signedProduct), output, productsignTimeout(), TimeUnit.SECONDS);
			if (productsignExitValue == 0) {
				Files.move(signedProduct, file, StandardCopyOption.REPLACE_EXISTING);
				return true;
			} else {
				throw new IOException(Joiner.on('\n').join(
						"The 'productsign' command on '" + file.getFileName() + "' exited with value '" + productsignExitValue + "'",
						"'productsign' command output:",
						output));
			}
		} finally {
			cleanTemporaryResource(signedProduct);
		}
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

	private ImmutableList<String> codesignCommand(Path path, Options options) {
		return ImmutableList.<String>builder()
			.addAll(codesignCommandPrefix())
			.addAll(options.toArgsList())
			.add(path.toString())
			.build();
	}

	private ImmutableList<String> productsignCommand(Path input, Path output) {
		ImmutableList.Builder<String> command = ImmutableList.<String>builder().addAll(productsignCommandPrefix());
		return command
			.add(input.toString())
			.add(output.toString())
			.build();
	}

	static void cleanTemporaryResource(Path tempResource) {
		if (tempResource != null && Files.exists(tempResource)) {
			try {
				Paths.delete(tempResource);
			} catch (IOException e) {
				logger.error("Error occured while deleting temporary resource '"+tempResource.toString()+"'", e);
			}
		}
	}

	/**
	 * Returns the temporary folder to use during intermediate step of
	 * application signing.
	 *
	 * @return the temporary folder to use during intermediate step of
	 *         application signing.
	 */
	abstract Path tempFolder();

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
	abstract String identityApplication();

	abstract String identityInstaller();

	abstract ProcessExecutor processExecutor();

	abstract long codesignTimeout();

	abstract long productsignTimeout();
	
	abstract String timeStampAuthority();

	abstract long securityUnlockTimeout();

	abstract ImmutableList<String> codesignCommandPrefix();

	abstract ImmutableList<String> productsignCommandPrefix();

	abstract ImmutableList<String> securityUnlockCommand();

	public static Builder builder() {
		return new AutoValue_Codesigner.Builder()
			.securityUnlockTimeout(20)
			.codesignTimeout(TimeUnit.MINUTES.toSeconds(10))
			.productsignTimeout(TimeUnit.MINUTES.toSeconds(10));
	}

	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}

		/**
		 * Sets the temporary folder for intermediate step during signing.
		 *
		 * @param tempFolder
		 *            the temporary folder for intermediate step during signing.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder tempFolder(Path tempFolder);

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
		 * @param identityApplication
		 *            the name of the certificate to be used for signing.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder identityApplication(String identityApplication);
		abstract String identityApplication();

		public abstract Builder identityInstaller(String identityInstaller);
		abstract String identityInstaller();

		public abstract Builder processExecutor(ProcessExecutor executor);

		public abstract Builder codesignTimeout(long codesignTimeout);

		public abstract Builder productsignTimeout(long productsignTimeout);
		
		public abstract Builder timeStampAuthority(String timeStampAuthority);
		abstract String timeStampAuthority();

		public abstract Builder securityUnlockTimeout(long securityUnlockTimeout);

		abstract Builder codesignCommandPrefix(ImmutableList<String> commandPrefix);

		abstract Builder productsignCommandPrefix(ImmutableList<String> commandPrefix);

		abstract Builder securityUnlockCommand(ImmutableList<String> command);

		abstract Codesigner autoBuild();

		/**
		 * Creates and returns a new instance of {@link Codesigner} as
		 * configured by this builder. The following checks are made:
		 * <ul>
		 * <li>The temporary folder must exists.</li>
		 * <li>The keychain file must exists.</li>
		 * <li>The certificate name must not be empty.</li>
		 * </ul>
		 *
		 * @return a new instance of {@link Codesigner} as configured by this
		 *         builder.
		 */
		public Codesigner build() {
			checkState(!identityApplication().isEmpty(), "Certificate name must not be empty");
			checkState(Files.exists(keychain()) && Files.isRegularFile(keychain()), "Keychain file must exists");
			ImmutableList.Builder<String> codesignCommandPrefix = ImmutableList.builder();
			codesignCommandPrefix.add("codesign", "-s", identityApplication(), "--options", "runtime", "-f", "--verbose=4",  "--keychain", keychain().toString());
			if (!timeStampAuthority().trim().isEmpty()) {
				codesignCommandPrefix.add("--timestamp=\""+timeStampAuthority().trim()+"\"");
			} else {
				codesignCommandPrefix.add("--timestamp");
			}
			codesignCommandPrefix(codesignCommandPrefix.build());

			ImmutableList.Builder<String> productsignCommandPrefix = ImmutableList.builder();
			productsignCommandPrefix.add("productsign", "--sign", identityInstaller(), "--keychain", keychain().toString());
			if (!timeStampAuthority().trim().isEmpty()) {
				productsignCommandPrefix.add("--timestamp=\""+timeStampAuthority().trim()+"\"");
			} else {
				productsignCommandPrefix.add("--timestamp");
			}
			productsignCommandPrefix(productsignCommandPrefix.build());

			securityUnlockCommand(ImmutableList.of("security", "unlock", "-p", keychainPassword(), keychain().toString()));

			Codesigner codesigner = autoBuild();
			checkState(codesigner.codesignTimeout() > 0, "Codesign timeout must be strictly positive");
			checkState(codesigner.securityUnlockTimeout() > 0, "Security unlock timeout must be strictly positive");
			checkState(Files.exists(codesigner.tempFolder()), "Temporary folder must exists");
			checkState(Files.exists(codesigner.tempFolder()), "Temporary folder must exists");
			checkState(Files.isDirectory(codesigner.tempFolder()), "Temporary folder must be a directory");
			return codesigner;
		}
	}

	@AutoValue
	public static abstract class Options {

		public abstract boolean deep();

		public abstract boolean force();

		public abstract Optional<Path> entitlements();

		public static Options.Builder builder() {
			return new AutoValue_Codesigner_Options.Builder()
				.deep(true)
				.force(true);
		}

		public List<String> toArgsList() {
			ImmutableList.Builder<String> ret = ImmutableList.builder();
			if (deep()) {
				ret.add("--deep");
			}
			if (force()) {
				ret.add("--force");
			}
			if (entitlements().isPresent()) {
				ret.add("--entitlements", entitlements().get().toString());
			}
			return ret.build();
		}

		@AutoValue.Builder
		public static abstract class Builder {
			public abstract Options.Builder deep(boolean deep);
			public abstract Options.Builder force(boolean force);
			public abstract Options.Builder entitlements(Path entitlements);
			public abstract Options.Builder entitlements(Optional<Path> entitlements);
			public abstract Options build();
		}
	}
}
