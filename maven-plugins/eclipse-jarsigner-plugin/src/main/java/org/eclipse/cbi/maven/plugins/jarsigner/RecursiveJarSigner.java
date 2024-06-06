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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class RecursiveJarSigner extends FilteredJarSigner {

	abstract JarSigner delegate();
	
	abstract int maxDepth();
	
	/**
	 * The log on which feedback will be provided.
	 */
	abstract Log log();
	
	@Override
	int doSignJar(Path jar, Options options) throws IOException {
		return doSignJarRecursively(jar, options, 0);
	}
	
	/**
	 * Sign this Jar and its nested Jar.
	 *
	 * @param file
	 *            the file to sign
	 * @param digestAlgorithm 
	 * @param currentDepth
	 *            the current nesting depth of the current file.
	 * @param signedFile
	 * @return the number of Jar that has been signed.
	 * @throws MojoExecutionException
	 */
	private int doSignJarRecursively(final Path file, Options options, int currentDepth) throws IOException {
		int nestedJarsSigned = 0;
		if (currentDepth >= maxDepth()) {
			log().debug(String.format("Signing of nested jars within '" + file + "' is disabled (current depth = %d, max depth = %d).", currentDepth, maxDepth()));
		} else {
			nestedJarsSigned = signNestedJars(file, options, currentDepth);
		}

		return nestedJarsSigned  + delegate().sign(file, options);
	}
	
	/**
	 * Signs the inner jars in the given jar file
	 *
	 * @param file
	 *            jar file containing inner jars to be signed
	 * @param currentDepth
	 * @param digestAlgorithm 
	 * @param innerJars
	 *            A list of inner jars that needs to be signed
	 * @return the number of Jar that has been signed.
	 */
	private int signNestedJars(Path file, Options options, int currentDepth) throws IOException {
		int numberOfSignedNestedJar = 0;
		Path jarUnpackFolder = null;
		try {
			jarUnpackFolder = Files.createTempDirectory(Paths.getParent(file), file.getFileName().toString() + "_unpacked_");
			Zips.unpackJar(file, jarUnpackFolder);

			// sign inner jars
			NestedJarSigner nestedJarSigner = new NestedJarSigner(currentDepth, options);
			Files.walkFileTree(jarUnpackFolder, nestedJarSigner);

			// rejaring with the signed inner jars
			Zips.packJar(jarUnpackFolder, file, false);

			numberOfSignedNestedJar = nestedJarSigner.getNumberOfSignedNestedJar();
		} finally {
			if (jarUnpackFolder != null) {
				Paths.deleteQuietly(jarUnpackFolder);
			}
		}
		return numberOfSignedNestedJar;
	}

	private final class NestedJarSigner extends SimpleFileVisitor<Path> {

		private final int currentDepth;
		
		private final Options options;

		private int numberOfSignedNestedJar;

		NestedJarSigner(int currentDepth, Options options) {
			this.currentDepth = currentDepth;
			this.options = options;
			this.numberOfSignedNestedJar = 0;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (file.getFileSystem().getPathMatcher(EclipseJarSignerFilter.DOT_JAR_GLOB_PATTERN).matches(file) &&
					filter().shouldBeSigned(file)) {
				numberOfSignedNestedJar += doSignJarRecursively(file, options, currentDepth+1);
			}
			return FileVisitResult.CONTINUE;
		}

		public int getNumberOfSignedNestedJar() {
			return numberOfSignedNestedJar;
		}
	}
	
	public static Builder builder() {
		return new AutoValue_RecursiveJarSigner.Builder().filter(Filters.ALWAYS_SIGN);
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		/**
		 * Whether the nested Jars should be signed (when >= 1). Signs all nested Jars recursively
		 * when {@link Integer#MAX_VALUE}. Set to 0 if you want to avoid signing nested Jars.
		 */
		public abstract Builder maxDepth(int max);
		public abstract Builder filter(Filter filter);
		public abstract Builder delegate(JarSigner jarSigner);
		public abstract Builder log(Log log);
		public abstract RecursiveJarSigner build();
	}
}
