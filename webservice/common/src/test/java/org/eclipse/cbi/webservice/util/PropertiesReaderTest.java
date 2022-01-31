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
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.util;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.junit.Test;

import com.google.common.jimfs.Jimfs;

@SuppressWarnings("javadoc")
public class PropertiesReaderTest {

	@Test
	public void testCreate() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			Path path = SampleFilesGenerators.writeFile(fs.getPath("test.properties"), "a=b\nc=d X");
			PropertiesReader properties = PropertiesReader.create(path);
			assertEquals("b", properties.getString("a"));
			assertEquals("d X", properties.getString("c"));
			assertEquals("", properties.getString("d", ""));
		}
	}

	@Test
	public void testToMap() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			Path path = SampleFilesGenerators.writeFile(fs.getPath("test.properties"), "a=b\nc=d X");
			PropertiesReader properties = PropertiesReader.create(path);

			assertEquals("b", properties.toMap().get("a"));
			assertEquals("d X", properties.toMap().get("c"));
			assertEquals(null, properties.toMap().get("d"));
		}
	}

	@Test
	public void testGetRegularFile_resolve_relative_to_absolute() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			final Path relative = fs.getPath("relative");
			final Path absolute = fs.getPath("/absolute");

			assertNotEquals("Test is flawed!", relative.toAbsolutePath(), relative);

			Path propertiesFile = SampleFilesGenerators.writeFile(fs.getPath("test.properties"),format( "path1=%s\npath2=%s", relative, absolute));
			PropertiesReader properties = PropertiesReader.create(propertiesFile);

			assertEquals("relative", properties.getString("path1"));
			assertEquals("/absolute", properties.getString("path2"));

			assertEquals(relative, properties.getPath("path1"));
			assertEquals(absolute, properties.getPath("path2"));

			SampleFilesGenerators.createLoremIpsumFile(relative, 0);
			SampleFilesGenerators.createLoremIpsumFile(absolute, 0);

			assertEquals(relative.toAbsolutePath(), properties.getRegularFile("path1"));
			assertEquals(absolute.toAbsolutePath(), properties.getRegularFile("path2"));
		}
	}
}
