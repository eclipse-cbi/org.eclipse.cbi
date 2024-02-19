/*******************************************************************************
 * Copyright (c) 2015, 2018 Eclipse Foundation and others
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
package org.eclipse.cbi.common.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;
import org.apache.commons.compress.archivers.zip.ZipFile;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.UnsignedInteger;

/**
 * Utility class to work with Zip files ({@link Path} based).
 */
public class Zips {

	private static final int PERM_MASK = 0777;
	private static final String ZIP_ENTRY_NAME_SEPARATOR = "/";
	private static final String BACKSLASH_ESCAPE_REPLACEMENT = "\\\\\\\\";
	private static final Pattern BACKSLASH_PATTERN = Pattern.compile("\\\\");

	/**
	 * Unzip the given {@code source} Zip file in the {@code outputDir}.
	 *
	 * @param source    the file to unzip.
	 * @param outputDir the output directory where the Zip will be unpacked.
	 * @return the number of unpacked entries
	 * @throws IOException
	 */
	public static int unpackZip(Path source, final Path outputDir) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (ZipFile zipFile = ZipFile.builder().setSeekableByteChannel(Files.newByteChannel(source)).get()) {
			return unpack(zipFile, outputDir);
		}
	}

	private static int unpack(ZipFile zipFile, final Path outputDir) throws IOException {
		int unpack = 0;
		Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
		while (entries.hasMoreElements()) {
			ZipArchiveEntry entry = entries.nextElement();
			unpackEntry(zipFile, entry, outputDir);
			unpack++;
		}
		return unpack;
	}

	private static Path unpackEntry(ZipFile zipFile, ZipArchiveEntry entry, final Path outputDir) throws IOException {
		final Path entryPath = outputDir.resolve(entry.getName());

		if (!entryPath.normalize().startsWith(outputDir)) {
			// Potentially a malicious zip archive.
			// https://security.snyk.io/research/zip-slip-vulnerability
			// https://owasp.org/www-community/attacks/Path_Traversal
			throw new IOException("Bad Zip entry  '" + entry.getName() + "'");
		}

		if (entry.isDirectory()) {
			Files.createDirectories(entryPath);
		} else {
			Path parentPath = entryPath.normalize().getParent();
			Files.createDirectories(parentPath);
			if (entry.isUnixSymlink()) {
				Files.createSymbolicLink(entryPath, entryPath.getFileSystem().getPath(zipFile.getUnixSymlink(entry)));
			} else {
				Files.copy(zipFile.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
			}
		}

		setPermissions(entryPath, entry, LinkOption.NOFOLLOW_LINKS);
		setLastModifiedTime(entryPath, FileTime.from(entry.getTime(), TimeUnit.MILLISECONDS),
				LinkOption.NOFOLLOW_LINKS);

		return entryPath;
	}

	private static Path setLastModifiedTime(Path path, FileTime fileTime, LinkOption... linkOptions)
			throws IOException {
		// This "if" can be removed once it runs on JDK11+ (see
		// https://bugs.openjdk.java.net/browse/JDK-8220793)
		if (!Files.isSymbolicLink(path)) {
			BasicFileAttributeView attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class,
					linkOptions);
			if (attributes != null) {
				attributes.setTimes(fileTime, null, null);
			}
		}
		return path;
	}

	private static Path setPermissions(Path path, ZipArchiveEntry entry, LinkOption... linkOptions) throws IOException {
		// This "if" cannot be removed except for macOS platform, if jdk would support
		// such behavior
		if (!Files.isSymbolicLink(path) && entry.getPlatform() == ZipArchiveEntry.PLATFORM_UNIX) {
			PosixFileAttributeView attributes = Files.getFileAttributeView(path, PosixFileAttributeView.class,
					linkOptions);
			if (attributes != null) {
				attributes.setPermissions(MorePosixFilePermissions.fromFileMode(entry.getUnixMode() & PERM_MASK));
			}
		}
		return path;
	}

	/**
	 * Unzip the given {@code source} Jar file in the {@code outputDir}.
	 *
	 * @param source    the file to unzip.
	 * @param outputDir the output directory where the Jar will be unpacked. It does
	 *                  not have to exist beforehand.
	 * @return the number of unpacked entries
	 * @throws IOException
	 */
	public static int unpackJar(Path source, Path outputDir) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (ZipFile zipFile = ZipFile.builder().setSeekableByteChannel(Files.newByteChannel(source)).get()) {
			return unpack(zipFile, outputDir);
		}
	}

	public static int unpackTarGz(Path sourcePath, Path outputDir) throws IOException {
		try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(
				new GZIPInputStream(Files.newInputStream(sourcePath)))) {
			return unpack(tarArchiveInputStream, outputDir);
		}
	}

	@VisibleForTesting
	static int unpack(TarArchiveInputStream tais, Path outputDir) throws IOException {
		int unpackedEntries = 0;
		for (TarArchiveEntry entry = tais.getNextEntry(); entry != null; entry = tais.getNextEntry()) {
			unpackEntry(tais, entry, outputDir);
			unpackedEntries++;
		}

		return unpackedEntries;
	}

	private static Path unpackEntry(TarArchiveInputStream tais, TarArchiveEntry entry, final Path outputDir)
			throws IOException {
		final Path entryPath = outputDir.resolve(entry.getName());

		if (!entryPath.normalize().startsWith(outputDir)) {
			// Potentially a malicious zip archive.
			// // https://security.snyk.io/research/zip-slip-vulnerability
			// https://owasp.org/www-community/attacks/Path_Traversal
			throw new IOException("Bad Tar entry  '" + entry.getName() + "'");
		}

		if (entry.isDirectory()) {
			Files.createDirectories(entryPath);
		} else {
			Path parentPath = entryPath.normalize().getParent();
			Files.createDirectories(parentPath);
			if (entry.isLink()) {
				Files.createLink(entryPath, outputDir.resolve(entry.getLinkName()));
			} else if (entry.isSymbolicLink()) {
				Files.createSymbolicLink(entryPath, entryPath.getFileSystem().getPath(entry.getLinkName()));
			} else if (entry.isFile()) {
				Files.copy(tais, entryPath, StandardCopyOption.REPLACE_EXISTING);
			} else {
				throw new IOException("Type of a Tar entry is not supported");
			}
		}

		setPermissions(entryPath, entry, LinkOption.NOFOLLOW_LINKS);
		setLastModifiedTime(entryPath, FileTime.from(entry.getLastModifiedDate().getTime(), TimeUnit.MILLISECONDS),
				LinkOption.NOFOLLOW_LINKS);

		return entryPath;
	}

	private static Path setPermissions(final Path path, TarArchiveEntry entry, LinkOption... linkOptions)
			throws IOException {
		// to set permissions if we are on a posix file system.
		PosixFileAttributeView attributes = Files.getFileAttributeView(path, PosixFileAttributeView.class, linkOptions);
		if (attributes != null) {
			attributes.setPermissions(MorePosixFilePermissions.fromFileMode(entry.getMode()));
		}
		return path;
	}

	/**
	 * Zip the given {@code source} file or folder in the {@code targetZip} Zip
	 * file. If {@code preserveRoot} is set to true, the output Zip will contain the
	 * folder and its contents, only its contents otherwise.
	 *
	 * @param source       the folder to zip.
	 * @param targetZip    the Zip file to create or overwrite.
	 * @param preserveRoot whether the {@code source} folder should be kept in the
	 *                     target Zip.
	 * @return the number of packed entries
	 * @throws IOException
	 */
	public static int packZip(Path source, Path targetZip, boolean preserveRoot) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(newBufferedOutputStream(targetZip))) {
			return packEntries(source, zos, preserveRoot, Set.of());
		}
	}

	/**
	 * Zip the given {@code source} file or folder in the {@code targetJar} Jar
	 * file. If {@code preserveRoot} is set to true, the output Zip will contain the
	 * folder and its contents, only its contents otherwise.
	 *
	 * @param source       the folder to zip.
	 * @param targetJar    the Jar file to create or overwrite.
	 * @param preserveRoot whether the {@code source} folder should be kept in the
	 *                     target Jar.
	 * @return the number of packed entries
	 * @throws IOException
	 */
	public static int packJar(Path source, Path targetJar, boolean preserveRoot) throws IOException {
		checkPathExists(source, "'source' path must exists");
		try (JarArchiveOutputStream jos = new JarArchiveOutputStream(newBufferedOutputStream(targetJar))) {
			final Set<Path> pathToExcludes;
			if (!preserveRoot) {
				// if we preserve root folder, then we won't find META-INF/MANIFEST.MF as root
				// entries
				pathToExcludes = packManifestIfAny(source, jos);
			} else {
				pathToExcludes = Set.of();
			}
			return packEntries(source, jos, preserveRoot, pathToExcludes) + pathToExcludes.size();
		}
	}

	/**
	 * Looks for META-INF/MANIFEST.MF file in the given source folder and add them
	 * as entries to the JarOutputStream.
	 *
	 * @param source       the folder to jar. If not a directory, returns 0 and do
	 *                     nothing
	 * @param preserveRoot whether the root folder
	 * @param jos          the jar output stream to write
	 * @return set of paths to the META-INF and MANIFEST.MF, empty set otherwise.
	 * @throws IOException
	 */
	private static Set<Path> packManifestIfAny(Path source, JarArchiveOutputStream jos) throws IOException {
		final Set<Path> ret;
		if (Files.isDirectory(source)) {
			Path metaInf = source.resolve("META-INF");
			Path manifest = metaInf.resolve("MANIFEST.MF");
			if (Files.exists(manifest)) {
				putDirectoryEntry(metaInf, jos, source.relativize(metaInf));
				putFileEntry(manifest, jos, source.relativize(manifest));
				ret = Set.of(metaInf, manifest);
			} else {
				ret = Set.of();
			}
		} else {
			ret = Set.of();
		}
		return ret;
	}

	private static Path checkPathExists(Path source, String msg) {
		if (!Files.exists(source)) {
			throw new IllegalArgumentException(msg);
		}
		return source;
	}

	private static BufferedOutputStream newBufferedOutputStream(Path path) throws IOException {
		return new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING));
	}

	private static int packEntries(Path source, ZipArchiveOutputStream zos, boolean preserveRoot,
			Set<Path> pathToExcludes) throws IOException {
		final PathMapper pathMapper;
		if (preserveRoot) {
			pathMapper = new PreserveRootPathMapper(source);
		} else {
			pathMapper = new NoPreserveRootPathMapper(source);
		}
		if (Files.isDirectory(source)) {
			PackerFileVisitor packerFileVisitor = new PackerFileVisitor(zos, pathMapper, pathToExcludes);
			Files.walkFileTree(source, packerFileVisitor);
			return packerFileVisitor.packedEntries();
		} else if (Files.isSymbolicLink(source)) {
			putSymlinkEntry(source, zos, pathMapper, source.getFileName());
			return 1;
		} else {
			putFileEntry(source, zos, source.getFileName());
			return 1;
		}
	}

	private static String entryNameFrom(Path path, boolean isDirectory) {
		final String pathFsSeparator = path.getFileSystem().getSeparator();
		final String escapedEntryName;

		if (pathFsSeparator != ZIP_ENTRY_NAME_SEPARATOR) {
			String separatorRegex = BACKSLASH_PATTERN.matcher(pathFsSeparator).replaceAll(BACKSLASH_ESCAPE_REPLACEMENT);
			escapedEntryName = path.toString().replaceAll(separatorRegex, ZIP_ENTRY_NAME_SEPARATOR);
		} else {
			escapedEntryName = path.toString();
		}

		if (isDirectory && !escapedEntryName.endsWith(ZIP_ENTRY_NAME_SEPARATOR)) {
			return escapedEntryName + ZIP_ENTRY_NAME_SEPARATOR;
		} else {
			return escapedEntryName;
		}
	}

	private static void putSymlinkEntry(Path file, ZipArchiveOutputStream zos, PathMapper pathMapper, Path entryPath)
			throws IOException {
		ZipArchiveEntry zipEntry = createArchiveEntry(zos, entryNameFrom(entryPath, false));
		zipEntry.setTime(Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis());

		Path linkTarget = Files.readSymbolicLink(file);
		ZipEncoding zipEncoding = ZipEncodingHelper.getZipEncoding(zos.getEncoding());
		ByteBuffer rawLinkTargetEntryPath = zipEncoding
				.encode(entryNameFrom(linkTarget, linkTarget.toString().endsWith(file.getFileSystem().getSeparator())));
		byte[] b = new byte[rawLinkTargetEntryPath.remaining()];
		rawLinkTargetEntryPath.get(b);

		zipEntry.setSize(b.length);
		zipEntry.setUnixMode(UnixStat.LINK_FLAG | UnixStat.DEFAULT_LINK_PERM);

		zos.putArchiveEntry(zipEntry);
		ByteStreams.copy(new ByteArrayInputStream(b), zos);
		zos.closeArchiveEntry();
	}

	private static void putFileEntry(Path file, ZipArchiveOutputStream zos, Path entryPath) throws IOException {
		ZipArchiveEntry zipEntry = createArchiveEntry(zos, entryNameFrom(entryPath, false));
		zipEntry.setTime(Files.getLastModifiedTime(file).toMillis());
		zipEntry.setSize(Files.size(file));

		PosixFileAttributeView posixFileAttributeView = Files.getFileAttributeView(file, PosixFileAttributeView.class);
		if (posixFileAttributeView != null) {
			PosixFileAttributes posixFileAttributes = posixFileAttributeView.readAttributes();
			zipEntry.setUnixMode(UnsignedInteger
					.valueOf(MorePosixFilePermissions.toFileMode(posixFileAttributes.permissions())).intValue());
		}

		zos.putArchiveEntry(zipEntry);
		Files.copy(file, zos);
		zos.closeArchiveEntry();
	}

	private static void putDirectoryEntry(Path dir, ZipArchiveOutputStream zos, Path entryPath) throws IOException {
		ZipArchiveEntry zipEntry = createArchiveEntry(zos, entryNameFrom(entryPath, true));
		zipEntry.setTime(Files.getLastModifiedTime(dir).toMillis());

		PosixFileAttributeView posixFileAttributeView = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
		if (posixFileAttributeView != null) {
			PosixFileAttributes posixFileAttributes = posixFileAttributeView.readAttributes();
			zipEntry.setUnixMode(UnsignedInteger
					.valueOf(MorePosixFilePermissions.toFileMode(posixFileAttributes.permissions())).intValue());
		}

		zos.putArchiveEntry(zipEntry);
		zos.closeArchiveEntry();
	}

	private static ZipArchiveEntry createArchiveEntry(ZipArchiveOutputStream zos, String entryName) {
		if (zos instanceof JarArchiveOutputStream) {
			return new JarArchiveEntry(entryName);
		}
		return new ZipArchiveEntry(entryName);
	}

	private interface PathMapper {
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
		private final ZipArchiveOutputStream zos;
		private final Set<Path> pathToExcludes;
		private int packedEntries;

		private PackerFileVisitor(ZipArchiveOutputStream zos, PathMapper pathMapper, Set<Path> pathToExcludes) {
			this.pathMapper = pathMapper;
			this.zos = zos;
			this.pathToExcludes = pathToExcludes;
		}

		int packedEntries() {
			return this.packedEntries;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			if (!pathToExcludes.contains(dir)) {
				Path entryPath = pathMapper.mapTo(dir);
				// do not create an entry for empty root path when not preserving root
				if (!Strings.isNullOrEmpty(entryPath.toString())) {
					putDirectoryEntry(dir, zos, entryPath);
					packedEntries++;
				}
			} else {
				return FileVisitResult.CONTINUE;
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (!pathToExcludes.contains(file)) {
				Path entryPath = pathMapper.mapTo(file);
				if (Files.isSymbolicLink(file)) {
					putSymlinkEntry(file, zos, pathMapper, entryPath);
				} else {
					putFileEntry(file, zos, entryPath);
				}
				packedEntries++;
			}
			return FileVisitResult.CONTINUE;
		}
	}
}
