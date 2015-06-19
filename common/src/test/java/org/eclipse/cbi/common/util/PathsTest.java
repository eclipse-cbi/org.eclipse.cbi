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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.cbi.common.util.Paths;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import static org.junit.Assert.*;

@RunWith(Theories.class)
public class PathsTest {

	@DataPoints
	public static Configuration[] fsConfigurations() {
		return new Configuration[] {
				Configuration.unix(),
				Configuration.osX(),
				Configuration.windows(),
		};
	}
	
	@Test(expected=NullPointerException.class)
	public void testDeleteNullPath() throws IOException {
		Paths.delete(null);
	}
	
	@Test
	public void testQDeleteNullPath() {
		Paths.deleteQuietly(null);
	}
	
	@Theory
	@Test(expected=IOException.class)
	public void testDeleteNonExistingFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {	
			Path path = fs.getPath("test");
			Paths.delete(path);
		}
	}
	
	@Theory
	public void testQDeleteNonExistingFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {	
			Path path = fs.getPath("test");
			Paths.deleteQuietly(path);
			assertTrue(!Files.exists(path));
		}
	}
	
	@Theory
	public void testDeleteSimpleFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {	
			Path path = fs.getPath("test");
			Files.createFile(path);
			Paths.delete(path);
			assertTrue(!Files.exists(path));
		}
	}
	
	@Theory
	public void testQDeleteSimpleFile(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {	
			Path path = fs.getPath("test");
			Files.createFile(path);
			Paths.deleteQuietly(path);
			assertTrue(!Files.exists(path));
		}
	}
	
	@Theory
	public void testDeleteDirectory(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {	
			Path path = fs.getPath("test");
			Files.createDirectory(path);
			Paths.delete(path);
			assertTrue(!Files.exists(path));
		}
	}
	
	@Theory
	public void testQDeleteDirectory(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {	
			Path path = fs.getPath("test");
			Files.createDirectory(path);
			Paths.deleteQuietly(path);
			assertTrue(!Files.exists(path));
		}
	}
	
	@Theory
	public void testDeleteDirectories(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {	
			Path path = sampleFileHierarchy(fs);
			Paths.delete(path);
			assertTrue(!Files.exists(path));
		}
	}

	@Theory
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
