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
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.StreamSupport;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

public abstract class JarResigner implements JarSigner {

	private static final String DIGEST_ATTRIBUTE_SUFFIX = "-Digest";

	public enum Strategy {
		DO_NOT_RESIGN,
		THROW_EXCEPTION,
		RESIGN,
		RESIGN_WITH_SAME_DIGEST_ALGORITHM,
		OVERWRITE,
		OVERWRITE_WITH_SAME_DIGEST_ALGORITHM,
	}

	private final JarSigner delegate;
	private final Log log;

	JarResigner(JarSigner delegate, Log log) {
		this.delegate = Preconditions.checkNotNull(delegate);
		this.log = Preconditions.checkNotNull(log);
	}

	final Log log() {
		return log;
	}

	public static JarSigner create(Strategy strategy, JarSigner delegate, Log log) {
		final JarSigner ret;
		switch (strategy) {
			case DO_NOT_RESIGN:
				ret = new DoNotResign(delegate, log);
				break;
			case THROW_EXCEPTION:
				ret = new ThrowException(delegate, log);
				break;
			case RESIGN:
				ret = new Resign(delegate, log);
				break;
			case RESIGN_WITH_SAME_DIGEST_ALGORITHM:
				ret = new ResignWithSameDigestAlg(delegate, log);
				break;
			case OVERWRITE:
				ret = new OverwriteSignature(delegate, log);
				break;
			case OVERWRITE_WITH_SAME_DIGEST_ALGORITHM:
				ret = new OverwriteSignatureWithSameDigestAlg(delegate, log);
				break;
			default:
				throw new IllegalStateException("Unknow resigning strategy: " + strategy);
		}
		return ret;
	}

	JarSigner delegate() {
		return delegate;
	}

	@Override
	public int sign(Path jar, Options options) throws IOException {
		final int ret;
		if (isAlreadySigned(jar)) {
			ret = resign(jar, options);
		} else {
			ret = delegate().sign(jar, options);
		}
		return ret;
	}

	abstract int resign(Path jar, Options options) throws IOException;

	@VisibleForTesting static boolean isAlreadySigned(Path jar) throws IOException {
		boolean alreadySigned = false;
		try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
			JarEntry nextJarEntry = jis.getNextJarEntry();
			while (nextJarEntry != null && !alreadySigned) {
				alreadySigned = isBlockOrSF(nextJarEntry.getName()) || hasManifestDigest(nextJarEntry.getAttributes());
				nextJarEntry = jis.getNextJarEntry();
			}
		}
		return alreadySigned;
	}

	static boolean hasManifestDigest(Attributes entryAttributes) {
		if (entryAttributes != null) {
			return entryAttributes.keySet().stream().anyMatch(k -> k.toString().endsWith(DIGEST_ATTRIBUTE_SUFFIX));
		}
		return false;
	}

	private static boolean isBlockOrSF(String entryName) {
		String uname = entryName.toUpperCase(Locale.ENGLISH);
		if ((uname.startsWith("META-INF/") || uname.startsWith("/META-INF/"))) {
			return uname.endsWith(".SF") || uname.endsWith(".DSA") || uname.endsWith(".RSA") || uname.endsWith(".EC");
		}
		return false;
	}

	public static JarSigner doNotResign(JarSigner jarSigner, Log log) {
		return new DoNotResign(jarSigner, log);
	}

	public static JarSigner throwException(JarSigner jarSigner, Log log) {
		return new ThrowException(jarSigner, log);
	}

	public static JarSigner resignWithSameDigestAlgorithm(JarSigner jarSigner, Log log) {
		return new ResignWithSameDigestAlg(jarSigner, log);
	}

	public static JarSigner resign(JarSigner jarSigner, Log log) {
		return new Resign(jarSigner, log);
	}

	public static JarSigner overwriteWithSameDigestAlgorithm(JarSigner jarSigner, Log log) {
		return new OverwriteSignatureWithSameDigestAlg(jarSigner, log);
	}

	public static JarSigner overwrite(JarSigner jarSigner, Log log) {
		return new OverwriteSignature(jarSigner, log);
	}

	private static class DoNotResign extends JarResigner {

		DoNotResign(JarSigner delegate, Log log) {
			super(delegate, log);
		}

		@Override
		public int resign(Path jar, Options options) {
			log().info("Jar '" + jar.toString() + "' is already signed and will *not* be resigned.");
			return 0;
		}
	}

	private static class ThrowException extends JarResigner {

		ThrowException(JarSigner delegate, Log log) {
			super(delegate, log);
		}

		@Override
		public int resign(Path jar, Options options) {
			throw new IllegalStateException("Jar '" + jar  + "' is already signed");
		}
	}

	private static class Resign extends JarResigner {

		Resign(JarSigner delegate, Log log) {
			super(delegate, log);
		}

		@Override
		protected int resign(Path jar, Options options) throws IOException {
			log().info("Jar '" + jar.toString() + "' is already signed and will be resigned.");
			return delegate().sign(jar, options);
		}
	}

	private static class OverwriteSignature extends JarResigner {


		OverwriteSignature(JarSigner delegate, Log log) {
			super(delegate, log);
		}

		@Override
		protected int resign(Path jar, Options options) throws IOException {
			Path unpackedJar = Files.createTempDirectory(Paths.getParent(jar), "overwriteSignature-" + jar.getFileName() + "-");
			try {
				Zips.unpackJar(jar, unpackedJar);
				Path metaInf = unpackedJar.resolve("META-INF");
				boolean signatureFilesFound = removeSignatureFilesIfAny(metaInf);
				boolean manifestDigestsFound = removeManifestDigestsIfAny(metaInf.resolve("MANIFEST.MF"));
				if (signatureFilesFound || manifestDigestsFound) {
					log().info("Jar '" + jar.toString() + "' is already signed. The signature will be overwritten.");
					Zips.packJar(unpackedJar, jar, false);
				} else {
					log().info("No signature was found in Jar '" + jar.toString() + "', it will be signed without touching it. Signature would have been overwritten otherwise.");
				}
				return delegate().sign(jar, options);
			} finally {
				Paths.deleteQuietly(unpackedJar);
			}
		}

		private boolean removeSignatureFilesIfAny(Path metaInf) throws IOException {
			if (Files.exists(metaInf)) {
				try (DirectoryStream<Path> content = Files.newDirectoryStream(metaInf, "*.{SF,DSA,RSA,EC}")) {
					List<Path> signatureFiles = StreamSupport.stream(content.spliterator(), false).toList();
                    for (Path f : signatureFiles) {
						log().debug("Deleting signature file '" + f + "'");
						Paths.delete(f);
					}
					return !signatureFiles.isEmpty();
				}
			}
			return false;
		}

		private boolean removeManifestDigestsIfAny(Path manifestPath) throws IOException {
			if (Files.exists(manifestPath)) {
				Manifest manifest = readManifest(manifestPath);
				List<String> keysOfRemovedDigests = removeDigestAttributes(manifest);
				if (!keysOfRemovedDigests.isEmpty()) {
					pruneEmptyEntries(manifest, keysOfRemovedDigests);
					writeManifest(manifest, manifestPath);
				}
				return !keysOfRemovedDigests.isEmpty();
			}
			return false;
		}

		private List<String> removeDigestAttributes(Manifest manifest) {
			return manifest.getEntries().entrySet().stream().map(e -> {
				if (e.getValue().keySet().removeIf(k -> k.toString().endsWith(DIGEST_ATTRIBUTE_SUFFIX))) {
					log().debug("Deleting digest attribute(s) of entry '"+e.getKey()+"'");
					return e.getKey();
				}
				return null;
			}).filter(Objects::nonNull).toList();
          }

          private void pruneEmptyEntries(Manifest manifest, List<String> keysOfRemovedDigests) {
			keysOfRemovedDigests.forEach(k -> {
				if (manifest.getAttributes(k).isEmpty()) {
					log().debug("Deleting manifest entry for '"+k+"' as it has no attribute anymore");
					manifest.getEntries().remove(k);
				}
			});
		}

		private static void writeManifest(Manifest manifest, Path path) throws IOException {
			try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
				manifest.write(os);
			}
		}

		private static Manifest readManifest(Path manifestPath) throws IOException {
			Manifest manifest = new Manifest();
			try(InputStream is = Files.newInputStream(manifestPath, StandardOpenOption.READ)) {
				manifest.read(is);
			}
			return manifest;
		}
	}

	@VisibleForTesting static Set<MessageDigestAlgorithm> getAllUsedDigestAlgorithm(Path jar) throws IOException {
		Set<MessageDigestAlgorithm> usedDigestAlg = EnumSet.noneOf(MessageDigestAlgorithm.class);
		try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
			JarEntry jarEntry = jis.getNextJarEntry();
			while (jarEntry != null) {
				Attributes attributes = jarEntry.getAttributes();
				if (attributes != null) {
					for (Object k : attributes.keySet()) {
						if (k.toString().endsWith(DIGEST_ATTRIBUTE_SUFFIX)) {
							String digestAlgName = k.toString().substring(0, k.toString().length() - DIGEST_ATTRIBUTE_SUFFIX.length());
							usedDigestAlg.add(MessageDigestAlgorithm.fromStandardName(digestAlgName));
						}
					}
				}
				jarEntry = jis.getNextJarEntry();
			}
		}
		return usedDigestAlg;
	}

	@VisibleForTesting static MessageDigestAlgorithm getDigestAlgorithmToReuse(Path jar) throws IOException {
		Set<MessageDigestAlgorithm> usedDigestAlg = getAllUsedDigestAlgorithm(jar);
		if (usedDigestAlg.isEmpty()) {
			throw new IllegalArgumentException("Jar '" + jar + "' is not signed while it was asked to be resigned.");
		} else if (usedDigestAlg.size() > 1) {
			throw new IllegalArgumentException("Can't resign with the same digest algorithm. Jar '" + jar + "' contains entries that has been signed with more than one digest algorithm: '" + Joiner.on(", ").join(usedDigestAlg) + "'. Use another strategy to resign.");
		} else {
			return usedDigestAlg.iterator().next();
		}
	}

	private static class ResignWithSameDigestAlg extends Resign {

		public ResignWithSameDigestAlg(JarSigner delegate, Log log) {
			super(delegate, log);
		}

		@Override
		protected int resign(Path jar, Options options) throws IOException {
			log().debug("Jar signing options before change by strategy '" + Strategy.RESIGN_WITH_SAME_DIGEST_ALGORITHM + "': " + options);
			Options newOptions = Options.copy(options)
					.digestAlgorithm(getDigestAlgorithmToReuse(jar))
					.build();
			return super.resign(jar, newOptions);
		}
	}

	private static class OverwriteSignatureWithSameDigestAlg extends OverwriteSignature {

		private OverwriteSignatureWithSameDigestAlg(JarSigner delegate, Log log) {
			super(delegate, log);
		}

		@Override
		protected int resign(Path jar, Options options) throws IOException {
			log().debug("Jar signing options before change by strategy '" + Strategy.OVERWRITE_WITH_SAME_DIGEST_ALGORITHM + "': " + options);
			Options newOptions = Options.copy(options)
					.digestAlgorithm(getDigestAlgorithmToReuse(jar))
					.build();
			return super.resign(jar, newOptions);
		}

	}
}
