package org.eclipse.cbi.webservice.signing.jar;

import static org.eclipse.cbi.webservice.signing.jar.TestResigningStrategy.copyResource;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class TestJarSigner {

	@Test
	public void testIsAlreadySigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			assertFalse(JarSigner.isAlreadySigned(unsigned));
			
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			assertTrue(JarSigner.isAlreadySigned(signed_sha1));
			
			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			assertTrue(JarSigner.isAlreadySigned(signed_sha256));
			
			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			assertTrue(JarSigner.isAlreadySigned(signed_sha1_sha256));
		}
	}
}
