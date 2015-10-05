package org.eclipse.cbi.webservice.signing.macosx;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.webservice.util.PropertiesReader;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class CodesignerPropertiesTest {

	@Test(expected=IllegalStateException.class)
	public void testEmptyPropertiesGetCertificate() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(new Properties(), fs));
			propertiesReader.getCertificate();
		}
	}
	
	@Test(expected=IllegalStateException.class)
	public void testEmptyPropertiesGetKeychain() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(new Properties(), fs));
			propertiesReader.getKeychain();
		}
	}
	
	@Test(expected=IllegalStateException.class)
	public void testEmptyPropertiesGetKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(new Properties(), fs));
			propertiesReader.getKeychainPassword();
		}
	}
	
	@Test
	public void testGetCertificate() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(createTestProperties(), fs));
			assertEquals("Certificate Corporation, Inc.", propertiesReader.getCertificate());
		}
	}
	
	@Test
	public void testGetKeychain() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(createTestProperties(), fs));
			Path keychainPath = fs.getPath("/path/to/keychain");
			Files.createDirectories(keychainPath.normalize().getParent());
			Files.createFile(keychainPath);
			assertEquals(keychainPath, propertiesReader.getKeychain());
		}
	}
	
	@Test(expected=IllegalStateException.class)
	public void testGetNonExistingKeychain() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(createTestProperties(), fs));
			propertiesReader.getKeychain();
		}
	}
	
	@Test(expected=IllegalStateException.class)
	public void testNonExistingGetKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(createTestProperties(), fs));
			propertiesReader.getKeychainPassword();
		}
	}
	
	@Test
	public void testGetKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(createTestProperties(), fs));
			SampleFilesGenerators.writeFile(fs.getPath("/path", "to", "keychain", "password"), "the.password");
			assertEquals("the.password", propertiesReader.getKeychainPassword());
		}
	}
	
	@Test
	public void testGetEmptyKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(createTestProperties(), fs));
			SampleFilesGenerators.writeFile(fs.getPath("/path", "to", "keychain", "password"), "");
			assertEquals("", propertiesReader.getKeychainPassword());
		}
	}
	
	@Test
	public void testGetTrimmedKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			CodesignerProperties propertiesReader = new CodesignerProperties(new PropertiesReader(createTestProperties(), fs));
			SampleFilesGenerators.writeFile(fs.getPath("/path", "to", "keychain", "password"), "  password   ");
			assertEquals("password", propertiesReader.getKeychainPassword());
		}
	}
	
	private static Properties createTestProperties() {
		Properties properties = new Properties();
		properties.setProperty("macosx.certificate", "Certificate Corporation, Inc.");
		properties.setProperty("macosx.keychain.password", "/path/to/keychain/password");
		properties.setProperty("macosx.keychain", "/path/to/keychain");
		return properties;
	}
}
