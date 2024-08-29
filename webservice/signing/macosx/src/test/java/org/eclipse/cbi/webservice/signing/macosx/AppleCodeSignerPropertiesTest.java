package org.eclipse.cbi.webservice.signing.macosx;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.webservice.util.PropertiesReader;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class AppleCodeSignerPropertiesTest {

	@Test(expected=IllegalStateException.class)
	public void testEmptyPropertiesGetIdentityApplication() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(new Properties(), fs));
			propertiesReader.getIdentityApplication();
		}
	}

	@Test(expected=IllegalStateException.class)
	public void testEmptyPropertiesGetKeychain() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(new Properties(), fs));
			propertiesReader.getKeyChain();
		}
	}

	@Test(expected=IllegalStateException.class)
	public void testEmptyPropertiesGetKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(new Properties(), fs));
			propertiesReader.getKeyChainPassword();
		}
	}

	@Test
	public void testgetIdentityApplication() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(createTestProperties(), fs));
			assertEquals("Certificate Corporation, Inc.", propertiesReader.getIdentityApplication());
		}
	}

	@Test
	public void testGetKeychain() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(createTestProperties(), fs));
			Path keychainPath = fs.getPath("/path/to/keychain");
			Files.createDirectories(keychainPath.normalize().getParent());
			Files.createFile(keychainPath);
			assertEquals(keychainPath, propertiesReader.getKeyChain());
		}
	}

	@Test(expected=IllegalStateException.class)
	public void testGetNonExistingKeychain() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(createTestProperties(), fs));
			propertiesReader.getKeyChain();
		}
	}

	@Test(expected=IllegalStateException.class)
	public void testNonExistingGetKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(createTestProperties(), fs));
			propertiesReader.getKeyChainPassword();
		}
	}

	@Test
	public void testGetKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(createTestProperties(), fs));
			SampleFilesGenerators.writeFile(fs.getPath("/path", "to", "keychain", "password"), "the.password");
			assertEquals("the.password", propertiesReader.getKeyChainPassword());
		}
	}

	@Test
	public void testGetEmptyKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(createTestProperties(), fs));
			SampleFilesGenerators.writeFile(fs.getPath("/path", "to", "keychain", "password"), "");
			assertEquals("", propertiesReader.getKeyChainPassword());
		}
	}

	@Test
	public void testGetTrimmedKeychainPassword() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			AppleCodeSignerProperties propertiesReader = new AppleCodeSignerProperties(new PropertiesReader(createTestProperties(), fs));
			SampleFilesGenerators.writeFile(fs.getPath("/path", "to", "keychain", "password"), "  password   ");
			assertEquals("password", propertiesReader.getKeyChainPassword());
		}
	}

	private static Properties createTestProperties() {
		Properties properties = new Properties();
		properties.setProperty("macosx.identity.application", "Certificate Corporation, Inc.");
		properties.setProperty("macosx.keychain.password", "/path/to/keychain/password");
		properties.setProperty("macosx.keychain", "/path/to/keychain");
		return properties;
	}
}
