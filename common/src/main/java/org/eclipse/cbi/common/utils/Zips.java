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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Utility class to work with Zip files ({@link Path} based).
 */
public class Zips {
	
	private static final String ZIP_ENTRY_NAME_SEPARATOR = "/";
	private static final String BACKSLASH_ESCAPE_REPLACEMENT = "\\\\\\\\";
	private static final Pattern BACKSLASH_PATTERN = Pattern.compile("\\\\");
	// indexed by the standard binary representation of permission
	// if permission is 644; in binary 110 100 100 
	// the position of the ones give the index of the permission in the array below
	private static final PosixFilePermission[] POSIX_PERMISSIONS  = new PosixFilePermission[] {
		PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
		PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
		PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ,};

	/**
	 * Unzip the given {@code source} Zip file in the {@code outputDir}.
	 * 
	 * @param source
	 *            the file to unzip.
	 * @param outputDir
	 *            the output directory where the Zip will be unpacked.
	 * @return the number of unpacked entries
	 * @throws IOException
	 */
	public static int unpackZip(Path source, Path outputDir) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (ZipInputStream zis = new ZipInputStream(newBufferedInputStream(source))) {
			return unpack(zis, outputDir);
		}
	}

	/**
	 * Unzip the given {@code source} Jar file in the {@code outputDir}.
	 * 
	 * @param source
	 *            the file to unzip.
	 * @param outputDir
	 *            the output directory where the Jar will be unpacked. It does
	 *            not have to exist beforehand.
	 * @return the number of unpacked entries
	 * @throws IOException
	 */
	public static int unpackJar(Path source, Path outputDir) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (JarInputStream jis = new JarInputStream(newBufferedInputStream(source))) {
			return unpack(jis, outputDir);
		}
	}
	
	public static int unpackTarGz(Path sourcePath, Path outputDir) throws IOException {
		try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(sourcePath)))) {
			return unpack(tarArchiveInputStream, outputDir);
		}
	}
	
	public static int unpack(TarArchiveInputStream zis, Path outputDir) throws IOException {
		int unpackedEntries = 0;
		for(TarArchiveEntry entry = zis.getNextTarEntry(); entry != null; entry = zis.getNextTarEntry()) {
			final Path entryPath = outputDir.resolve(entry.getName());
			if (entry.isDirectory()) {
				Files.createDirectories(entryPath);
			} else if (entry.isLink()) {
				Files.createLink(entryPath, outputDir.resolve(entry.getLinkName()));
			} else if (entry.isSymbolicLink()) {
				Files.createSymbolicLink(entryPath, outputDir.resolve(entry.getLinkName()));
			} else if (entry.isFile()) {
				Path parentPath = entryPath.normalize().getParent();
				Files.createDirectories(parentPath);
				Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
			} else {
				throw new IOException("Type of a Tar entry is not supported");
			}

			if (!Files.isSymbolicLink(entryPath)) {
				setPermissions(entry, entryPath);
				Files.setLastModifiedTime(entryPath, FileTime.from(entry.getLastModifiedDate().getTime(), TimeUnit.MILLISECONDS));
			}
			unpackedEntries++;
		}
		
		return unpackedEntries;
	}

	private static void setPermissions(TarArchiveEntry entry, final Path entryPath) throws IOException {
		// to set permissions if we are on a posix file system.
		PosixFileAttributeView attributes = Files.getFileAttributeView(entryPath, PosixFileAttributeView.class);
		if (attributes != null) {
			attributes.setPermissions(modeToPosixPermissions(entry.getMode()));
		}
	}
	
	private static Set<PosixFilePermission> modeToPosixPermissions(int mode) {
		final Set<PosixFilePermission> ret = new LinkedHashSet<PosixFilePermission>();
		for (int i = 8; i >= 0; i--) {
			int perm = mode & (1 << i);
			if (perm != 0) {
				ret.add(POSIX_PERMISSIONS[i]);
			}	
		}
		return ret;
	}

	/**
	 * Zip the given {@code source} file or folder in the {@code targetZip} Zip
	 * file. If {@code preserveRoot} is set to true, the output Zip will contain
	 * the folder and its contents, only its contents otherwise.
	 * 
	 * @param source
	 *            the folder to zip.
	 * @param targetZip
	 *            the Zip file to create or overwrite.
	 * @param preserveRoot
	 *            whether the {@code source} folder should be kept in the target
	 *            Zip.
	 * @return the number of packed entries
	 * @throws IOException
	 */
	public static int packZip(Path source, Path targetZip, boolean preserveRoot) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (ZipOutputStream zos = new ZipOutputStream(newBufferedOutputStream(targetZip))) {
			return packEntries(source, zos, preserveRoot);
		}
	}

	/**
	 * Zip the given {@code source} file or folder in the {@code targetJar} Jar
	 * file. If {@code preserveRoot} is set to true, the output Zip will contain
	 * the folder and its contents, only its contents otherwise.
	 * 
	 * @param source
	 *            the folder to zip.
	 * @param targetJar
	 *            the Jar file to create or overwrite.
	 * @param preserveRoot
	 *            whether the {@code source} folder should be kept in the target
	 *            Jar.
	 * @return the number of packed entries
	 * @throws IOException
	 */
	public static int packJar(Path source, Path targetJar, boolean preserveRoot) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (JarOutputStream jos = new JarOutputStream(newBufferedOutputStream(targetJar))) {
			return packEntries(source, jos, preserveRoot);
		}
	}

	private static Path checkPathExists(Path source, String msg) {
		if (!Files.exists(source)) {
			throw new IllegalArgumentException(msg);
		} else {
			return source;
		}
	}
	
	private static BufferedInputStream newBufferedInputStream(Path source) throws IOException {
		return new BufferedInputStream(Files.newInputStream(source, StandardOpenOption.READ));
	}

	/**
	 * Unzip the given {@code zis} Zip input stream file in the {@code outputDir}.
	 * 
	 * @param source
	 *            the file to unzip.
	 * @param outputDir
	 *            the output directory where the Jar will be unpacked. It does
	 *            not have to exist beforehand.
	 * @return the number of unpacked entries
	 * @throws IOException
	 */
	public static int unpack(ZipInputStream zis, Path outputDir) throws IOException {
		int unpackedEntries = 0;
		for(ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
			final Path entryPath = outputDir.resolve(entry.getName());
			if (entry.isDirectory()) {
				Files.createDirectories(entryPath);
			} else {
				Path parentPath = entryPath.normalize().getParent();
				Files.createDirectories(parentPath);
				Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
			}
			Files.setLastModifiedTime(entryPath, FileTime.from(entry.getTime(), TimeUnit.MILLISECONDS));
			unpackedEntries++;
		}
		return unpackedEntries;
	}

	private static BufferedOutputStream newBufferedOutputStream(Path path) throws IOException {
		return new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE));
	}

	private static int packEntries(Path source, ZipOutputStream zos, boolean preserveRoot) throws IOException {
		if (Files.isDirectory(source)) {
			final PathMapper pathMapper;
			if (preserveRoot) {
				pathMapper = new PreserveRootPathMapper(source);
			} else {
				pathMapper = new NoPreserveRootPathMapper(source);
			}
			PackerFileVisitor packerFileVisitor = new PackerFileVisitor(zos, pathMapper);
			Files.walkFileTree(source, packerFileVisitor);
			return packerFileVisitor.packedEntries();
		} else {
			packFile(source, zos, source.getFileName());
			return 1;
		}
	}

	private static void packFile(Path file, ZipOutputStream zos, Path entryPath) throws IOException {
		ZipEntry zipEntry = new ZipEntry(entryNameFrom(entryPath, false));
		zipEntry.setTime(Files.getLastModifiedTime(file).toMillis());
		zipEntry.setSize(Files.size(file));
		
		zos.putNextEntry(zipEntry);
		Files.copy(file, zos);
		zos.closeEntry();
	}
	
	private static String entryNameFrom(Path path, boolean isDirectoryw) {
		final String pathFsSeparator = path.getFileSystem().getSeparator();
		final String escapedEntryName;
		
		if (pathFsSeparator != ZIP_ENTRY_NAME_SEPARATOR) {
			String separatorRegex = BACKSLASH_PATTERN.matcher(pathFsSeparator).replaceAll(BACKSLASH_ESCAPE_REPLACEMENT);
			escapedEntryName = path.toString().replaceAll(separatorRegex, ZIP_ENTRY_NAME_SEPARATOR);
		} else {
			escapedEntryName = path.toString();
		}
		
		if (isDirectoryw && !escapedEntryName.endsWith(ZIP_ENTRY_NAME_SEPARATOR)) {
			return escapedEntryName + ZIP_ENTRY_NAME_SEPARATOR;
		} else {
			return escapedEntryName;
		}
	}

	private static interface PathMapper {
		Path mapTo(Path name);
	}

	private static final class NoPreserveRootPathMapper implements PathMapper {
		private final Path source;
	
		private NoPreserveRootPathMapper(Path source) {
			this.source = source;
		}
	
		@Override
		public Path mapTo(Path path) {
			return source.relativize(path);
		}
	}

	private static final class PreserveRootPathMapper implements PathMapper {
		private final Path source;
	
		private PreserveRootPathMapper(Path source) {
			this.source = source;
		}
	
		@Override
		public Path mapTo(Path path) {
			return source.getFileName().resolve(source.relativize(path));
		}
	}

	private static final class PackerFileVisitor extends SimpleFileVisitor<Path> {
		private final PathMapper pathMapper;
		private final ZipOutputStream zos;
		private int packedEntries;

		private PackerFileVisitor(ZipOutputStream zos, PathMapper pathMapper) {
			this.pathMapper = pathMapper;
			this.zos = zos;
		}

		int packedEntries() {
			return this.packedEntries;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			Path entryPath = pathMapper.mapTo(dir);
			// do not create an entry for empty root path when not preserving root
			if (!Objects.toString(entryPath.toString(), "").isEmpty()) { 
				ZipEntry zipEntry = new ZipEntry(entryNameFrom(entryPath, true));
				zipEntry.setTime(Files.getLastModifiedTime(dir).toMillis());
				
				zos.putNextEntry(zipEntry);
				zos.closeEntry();
				packedEntries++;
			}
			
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Path entryPath = pathMapper.mapTo(file);
			packFile(file, zos, entryPath);
			packedEntries++;
			return FileVisitResult.CONTINUE;
		}
	}
}
