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
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static java.util.Objects.requireNonNull;
import static org.eclipse.cbi.webservice.util.function.UnsafePredicate.safePredicate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.eclipse.cbi.webservice.util.function.WrappedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class Codesigner {

	private final static Logger logger = LoggerFactory.getLogger(Codesigner.class);

	private static final String TEMP_FILE_PREFIX = Codesigner.class.getSimpleName() + "-";

	private static final String DOT_APP_GLOB_PATTERN = "glob:**.app";

	Codesigner() {}

	public long signZippedApplications(Path source, Path target) throws IOException {
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
				return signAndRezip(unzipDirectory, target);
			} else {
				throw new IOException("The provided Zip file is invalid");
			}
		} finally {
			// clean up temp folder if used
			cleanTemporaryResource(unzipDirectory);
		}
	}

	/**
	 * Use the {@code codesign} command line utility to sign all .app in the
	 * {@code tempDirectory}.
	 *
	 * @return true if all found apps has been signed properly, false if no apps
	 *         is signed or an error occured with at least one.
	 * @throws IOException  if an I/O error occurs when signing
	 */
	public long signApplications(Path directory) throws IOException {
		requireNonNull(directory);
		checkArgument(Files.isDirectory(directory), "Path must reference an existing directory");

		unlockKeychain();

		try (Stream<Path> pathStream = Files.list(directory)) {
			final PathMatcher dotAppPattern = directory.getFileSystem().getPathMatcher(DOT_APP_GLOB_PATTERN);

			try {
				return pathStream
					.filter(p -> Files.isDirectory(p) && dotAppPattern.matches(p))
					.filter(safePredicate(p -> signApplication(p, false)))
					.count();
			} catch (WrappedException e) {
				throwIfInstanceOf(e.getCause(), IOException.class);
				throwIfUnchecked(e.getCause());
				throw new RuntimeException(e.getCause());
			}
		}
	}

	public boolean signApplication(Path app) throws IOException {
		return signApplication(app, true);
	}

	private long signAndRezip(Path unzipDirectory, Path signedFile) throws IOException {
		final long nbSignedApps = signApplications(unzipDirectory);
		if (nbSignedApps > 0) {
			if (Zips.packZip(unzipDirectory, signedFile, false) <= 0) {
				throw new IOException("The signing was succesfull, but something wrong happened when trying to zip it back");
			}
		}
		return nbSignedApps;
	}

	private boolean signApplication(Path app, boolean needUnlock) throws IOException {
		requireNonNull(app);
		checkArgument(app.getFileSystem().getPathMatcher(DOT_APP_GLOB_PATTERN).matches(app), "Path must ends with '.app");
		checkArgument(Files.isDirectory(app), "Path must reference an existing directory");

		if (needUnlock) {
			unlockKeychain();
		}

		final StringBuilder output = new StringBuilder();
		final int codesignExitValue = processExecutor().exec(codesignCommand(app), output, codesignTimeout(), TimeUnit.SECONDS);
		if (codesignExitValue == 0) {
			return true;
		} else {
			throw new IOException(Joiner.on('\n').join(
					"The 'codesign' command on '" + app.getFileName() + "' exited with value '" + codesignExitValue + "'",
					"'codesign' command output:",
					output));
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

	private ImmutableList<String> codesignCommand(Path path) {
		return ImmutableList.<String>builder().addAll(codesignCommandPrefix()).add(path.toString()).build();
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
	abstract String certificateName();

	abstract ProcessExecutor processExecutor();

	abstract long codesignTimeout();
	
	abstract String timeStampAuthority();

	abstract long securityUnlockTimeout();

	abstract ImmutableList<String> codesignCommandPrefix();

	abstract ImmutableList<String> securityUnlockCommand();

	public static Builder builder() {
		return new AutoValue_Codesigner.Builder()
			.securityUnlockTimeout(20)
			.codesignTimeout(TimeUnit.MINUTES.toSeconds(10));
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
		 * @param certificateName
		 *            the name of the certificate to be used for signing.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder certificateName(String certificateName);
		abstract String certificateName();

		public abstract Builder processExecutor(ProcessExecutor executor);

		public abstract Builder codesignTimeout(long codesignTimeout);
		
		public abstract Builder timeStampAuthority(String timeStampAuthority);
		abstract String timeStampAuthority();

		public abstract Builder securityUnlockTimeout(long securityUnlockTimeout);

		abstract Builder codesignCommandPrefix(ImmutableList<String> commandPrefix);

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

			Codesigner codesigner = autoBuild();
			checkState(codesigner.codesignTimeout() > 0, "Codesign timeout must be strictly positive");
			checkState(codesigner.securityUnlockTimeout() > 0, "Security unlock timeout must be strictly positive");
			checkState(Files.exists(codesigner.tempFolder()), "Temporary folder must exists");
			checkState(Files.exists(codesigner.tempFolder()), "Temporary folder must exists");
			checkState(Files.isDirectory(codesigner.tempFolder()), "Temporary folder must be a directory");
			return codesigner;
		}
	}
}
