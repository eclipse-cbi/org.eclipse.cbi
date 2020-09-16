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
package org.eclipse.cbi.common.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ZipsTest {

	private static final long modTime;
	
	static {
		Calendar c = Calendar.getInstance();
		c.set(2001, 3, 7, 8, 30, 12);
		modTime = c.getTimeInMillis();
	}
	
	@DataPoints
	public static Configuration[] fsConfiguration() {
		return new Configuration[] {
				Configuration.unix().toBuilder().setAttributeViews("basic", "owner", "unix", "posix").build(),
				Configuration.osX(),
				Configuration.windows(),
		};
	}
	
	@Theory
	public void testPackSingleFile(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			Path path = createLoremIpsumFile(fs.getPath("workDir/Test.java"), 3);
			Path zip = fs.getPath("testPackSingleFile.zip");
			assertEquals(1, Zips.packZip(path, zip, false));
	
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
				checkNextEntry(zis, path, "Test.java");
				assertNull(zis.getNextEntry());
			}
		}
	}
	
	@Theory
	public void testUnpackZipPreserveFilePerms(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {
			Path zip = fs.getPath("fileperm.zip");
			Files.copy(this.getClass().getResourceAsStream("/fileperm.zip"), zip);
			assertEquals(5, Zips.unpackZip(zip, fs.getPath("unzipFolder")));
			
			PosixFileAttributeView posixView = Files.getFileAttributeView(zip, PosixFileAttributeView.class);
			if (posixView != null) {
				assertEquals("777", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "fileperm", "a")))));
				assertEquals("660", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "fileperm", "b")))));
				assertEquals("744", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "fileperm", "c")))));
				assertEquals("500", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "fileperm", "f1")))));
				assertEquals("777", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "fileperm", "f2")))));
			}
		}
	}
	
	@Test
	public void testPackZipPreserveFilePerms() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("basic", "owner", "unix", "posix").build())) {
			Path a = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileperm", "a"), 1);
			Path b = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileperm", "b"), 1);
			Path c = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileperm", "c"), 1);
			Path f1 = Files.createDirectories(fs.getPath("fileperm", "f1"));
			Path f2 = Files.createDirectories(fs.getPath("fileperm", "f2"));
			
			PosixFileAttributeView posixView = Files.getFileAttributeView(f1, PosixFileAttributeView.class);
			if (posixView != null) {
				Files.setAttribute(a, "posix:permissions", MorePosixFilePermissions.fromFileMode(0777));
				Files.setAttribute(b, "posix:permissions", MorePosixFilePermissions.fromFileMode(0660));
				Files.setAttribute(c, "posix:permissions", MorePosixFilePermissions.fromFileMode(0744));
				Files.setAttribute(f1, "posix:permissions", MorePosixFilePermissions.fromFileMode(0500));
				Files.setAttribute(f2, "posix:permissions", MorePosixFilePermissions.fromFileMode(0777));
			}
			
			assertEquals(5, Zips.packZip(fs.getPath("fileperm"), fs.getPath("fileperm.zip"), false));
			assertEquals(5, Zips.unpackZip(fs.getPath("fileperm.zip"), fs.getPath("unzipFolder")));
			if (posixView != null) {
				assertEquals("777", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "a")))));
				assertEquals("660", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "b")))));
				assertEquals("744", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "c")))));
				assertEquals("500", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "f1")))));
				assertEquals("777", Long.toOctalString(MorePosixFilePermissions.toFileMode(Files.getPosixFilePermissions(fs.getPath("unzipFolder", "f2")))));
			}
		}
	}

	@Theory
	public void testPackTwoFiles(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			Path path1 = createLoremIpsumFile(fs.getPath("folder", "Test1.java"), 3);
			Path path2 = createLoremIpsumFile(fs.getPath("folder", "Test2.java"), 10);
			Path zip = fs.getPath("testPackTwoFiles.zip");
			assertEquals(2, Zips.packZip(path1.getParent(), zip, false));
			
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
				checkNextEntry(zis, path1, "Test1.java");
				checkNextEntry(zis, path2, "Test2.java");
				assertNull(zis.getNextEntry());
			}
		}
	}
	
	@Theory
	public void testPackWithFolders(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			Path path1 = createLoremIpsumFile(fs.getPath("folder", "t1", "Test1.java"), 3);
			Path path2 = createLoremIpsumFile(fs.getPath("folder", "t2", "t3", "Test2.java"), 10);
			Path zip = fs.getPath("testPackWithFolders.zip");
			assertEquals(5, Zips.packZip(path1.getParent().getParent(), zip, false));
			
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
				checkNextEntry(zis, path1.getParent(), "t1/");
				checkNextEntry(zis, path1, "t1/Test1.java");
				checkNextEntry(zis, path2.getParent(), "t2/");
				checkNextEntry(zis, path2.getParent().getParent(), "t2/t3/");
				checkNextEntry(zis, path2, "t2/t3/Test2.java");
				assertNull(zis.getNextEntry());
			}
		}
	}

	@Test
	public void testPackLink() throws IOException {
		Configuration conf = Configuration.unix().toBuilder().setAttributeViews("basic", "owner", "unix", "posix").build();
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			Path path1 = createLoremIpsumFile(fs.getPath("folder", "t1", "Test1.java"), 3);
			Path path2 = createLoremIpsumFile(fs.getPath("folder", "t2", "t3", "Test2.java"), 10);
			Path path3 = Files.createSymbolicLink(fs.getPath("folder/link1"), fs.getPath("t1/Test1.java"));
			Path path4 = Files.createSymbolicLink(fs.getPath("folder/t2/link2"), fs.getPath("t3"));
			Path zip = fs.getPath("testPackWithFolders.zip");
			assertEquals(7, Zips.packZip(fs.getPath("folder"), zip, false));
			
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
				checkNextEntry(zis, path3, "t1/Test1.java");
				checkNextEntry(zis, path1.getParent(), "t1/");
				checkNextEntry(zis, path1, "t1/Test1.java");
				checkNextEntry(zis, path2.getParent(), "t2/");
				checkNextEntry(zis, path4, "t3");
				checkNextEntry(zis, path2.getParent().getParent(), "t2/t3/");
				checkNextEntry(zis, path2, "t2/t3/Test2.java");
				assertNull(zis.getNextEntry());
			}
		}
	}

	@Test
	public void testPackLinkAbsolute() throws IOException {
		Configuration conf = Configuration.unix().toBuilder().setAttributeViews("basic", "owner", "unix", "posix").build();
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {
			Path path1 = createLoremIpsumFile(fs.getPath("/folder", "t1", "Test1.java"), 3);
			Path path2 = createLoremIpsumFile(fs.getPath("/folder", "t2", "t3", "Test2.java"), 10);
			Path path3 = Files.createSymbolicLink(fs.getPath("/folder/link1"), fs.getPath("t1/Test1.java"));
			Path path4 = Files.createSymbolicLink(fs.getPath("/folder/t2/link2"), fs.getPath("t3"));
			Path zip = fs.getPath("testPackWithFolders.zip");
			assertEquals(7, Zips.packZip(fs.getPath("/folder"), zip, false));

			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
				checkNextEntry(zis, path3, "t1/Test1.java");
				checkNextEntry(zis, path1.getParent(), "t1/");
				checkNextEntry(zis, path1, "t1/Test1.java");
				checkNextEntry(zis, path2.getParent(), "t2/");
				checkNextEntry(zis, path4, "t3");
				checkNextEntry(zis, path2.getParent().getParent(), "t2/t3/");
				checkNextEntry(zis, path2, "t2/t3/Test2.java");
				assertNull(zis.getNextEntry());
			}
		}
	}
	
	@Theory
	public void testPackWithFoldersPreservingRoot(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			Path path1 = createLoremIpsumFile(fs.getPath("folder", "t1", "Test1.java"), 3);
			Path path2 = createLoremIpsumFile(fs.getPath("folder", "t2", "t3", "Test2.java"), 10);
			Path zip = fs.getPath("testPackWithFoldersPreservingRoot.zip");
			assertEquals(6, Zips.packZip(path1.getParent().getParent(), zip, true));
			
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
				checkNextEntry(zis, path1.getParent().getParent(), "folder/");
				checkNextEntry(zis, path1.getParent(), "folder/t1/");
				checkNextEntry(zis, path1, "folder/t1/Test1.java");
				checkNextEntry(zis, path2.getParent(), "folder/t2/");
				checkNextEntry(zis, path2.getParent().getParent(), "folder/t2/t3/");
				checkNextEntry(zis, path2, "folder/t2/t3/Test2.java");
				assertNull(zis.getNextEntry());
			}
		}
	}
	
	@Test
	public void testPackAbsolutePath_unix() throws IOException {
		testPackAbsolutePath(Jimfs.newFileSystem(Configuration.unix()), "/");
	}
	
	@Test
	public void testPackAbsolutePath_osx() throws IOException {
		testPackAbsolutePath(Jimfs.newFileSystem(Configuration.osX()), "/");
	}
	
	@Test
	public void testPackAbsolutePath_win() throws IOException {
		testPackAbsolutePath(Jimfs.newFileSystem(Configuration.windows()), "C:\\");
	}
	
	private static void testPackAbsolutePath(FileSystem fs, String root) throws IOException {
		Path path1 = createLoremIpsumFile(fs.getPath(root, "tmp", "folder", "t1", "Test1.java"), 3);
		Path path2 = createLoremIpsumFile(fs.getPath(root, "tmp", "folder", "t2", "t3", "Test2.java"), 10);
		Path zip = fs.getPath("testPackAbsolutePath.zip");
		assertEquals(6, Zips.packZip(fs.getPath(root + "tmp/folder"), zip, true));
		
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
			checkNextEntry(zis, path1.getParent().getParent(), "folder/");
			checkNextEntry(zis, path1.getParent(), "folder/t1/");
			checkNextEntry(zis, path1, "folder/t1/Test1.java");
			checkNextEntry(zis, path2.getParent(), "folder/t2/");
			checkNextEntry(zis, path2.getParent().getParent(), "folder/t2/t3/");
			checkNextEntry(zis, path2, "folder/t2/t3/Test2.java");
			assertNull(zis.getNextEntry());
		}
	}
	
	@Theory
	public void testUnpack(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			try (InputStream is = this.getClass().getResource("/folder.zip").openStream()) {
				Files.copy(is, fs.getPath("folder.zip"));
			}
			assertEquals(17, Zips.unpackZip(fs.getPath("folder.zip"), fs.getPath("unzipFolder")));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t1/Test1.java")));
			assertTrue(Files.size(fs.getPath("unzipFolder/folder/t1/Test1.java")) > 0);
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")));
			assertTrue(Files.size(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")) > 0);
		}
	}

	@Theory
	public void testUnpackZipWithLinks(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			try (InputStream is = this.getClass().getResource("/withsymlinks.zip").openStream()) {
				Files.copy(is, fs.getPath("withsymlinks.zip"));
			}
			try {
				int unpackNum = Zips.unpackZip(fs.getPath("withsymlinks.zip"), fs.getPath("unzipFolder"));
				assertEquals(12, unpackNum);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			assertTrue(Files.exists(fs.getPath("unzipFolder/file1")));
			assertTrue(Files.exists(fs.getPath("unzipFolder/file2")));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder1")));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder1/subfile1")));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder1/subfile2")));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder2")));
			
			assertTrue(Files.exists(fs.getPath("unzipFolder/link1"), LinkOption.NOFOLLOW_LINKS));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder1/sublink1"), LinkOption.NOFOLLOW_LINKS));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder1/subfolderlink2"), LinkOption.NOFOLLOW_LINKS));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder1Link"), LinkOption.NOFOLLOW_LINKS));

			assertEquals(Files.readSymbolicLink(fs.getPath("unzipFolder/link1")), fs.getPath("file1"));
			assertEquals(Files.readSymbolicLink(fs.getPath("unzipFolder/folder1/sublink1")), fs.getPath("../file1"));
			assertEquals(Files.readSymbolicLink(fs.getPath("unzipFolder/folder1/subfolderlink2")), fs.getPath("../folder2"));
			assertEquals(Files.readSymbolicLink(fs.getPath("unzipFolder/folder1Link")), fs.getPath("folder1"));

			assertTrue(Files.isSameFile(resolveLink(fs.getPath("unzipFolder/link1")), fs.getPath("unzipFolder/file1")));
			assertTrue(Files.isSameFile(resolveLink(fs.getPath("unzipFolder/folder1/sublink1")), fs.getPath("unzipFolder/file1")));
			assertTrue(Files.isSameFile(resolveLink(fs.getPath("unzipFolder/folder1/subfolderlink2")), fs.getPath("unzipFolder/folder2")));
			assertTrue(Files.isSameFile(resolveLink(fs.getPath("unzipFolder/folder1Link")), fs.getPath("unzipFolder/folder1")));

			assertTrue(Files.isSymbolicLink(fs.getPath("unzipFolder/link1")));
			assertTrue(Files.isSymbolicLink(fs.getPath("unzipFolder/folder1/sublink1")));
			assertTrue(Files.isSymbolicLink(fs.getPath("unzipFolder/folder1/subfolderlink2")));
			assertTrue(Files.isSymbolicLink(fs.getPath("unzipFolder/folder1Link")));
		}
	}

	Path resolveLink(Path link) throws IOException {
		return link.getParent().resolve(Files.readSymbolicLink(link));
	}
	
	@Theory
	public void testUnpackRepackOverwriteZip(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {
			Path unpackPath = fs.getPath("unpack");
			Path zip = fs.getPath("file.zip");
			
			try (InputStream is = this.getClass().getResource("/folder.zip").openStream()) {
				Files.copy(is, zip);
			}
			
			checkZip(zip);
			assertEquals(Zips.unpackZip(zip, unpackPath), Zips.packZip(unpackPath, zip, false));
			checkZip(zip);
		}
	}
	
	@Theory
	public void testUnpackRepackOverwriteJar(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {
			Path unpackPath = fs.getPath("unpack");
			Path zip = fs.getPath("file.zip");
			
			try (InputStream is = this.getClass().getResource("/folder.zip").openStream()) {
				Files.copy(is, zip);
			}
			
			checkZip(zip);
			assertEquals(Zips.unpackJar(zip, unpackPath), Zips.packJar(unpackPath, zip, false));
			checkZip(zip);
		}
	}

	private static void checkZip(Path zip) throws IOException {
		try (ZipFile zipFile = new ZipFile(Files.newByteChannel(zip, StandardOpenOption.READ))) {
			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				assertFalse(entries.nextElement().getName().isEmpty());
			}
		}
	}
	
	@Theory
	public void testUnpackStream(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			try (InputStream zipStream = this.getClass().getResourceAsStream("/folder.zip")) {
				Files.copy(zipStream, fs.getPath("folder.zip"));
			}
			assertEquals(17, Zips.unpackZip(fs.getPath("folder.zip"), fs.getPath("unzipFolder")));
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t1/Test1.java")));
			assertTrue(Files.size(fs.getPath("unzipFolder/folder/t1/Test1.java")) > 0);
			assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")));
			assertTrue(Files.size(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")) > 0);
		}
	}
	
	@Theory
	public void testPackUnpackZip(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			Path path1 = createLoremIpsumFile(fs.getPath("folder", "t1", "Test1.java"), 3);
			Path path2 = createLoremIpsumFile(fs.getPath("folder", "t2", "t3", "Test2.java"), 10);
			Path zip = fs.getPath("testPackWithFolders.zip");
			assertEquals(5, Zips.packZip(path1.getParent().getParent(), zip, false));
			Path unpackFolder = Files.createDirectories(fs.getPath("unpackFolder"));
			assertEquals(5, Zips.unpackZip(zip, unpackFolder));
			assertTrue(Files.exists(fs.getPath("unpackFolder/t1/Test1.java")));
			assertTrue(Files.exists(fs.getPath("unpackFolder/t2/t3/Test2.java")));
			assertArrayEquals(Files.readAllBytes(path1), Files.readAllBytes(fs.getPath("unpackFolder/t1/Test1.java")));
			assertArrayEquals(Files.readAllBytes(path2), Files.readAllBytes(fs.getPath("unpackFolder/t2/t3/Test2.java")));
		}
	}

	@Theory
	public void testPackJar(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			createLoremIpsumFile(fs.getPath("c", "Afolder", "t1", "Test1.java"), 3);
			createLoremIpsumFile(fs.getPath("c", "folder", "t2", "t3", "Test2.java"), 10);
			SampleFilesGenerators.writeFile(fs.getPath("c", "META-INF", "MANIFEST.MF"), "Manifest-Version: 1.0\nCreated-By: CBI Project!");
			Path jarWithZip = fs.getPath("testPackWithFolders.zip.jar");
			Path jar = fs.getPath("testPackWithFolders.jar");
	
			assertEquals(9, Zips.packZip(fs.getPath("c"), jarWithZip, false));
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarWithZip))) {
				// with zip, the first entry is Afolder
				assertEquals("Afolder/", zis.getNextEntry().getName());
			}
			
			assertEquals(9, Zips.packJar(fs.getPath("c"), jar, false));
			try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
				// with Jar, the first entry must be META-INF/
				assertEquals("META-INF/", zis.getNextEntry().getName());
				assertEquals("META-INF/MANIFEST.MF", zis.getNextEntry().getName());
			}
		}
	}

	@Theory
	public void testPackUnPackJar(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf)) {	
			createLoremIpsumFile(fs.getPath("c", "Afolder", "t1", "Test1.java"), 3);
			createLoremIpsumFile(fs.getPath("c", "folder", "t2", "t3", "Test2.java"), 10);
			SampleFilesGenerators.writeFile(fs.getPath("c", "META-INF", "MANIFEST.MF"), "Manifest-Version: 1.0\nCreated-By: CBI Project!");
			Path jar = fs.getPath("testPackWithFolders.jar");
			assertEquals(Zips.packJar(fs.getPath("c"), jar, false), Zips.unpackJar(jar, Files.createDirectories(fs.getPath("o"))));
		}
	}

	@Theory
	public void testUnpackTarGz(Configuration conf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(conf); 
			TarArchiveInputStream is = new TarArchiveInputStream(new GZIPInputStream(this.getClass().getResource("/test.tar.gz").openStream()))) {
			assertEquals(8, Zips.unpack(is, fs.getPath("untarFolder")));
			assertTrue(Files.exists(fs.getPath("untarFolder", "folderSymlink"), LinkOption.NOFOLLOW_LINKS));
			assertTrue(Files.isSymbolicLink(fs.getPath("untarFolder", "folderSymlink")));
			assertTrue(Files.isSameFile(fs.getPath("untarFolder", "folder"), resolveLink(fs.getPath("untarFolder", "folderSymlink"))));
			assertTrue(Files.isSameFile(fs.getPath("untarFolder","folder","testFile"), resolveLink(fs.getPath("untarFolder","folder2","aSymlink"))));
			assertTrue(Files.isSameFile(fs.getPath("untarFolder", "folder2", "hardlinkToExe"), fs.getPath("untarFolder", "anExe")));
			Path exe = fs.getPath("untarFolder", "anExe");
			PosixFileAttributeView posixView = Files.getFileAttributeView(exe, PosixFileAttributeView.class);
			if (posixView != null) {
				Set<PosixFilePermission> permissions = posixView.readAttributes().permissions();
				assertEquals(6, permissions.size());
				assertTrue(permissions.contains(PosixFilePermission.OWNER_READ));
				assertTrue(permissions.contains(PosixFilePermission.OWNER_WRITE));
				assertTrue(permissions.contains(PosixFilePermission.OWNER_EXECUTE));
				assertTrue(permissions.contains(PosixFilePermission.GROUP_READ));
				assertTrue(permissions.contains(PosixFilePermission.GROUP_EXECUTE));
				assertTrue(permissions.contains(PosixFilePermission.OTHERS_READ));
			}
		}
	}

	private static void checkNextEntry(ZipInputStream zis, Path originalPath, String expectedEntryName) throws IOException {
		ZipEntry entry = zis.getNextEntry();
		assertNotNull(entry);
		if (!Files.isSymbolicLink(originalPath)) {
			assertEquals(expectedEntryName, entry.getName());
		} else {
			assertEquals(expectedEntryName, new String(ByteStreams.toByteArray(zis)));
		}
		
		if (!Files.isDirectory(originalPath) && !Files.isSymbolicLink(originalPath)) {
			assertArrayEquals(Files.readAllBytes(originalPath), ByteStreams.toByteArray(zis));
			assertEquals(Files.size(originalPath), entry.getSize());
		}

		// zip entry time encoding is lossy.
		long pathTime = dosToJavaTime(javaToDosTime(Files.getLastModifiedTime(originalPath, LinkOption.NOFOLLOW_LINKS).toMillis()));
		assertEquals(pathTime, entry.getTime());
	}

	private static Path createLoremIpsumFile(Path path, int repeat) throws IOException {
		SampleFilesGenerators.createLoremIpsumFile(path, repeat);
		return Files.setLastModifiedTime(path, FileTime.fromMillis(modTime));
	}
	
	/*
     * Converts DOS time to Java time (number of milliseconds since epoch).
     */
    @SuppressWarnings("deprecation")
	private static long dosToJavaTime(long dtime) {
        Date d = new Date((int)(((dtime >> 25) & 0x7f) + 80),
                          (int)(((dtime >> 21) & 0x0f) - 1),
                          (int)((dtime >> 16) & 0x1f),
                          (int)((dtime >> 11) & 0x1f),
                          (int)((dtime >> 5) & 0x3f),
                          (int)((dtime << 1) & 0x3e));
        return d.getTime();
    }

    /*
     * Converts Java time to DOS time.
     */
    @SuppressWarnings("deprecation")
	private static long javaToDosTime(long time) {
        Date d = new Date(time);
        int year = d.getYear() + 1900;
        if (year < 1980) {
            return (1 << 21) | (1 << 16);
        }
        return (year - 1980) << 25 | (d.getMonth() + 1) << 21 |
               d.getDate() << 16 | d.getHours() << 11 | d.getMinutes() << 5 |
               d.getSeconds() >> 1;
    }
}
