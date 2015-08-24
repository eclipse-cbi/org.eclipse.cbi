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
package org.eclipse.cbi.webservice.signing.jar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

public abstract class ResigningStrategy {

	private static final ResigningStrategy DO_NOT_RESIGN_STRATEGY = new DoNotResign();
	private static final ResigningStrategy THROW_EXCEPTION_STRATEGY = new ThrowException();

	ResigningStrategy() {
		
	}
	
	public abstract Path resignJar(Path jar) throws IOException;
	
	public static ResigningStrategy doNotResign() {
		return DO_NOT_RESIGN_STRATEGY;
	}
	
	public static ResigningStrategy throwException() {
		return THROW_EXCEPTION_STRATEGY;
	}
	
	public static ResigningStrategy resignWithSameDigestAlgorithm(JarSigner jarSigner) {
		return new ResignWithSameDigestAlg(jarSigner);
	}

	public static ResigningStrategy resign(JarSigner jarSigner, MessageDigestAlgorithm digestAlgorithm) {
		return new Resign(jarSigner, digestAlgorithm);
	}
	
	public static ResigningStrategy overwriteWithSameDigestAlgorithm(JarSigner jarSigner, Path tempFolder) {
		return new OverwriteSignatureWithSameDigestAlg(jarSigner, tempFolder);
	}
	
	public static ResigningStrategy overwrite(JarSigner jarSigner,MessageDigestAlgorithm digestAlgorithm, Path tempFolder) {
		return new OverwriteSignature(jarSigner, digestAlgorithm, tempFolder);
	}
	
	@VisibleForTesting static Set<MessageDigestAlgorithm> getAllUsedDigestAlgorithm(Path jar) throws IOException {
		Set<MessageDigestAlgorithm> usedDigestAlg = EnumSet.noneOf(MessageDigestAlgorithm.class);
		try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
			JarEntry jarEntry = jis.getNextJarEntry();
			while (jarEntry != null) {
				Attributes attributes = jarEntry.getAttributes();
				if (attributes != null) {
					attributes.keySet().forEach(k -> {
						if (k.toString().endsWith("-Digest")) {
							String digestAlgName = k.toString().substring(0, k.toString().length() - "-Digest".length());
							usedDigestAlg.add(MessageDigestAlgorithm.fromStandardName(digestAlgName));
						}
					});
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

	private static class DoNotResign extends ResigningStrategy {
		@Override
		public Path resignJar(Path jar) {
			return jar;
		}
	}
	
	private static class ThrowException extends ResigningStrategy {
		@Override
		public Path resignJar(Path jar) {
			throw new IllegalStateException("Jar '" + jar  + "' is already signed");
		}
	}
	
	private static class ResignWithSameDigestAlg extends ResigningStrategy {

		private final JarSigner jarSigner;

		private ResignWithSameDigestAlg(JarSigner jarSigner) {
			this.jarSigner = Preconditions.checkNotNull(jarSigner);
		}
		
		@Override
		public Path resignJar(Path jar) throws IOException {
			return jarSigner.doSign(jar, getDigestAlgorithmToReuse(jar));
		}
		
	}
	
	private static class Resign extends ResigningStrategy {

		private final JarSigner jarSigner;
		private final MessageDigestAlgorithm digestAlgorithm;

		private Resign(JarSigner jarSigner, MessageDigestAlgorithm digestAlgorithm) {
			this.jarSigner = Preconditions.checkNotNull(jarSigner);
			this.digestAlgorithm = Preconditions.checkNotNull(digestAlgorithm);
		}
		
		@Override
		public Path resignJar(Path jar) throws IOException {
			return jarSigner.doSign(jar, digestAlgorithm);
		}
	}
	
	private static class OverwriteSignature extends ResigningStrategy {

		private final JarSigner jarSigner;
		private final MessageDigestAlgorithm digestAlgorithm;
		private final Path tempFolder;

		private OverwriteSignature(JarSigner jarSigner, MessageDigestAlgorithm digestAlgorithm, Path tempFolder) {
			this.jarSigner = Preconditions.checkNotNull(jarSigner);
			this.digestAlgorithm = Preconditions.checkNotNull(digestAlgorithm);
			this.tempFolder = Preconditions.checkNotNull(tempFolder);
		}
		
		@Override
		public Path resignJar(Path jar) throws IOException {
			Path unpackedJar = Files.createTempDirectory(tempFolder, "overwriteSignature-");
			try {
				Zips.unpackJar(jar, unpackedJar);
				Path metaInf = unpackedJar.resolve("META-INF");
				if (Files.exists(metaInf)) {
					try (Stream<Path> metaInfContent = Files.list(metaInf)) {
						metaInfContent.filter(p -> {
							final String filename = p.getFileName().toString();
							return filename.endsWith(".SF") || filename.endsWith(".DSA") || filename.endsWith(".RSA") || filename.endsWith(".EC");
						}).forEach(Paths::deleteQuietly);
					}
				} else {
					throw new IOException("'META-INF' folder does not exist in Jar file '" + jar + "'");
				}
				Zips.packJar(unpackedJar, jar, false);
				return jarSigner.doSign(jar, digestAlgorithm);
			} finally {
				Paths.deleteQuietly(unpackedJar);
			}
		}
	}
	
	private static class OverwriteSignatureWithSameDigestAlg extends ResigningStrategy {

		private final JarSigner jarSigner;
		private final Path tempFolder;

		private OverwriteSignatureWithSameDigestAlg(JarSigner jarSigner, Path tempFolder) {
			this.jarSigner = jarSigner;
			this.tempFolder = tempFolder;
		}
		
		@Override
		public Path resignJar(Path jar) throws IOException {
			return new OverwriteSignature(jarSigner, getDigestAlgorithmToReuse(jar), tempFolder).resignJar(jar);
		}
		
	}
}
