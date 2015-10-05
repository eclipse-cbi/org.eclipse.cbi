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

	JarResigner(JarSigner delegate) {
		this.delegate = Preconditions.checkNotNull(delegate);
	}

	public static JarSigner create(Strategy strategy, JarSigner delegate) {
		final JarSigner ret;
		switch (strategy) {
			case DO_NOT_RESIGN:
				ret = new DoNotResign(delegate);
				break;
			case THROW_EXCEPTION:
				ret = new ThrowException(delegate);
				break;
			case RESIGN:
				ret = new Resign(delegate);
				break;
			case RESIGN_WITH_SAME_DIGEST_ALGORITHM:
				ret = new ResignWithSameDigestAlg(delegate);
				break;
			case OVERWRITE:
				ret = new OverwriteSignature(delegate);
				break;
			case OVERWRITE_WITH_SAME_DIGEST_ALGORITHM:
				ret = new OverwriteSignatureWithSameDigestAlg(delegate);
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
	
	public static JarSigner doNotResign(JarSigner jarSigner) {
		return new DoNotResign(jarSigner);
	}
	
	public static JarSigner throwException(JarSigner jarSigner) {
		return new ThrowException(jarSigner);
	}
	
	public static JarSigner resignWithSameDigestAlgorithm(JarSigner jarSigner) {
		return new ResignWithSameDigestAlg(jarSigner);
	}

	public static JarSigner resign(JarSigner jarSigner) {
		return new Resign(jarSigner);
	}
	
	public static JarSigner overwriteWithSameDigestAlgorithm(JarSigner jarSigner) {
		return new OverwriteSignatureWithSameDigestAlg(jarSigner);
	}
	
	public static JarSigner overwrite(JarSigner jarSigner) {
		return new OverwriteSignature(jarSigner);
	}
	
	private static class DoNotResign extends JarResigner {
		
		DoNotResign(JarSigner delegate) {
			super(delegate);
		}

		@Override
		public int resign(Path jar, Options options) {
			return 0;
		}
	}
	
	private static class ThrowException extends JarResigner {
		
		ThrowException(JarSigner delegate) {
			super(delegate);
		}

		@Override
		public int resign(Path jar, Options options) {
			throw new IllegalStateException("Jar '" + jar  + "' is already signed");
		}
	}
	
	private static class Resign extends JarResigner {

		Resign(JarSigner delegate) {
			super(delegate);
		}

		@Override
		protected int resign(Path jar, Options options) throws IOException {
			return delegate().sign(jar, options);
		}
	}
	
	private static class OverwriteSignature extends JarResigner {

		
		OverwriteSignature(JarSigner delegate) {
			super(delegate);
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

		public ResignWithSameDigestAlg(JarSigner delegate) {
			super(delegate);
		}
		
		@Override
		protected int resign(Path jar, Options options) throws IOException {
			Options newOptions = Options.copy(options)
					.digestAlgorithm(getDigestAlgorithmToReuse(jar))
					.build();
			return super.resign(jar, newOptions);
		}
	}
	
	private static class OverwriteSignatureWithSameDigestAlg extends OverwriteSignature {

		private OverwriteSignatureWithSameDigestAlg(JarSigner delegate) {
			super(delegate);
		}
		
		@Override
		protected int resign(Path jar, Options options) throws IOException {
			Options newOptions = Options.copy(options)
					.digestAlgorithm(getDigestAlgorithmToReuse(jar))
					.build();
			return super.resign(jar, newOptions);
		}
		
	}
}
