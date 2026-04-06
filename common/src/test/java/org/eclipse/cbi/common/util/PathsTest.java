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
package org.eclipse.cbi.common.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class PathsTest {

	public static Configuration[] fsConfigurations() {
		return new Configuration[] { Configuration.unix(), Configuration.osX(), Configuration.windows(), };
	}

	@Test
	public void testDeleteNullPath() {
		assertThrows(NullPointerException.class, () -> Paths.delete(null));
	}

	@Test
	public void testQDeleteNullPath() {
		Paths.deleteQuietly(null);
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testDeleteNonExistingFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = fs.getPath("test");
			assertThrows(IOException.class, () -> Paths.delete(path));
		}
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testQDeleteNonExistingFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = fs.getPath("test");
			Paths.deleteQuietly(path);
			assertTrue(!Files.exists(path));
		}
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testDeleteSimpleFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = fs.getPath("test");
			Files.createFile(path);
			Paths.delete(path);
			assertTrue(!Files.exists(path));
		}
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testQDeleteSimpleFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = fs.getPath("test");
			Files.createFile(path);
			Paths.deleteQuietly(path);
			assertTrue(!Files.exists(path));
		}
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testDeleteDirectory(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = fs.getPath("test");
			Files.createDirectory(path);
			Paths.delete(path);
			assertTrue(!Files.exists(path));
		}
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testQDeleteDirectory(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = fs.getPath("test");
			Files.createDirectory(path);
			Paths.deleteQuietly(path);
			assertTrue(!Files.exists(path));
		}
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testDeleteDirectories(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = sampleFileHierarchy(fs);
			Paths.delete(path);
			assertTrue(!Files.exists(path));
		}
	}

	@ParameterizedTest
	@MethodSource("fsConfigurations")
	public void testQDeleteDirectories(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path path = sampleFileHierarchy(fs);
			Paths.deleteQuietly(path);
			assertTrue(!Files.exists(path));
		}
	}

	private static Path sampleFileHierarchy(FileSystem fs) throws IOException {
		Path path = fs.getPath("root");
		Files.createDirectories(path.resolve("sub1/subsub1"));
		Files.createFile(path.resolve("sub1/subsub1/file1"));
		Files.createFile(path.resolve("sub1/subsub1/file2"));
		Files.createDirectories(path.resolve("sub1/subsub2"));
		Files.createFile(path.resolve("sub1/subsub2/file1"));
		Files.createDirectories(path.resolve("sub2/subsub1/subsubsub1"));
		Files.createFile(path.resolve("sub2/file1"));
		Files.createFile(path.resolve("sub2/file2"));
		Files.createFile(path.resolve("sub2/subsub1/file1"));
		Files.createFile(path.resolve("sub2/subsub1/file2"));
		Files.createDirectories(path.resolve("sub2/subsub1/subsubsub1"));
		Files.createFile(path.resolve("sub2/subsub1/subsubsub1/file1"));
		Files.createFile(path.resolve("sub2/subsub1/subsubsub1/file2"));
		Files.createDirectories(path.resolve("sub2/subsub2/subsubsub1"));
		Files.createFile(path.resolve("sub2/subsub2/subsubsub1/file2"));
		Files.createFile(path.resolve("sub2/subsub2/subsubsub1/file1"));
		Files.createDirectories(path.resolve("sub2/subsub1/subsubsub1"));
		Files.createDirectories(path.resolve("sub3/subsub1/subsubsub1"));
		Files.createFile(path.resolve("sub3/subsub1/subsubsub1/file1"));
		Files.createFile(path.resolve("sub3/subsub1/subsubsub1/file2"));
		return path;
	}
}
