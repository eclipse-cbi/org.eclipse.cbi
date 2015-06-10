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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.eclipse.cbi.common.TestUtils;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

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
				Configuration.unix(),
				Configuration.osX(),
				Configuration.windows(),
		};
	}
	
	@Theory
	public void testPackSingleFile(Configuration conf) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(conf);
		Path path = createLoremIpsumFile(fs.getPath("workDir/Test.java"), 3);
		Path zip = fs.getPath("testPackSingleFile.zip");
		assertEquals(1, Zips.packZip(path, zip, false));

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
			checkNextEntry(zis, path, "Test.java");
			assertNull(zis.getNextEntry());
		}
	}

	@Theory
	public void testPackTwoFiles(Configuration conf) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(conf);
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
	
	@Theory
	public void testPackWithFolders(Configuration conf) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(conf);
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
	
	@Theory
	public void testPackWithFoldersPreservingRoot(Configuration conf) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(conf);
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
	
	public void testPackAbsolutePath(FileSystem fs, String root) throws IOException {
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
		FileSystem fs = Jimfs.newFileSystem(conf);
		try (InputStream is = this.getClass().getResource("/folder.zip").openStream()) {
			Files.copy(is, fs.getPath("folder.zip"));
		}
		assertEquals(17, Zips.unpackZip(fs.getPath("folder.zip"), fs.getPath("unzipFolder")));
		assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t1/Test1.java")));
		assertTrue(Files.size(fs.getPath("unzipFolder/folder/t1/Test1.java")) > 0);
		assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")));
		assertTrue(Files.size(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")) > 0);
	}
	
	@Theory
	public void testUnpackStream(Configuration conf) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(conf);
		try (ZipInputStream is = new ZipInputStream(this.getClass().getResource("/folder.zip").openStream())) {
			assertEquals(17, Zips.unpack(is, fs.getPath("unzipFolder")));
		}
		assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t1/Test1.java")));
		assertTrue(Files.size(fs.getPath("unzipFolder/folder/t1/Test1.java")) > 0);
		assertTrue(Files.exists(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")));
		assertTrue(Files.size(fs.getPath("unzipFolder/folder/t2/t3/Test2.java")) > 0);
	}
	
	@Theory
	public void testPackUnpack(Configuration conf) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(conf);
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
	
	@Theory
	public void testUnpackTarGz(Configuration conf) throws IOException {
		FileSystem fs = Jimfs.newFileSystem(conf);
		try (TarArchiveInputStream is = new TarArchiveInputStream(new GZIPInputStream(this.getClass().getResource("/test.tar.gz").openStream()))) {
			assertEquals(8, Zips.unpack(is, fs.getPath("untarFolder")));
			assertTrue(Files.isSymbolicLink(fs.getPath("untarFolder", "folderSymlink")));
			assertTrue(Files.isSameFile(fs.getPath("untarFolder", "folder"), Files.readSymbolicLink(fs.getPath("untarFolder", "folderSymlink"))));
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
		assertEquals(expectedEntryName, entry.getName());
		
		if (!Files.isDirectory(originalPath)) {
			assertArrayEquals(Files.readAllBytes(originalPath), TestUtils.readAllBytes(zis));
			assertEquals(Files.size(originalPath), entry.getSize());
		}
		
		// zip entry time encoding is lossy.
		long pathTime = dosToJavaTime(javaToDosTime(Files.getLastModifiedTime(originalPath).toMillis()));
		assertEquals(pathTime, entry.getTime());
	}

	private static Path createLoremIpsumFile(Path path, int repeat) throws IOException {
		TestUtils.createLoremIpsumFile(path, repeat);
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
