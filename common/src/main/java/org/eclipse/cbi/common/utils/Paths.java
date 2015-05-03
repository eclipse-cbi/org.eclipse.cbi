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
package org.eclipse.cbi.common.utils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Collection of {@link Path}-related utility methods.
 */
public class Paths {

	private static final class DeletionVisitor extends SimpleFileVisitor<Path> {
		
		private final boolean quiet;

		public DeletionVisitor(boolean quiet) {
			this.quiet = quiet;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			Objects.requireNonNull(dir);
			if (exc == null) {
				try {
					if (quiet)
						Files.deleteIfExists(dir);
					else
						Files.delete(dir);
					return FileVisitResult.CONTINUE;
				} catch (IOException e) {
					if (quiet) {
						return FileVisitResult.CONTINUE;
					} else {
						throw e;
					}
				}
			} else if (quiet) {
				return FileVisitResult.CONTINUE;
			} else {
				throw exc;
			}
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Objects.requireNonNull(file);
			Objects.requireNonNull(attrs);
			
			if (quiet)
				Files.deleteIfExists(file);
			else
				Files.delete(file);
			return FileVisitResult.CONTINUE;
		}
		
		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			Objects.requireNonNull(file);
			
			if (quiet) {
				return FileVisitResult.CONTINUE;
			} else {
				throw exc;
			}
		}
	}

	/**
	 * Delete the given {@link Path} without throwing any Exception if any
	 * occurs during the deletion. If the path is a directory, it will be
	 * deleted recursively.
	 * 
	 * @param path
	 *            the path to delete
	 */
	public static void deleteQuietly(Path path) {
		try {
			doDelete(path, true);
		} catch (Exception e) {
			// swallow
		}
	}
	
	/**
	 * Delete the given {@link Path} whether it is a directory or a simple file.
	 * If the path is a directory, it will be deleted recursively.
	 * 
	 * @param path
	 *            the path to delete
	 * @throws IOException 
	 */
	public static void delete(Path path) throws IOException {
		doDelete(path, false);
	}

	private static void doDelete(Path path, boolean quiet) throws IOException {
		Objects.requireNonNull(path);
		if (Files.isDirectory(path)) {
			Files.walkFileTree(path, new DeletionVisitor(quiet));
		} else if (quiet) {
			Files.deleteIfExists(path);
		} else {
			Files.delete(path);
		}
	}
	
	public static Path getParent(Path path) {
		Objects.requireNonNull(path);
		final Path parent;
		final Path normalizedParent = path.normalize().getParent();
		if (normalizedParent == null) {
			parent = path.getFileSystem().getRootDirectories().iterator().next();
		} else {
			parent = normalizedParent;
		}
		return parent;
	}
}
