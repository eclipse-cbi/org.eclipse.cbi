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
 *   Thomas Neidhart - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.macosx;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.eclipse.cbi.webservice.util.function.WrappedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.util.Objects.requireNonNull;
import static org.eclipse.cbi.webservice.util.function.UnsafePredicate.safePredicate;

/**
 * Interface for different tools to implement code signing.
 */
public abstract class CodeSigner {
	private final String TEMP_FILE_PREFIX = CodeSigner.class.getSimpleName() + "-";
	private static final String DOT_PKG_GLOB_PATTERN = "glob:**.{pkg,mpkg}";

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	abstract Path tempFolder();
	abstract ProcessExecutor processExecutor();
	abstract long codeSignTimeout();

	public long signFile(Path source, Options options) throws IOException {
		requireNonNull(source);
		if (doSign(source, options, true)) {
			return 1;
		}
		return 0;
	}

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
				throw new IOException("The provided zip file is invalid");
			}
		} finally {
			// clean up temp folder if used
			cleanTemporaryResource(unzipDirectory);
		}
	}

	protected abstract void unlockKeychain() throws IOException;

	protected abstract ImmutableList<String> signApplicationCommand(Path path, Options options);
	protected abstract ImmutableList<String> signInstallerCommand(Path input, Path output);

	private boolean signWithApplicationCertificate(Path file, Options options) throws IOException {
		requireNonNull(file);

		final StringBuilder output = new StringBuilder();
		final int codesignExitValue =
				processExecutor().exec(signApplicationCommand(file, options), output, codeSignTimeout(), TimeUnit.SECONDS);
		if (codesignExitValue == 0) {
			return true;
		} else {
			throw new IOException(Joiner.on('\n').join(
					"The 'codesign' command on '" + file.getFileName() + "' exited with value '" + codesignExitValue + "'",
					"'codesign' command output:",
					output));
		}
	}

	private boolean signWithInstallerCertificate(Path file) throws IOException {
		requireNonNull(file);
		checkArgument(file.getFileSystem().getPathMatcher(DOT_PKG_GLOB_PATTERN).matches(file), "Path must ends with '.pkg' or '.mpkg'");
		checkArgument(Files.isRegularFile(file), "Path must reference an existing regular file");

		final StringBuilder output = new StringBuilder();
		Path signedProduct =
				Files.createTempFile(file.getParent(),
					com.google.common.io.Files.getNameWithoutExtension(file.getFileName().toString()),
					com.google.common.io.Files.getFileExtension(file.getFileName().toString()));
		try {
			final int productsignExitValue =
				processExecutor().exec(signInstallerCommand(file, signedProduct), output, codeSignTimeout(), TimeUnit.SECONDS);
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

	private long signAndRezip(Path unzipDirectory, Path signedFile, Options options) throws IOException {
		final long nbSignedApps = signAll(unzipDirectory, options);
		if (nbSignedApps > 0) {
			if (Zips.packZip(unzipDirectory, signedFile, false) <= 0) {
				throw new IOException("The signing was successful, but something wrong happened when trying to zip it back");
			}
		}
		return nbSignedApps;
	}

	/**
	 * Use the {@code codesign} command line utility to sign all .app in the
	 * {@code tempDirectory}.
	 *
	 * @return the number of signed executables.
	 * @throws IOException  if an I/O error occurs when signing
	 */
	private long signAll(Path directory, Options options) throws IOException {
		requireNonNull(directory);
		checkArgument(Files.isDirectory(directory), "Path must reference an existing directory");

		unlockKeychain();

		try (Stream<Path> pathStream = Files.list(directory)) {
			try {
				return pathStream
						.filter(safePredicate(p -> doSign(p, options, false)))
						.count();
			} catch (WrappedException e) {
				throwIfInstanceOf(e.getCause(), IOException.class);
				throwIfUnchecked(e.getCause());
				throw new RuntimeException(e.getCause());
			}
		}
	}

	private boolean doSign(Path file, Options options, boolean needUnlock) throws IOException {
		if (needUnlock) {
			unlockKeychain();
		}

		final FileSystem fs = file.getFileSystem();
		if (Files.isDirectory(file)) {
			return signWithApplicationCertificate(file, options);
		} else if (Files.isRegularFile(file)) {
			if (fs.getPathMatcher(DOT_PKG_GLOB_PATTERN).matches(file)) {
				return signWithInstallerCertificate(file);
			} else {
				return signWithApplicationCertificate(file, options);
			}
		}

		return false;
	}

	private void cleanTemporaryResource(Path tempResource) {
		if (tempResource != null && Files.exists(tempResource)) {
			try {
				Paths.delete(tempResource);
			} catch (IOException e) {
				logger.error("Error occurred while deleting temporary resource '{}'", tempResource, e);
			}
		}
	}

	@AutoValue
	public static abstract class Options {

		abstract boolean deep();
		abstract boolean force();
		abstract Optional<Path> entitlements();

		public static Options.Builder builder() {
			return new AutoValue_CodeSigner_Options.Builder()
				.deep(false)
				.force(true);
		}

		@AutoValue.Builder
		public static abstract class Builder {
			public abstract Options.Builder deep(boolean deep);
			public abstract Options.Builder force(boolean force);
			public abstract Options.Builder entitlements(Path entitlements);
			public abstract Options build();
		}
	}
}

