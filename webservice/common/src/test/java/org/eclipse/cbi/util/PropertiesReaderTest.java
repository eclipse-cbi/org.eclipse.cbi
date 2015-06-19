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
package org.eclipse.cbi.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.junit.Test;

import com.google.common.jimfs.Jimfs;

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
}
