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
package org.eclipse.cbi.webservice.server;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.eclipse.cbi.webservice.util.PropertiesReader;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import static org.junit.Assert.*;

@SuppressWarnings("javadoc")
public class EmbeddedServerPropertiesTest {

	@Test
	public void testEmptyPropertiesGetAccessLog() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(new Properties(), fs));
			assertNull(propertiesReader.getAccessLogFile());
		}
	}

	@Test(expected=IllegalStateException.class)
	public void testEmptyPropertiesGetServicePathSpec() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(new Properties(), fs));
			propertiesReader.getServicePathSpec();
		}
	}

	@Test
	public void testEmptyPropertiesGetTempFolder() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(new Properties(), fs));
			assertEquals(fs.getPath(System.getProperty("java.io.tmpdir")), propertiesReader.getTempFolder());
		}
	}

	@Test
	public void testEmptyPropertiesGetServerPort() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(new Properties(), fs));
			assertEquals(8080, propertiesReader.getServerPort());
		}
	}

	@Test
	public void testGetAccessLog() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(createTestProperties(), fs));
			Path accessLog = fs.getPath("/var/log/access.log");
			assertEquals(accessLog, propertiesReader.getAccessLogFile());
			assertTrue(Files.exists(accessLog.getParent()));
		}
	}

	@Test
	public void testGetServicePathSpec() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(createTestProperties(), fs));
			assertEquals("service/serve", propertiesReader.getServicePathSpec());
		}
	}

	@Test
	public void testGetTempFolder() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(createTestProperties(), fs));
			Path tmpFolder = fs.getPath("/tmp/X");
			assertEquals(tmpFolder, propertiesReader.getTempFolder());
			assertTrue(Files.exists(tmpFolder));
		}
	}

	@Test
	public void testGetServerPort() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(createTestProperties(), fs));
			assertEquals(1025, propertiesReader.getServerPort());
		}
	}

	@Test
	public void testGetLog4jConfiguration() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			EmbeddedServerConfiguration propertiesReader = new EmbeddedServerProperties(new PropertiesReader(createTestProperties(), fs));
			Properties log4jProperties = propertiesReader.getLog4jProperties();
			assertEquals(2, log4jProperties.size());
			assertEquals("INFO", log4jProperties.getProperty("log4j.rootLogger"));
			assertEquals("10", log4jProperties.getProperty("log4j.appender.file.MaxBackupIndex"));
		}
	}

	private static Properties createTestProperties() {
		Properties properties = new Properties();
		properties.setProperty("server.access.log", "/var/log/access.log   ");
		properties.setProperty("server.temp.folder", "/tmp/X");
		properties.setProperty("server.port", "1025");
		properties.setProperty("server.service.pathspec", "service/serve");
		properties.setProperty("log4j.rootLogger", "INFO");
		properties.setProperty("log4j.appender.file.MaxBackupIndex", "10");
		properties.setProperty("log4jsubSection", "None");
		return properties;
	}
}
