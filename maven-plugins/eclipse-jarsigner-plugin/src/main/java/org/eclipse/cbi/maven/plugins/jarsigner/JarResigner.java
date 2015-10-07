/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public abstract class JarResigner implements JarSigner {

	public static enum Strategy {
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
				Attributes attributes = nextJarEntry.getAttributes();
				if (attributes != null) {
					alreadySigned = Iterables.any(attributes.keySet(), new Predicate<Object>() {
						@Override
						public boolean apply(Object k) {
							return k.toString().endsWith("-Digest");
						}
					});
				}
				nextJarEntry = jis.getNextJarEntry();
			}
		}
		return alreadySigned;
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
			Path unpackedJar = Files.createTempDirectory(Paths.getParent(jar), "overwriteSignature-");
			try {
				Zips.unpackJar(jar, unpackedJar);
				Path metaInf = unpackedJar.resolve("META-INF");
				if (Files.exists(metaInf)) {
					Files.walkFileTree(metaInf, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							String filename = file.getFileName().toString();
							if (filename.endsWith(".SF") || filename.endsWith(".DSA") || filename.endsWith(".RSA") || filename.endsWith(".EC")) {
								Paths.delete(file);
							}
							return FileVisitResult.CONTINUE;
						}
					});
				} else {
					throw new IOException("'META-INF' folder does not exist in Jar file '" + jar + "'");
				}
				Zips.packJar(unpackedJar, jar, false);
				log().info("Jar '" + jar.toString() + "' is already signed. The signature will be overwritten.");
				return delegate().sign(jar, options);
			} finally {
				Paths.deleteQuietly(unpackedJar);
			}
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
						if (k.toString().endsWith("-Digest")) {
							String digestAlgName = k.toString().substring(0, k.toString().length() - "-Digest".length());
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
