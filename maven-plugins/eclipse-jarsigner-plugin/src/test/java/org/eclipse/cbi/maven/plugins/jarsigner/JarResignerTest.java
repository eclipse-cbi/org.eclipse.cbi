package org.eclipse.cbi.maven.plugins.jarsigner;

import static org.eclipse.cbi.maven.plugins.jarsigner.RemoteJarSignerTest.dummyOptions;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.maven.plugins.jarsigner.JarSigner.Options;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class JarResignerTest {

	@Test
	public void testIsAlreadySigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			assertFalse(JarResigner.isAlreadySigned(unsigned));
			
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			assertTrue(JarResigner.isAlreadySigned(signed_sha1));
			
			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			assertTrue(JarResigner.isAlreadySigned(signed_sha256));
			
			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			assertTrue(JarResigner.isAlreadySigned(signed_sha1_sha256));
		}
	}
	
	@Test(expected = IllegalStateException.class)
	public void testThrowException() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path jar = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			((JarResigner) JarResigner.throwException(new DummyJarSigner())).resign(jar, dummyOptions());
		}
	}
	
	@Test
	public void testDoNotResign() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path jar = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			byte[] before = Files.readAllBytes(jar);
			((JarResigner) JarResigner.doNotResign(new DummyJarSigner())).resign(jar, dummyOptions());
			byte[] after = Files.readAllBytes(jar);
			assertArrayEquals(before, after);
		}
	}
	
	@Test
	public void testGetAllUsedDigestAlgorithm() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			assertEquals(EnumSet.noneOf(MessageDigestAlgorithm.class), JarResigner.getAllUsedDigestAlgorithm(unsigned));
			
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			assertEquals(EnumSet.of(MessageDigestAlgorithm.SHA_1), JarResigner.getAllUsedDigestAlgorithm(signed_sha1));
			
			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			assertEquals(EnumSet.of(MessageDigestAlgorithm.SHA_256), JarResigner.getAllUsedDigestAlgorithm(signed_sha256));
			
			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			assertEquals(EnumSet.of(MessageDigestAlgorithm.SHA_1, MessageDigestAlgorithm.SHA_256), JarResigner.getAllUsedDigestAlgorithm(signed_sha1_sha256));
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testGetDigestAlgorithmToReuseEmpty() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			JarResigner.getDigestAlgorithmToReuse(unsigned);
		}
	}
	
	@Test
	public void testGetDigestAlgorithmToReuseNormal() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			assertEquals(MessageDigestAlgorithm.SHA_1, JarResigner.getDigestAlgorithmToReuse(signed_sha1));
			
			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			assertEquals(MessageDigestAlgorithm.SHA_256, JarResigner.getDigestAlgorithmToReuse(signed_sha256));	
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testGetDigestAlgorithmToReuseTooMany() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			JarResigner.getDigestAlgorithmToReuse(signed_sha1_sha256);
		}
	}
	
	@Test
	public void testOverwriteStrategyOnUnsignerd() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.overwrite(jarSigner);
			jarResigner.resign(unsigned, options(MessageDigestAlgorithm.MD5));
			
			assertTrue(noSignatureFiles(unsigned));
			assertEquals(MessageDigestAlgorithm.MD5, jarSigner.latestRequestedDigestAlgorithm());
		}
	}
	
	@Test
	public void testOverwriteStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.overwrite(jarSigner);
			
			jarResigner.resign(signed_sha1, options(MessageDigestAlgorithm.MD5));
			
			assertTrue(noSignatureFiles(signed_sha1));
			assertEquals(MessageDigestAlgorithm.MD5, jarSigner.latestRequestedDigestAlgorithm());
		}
	}
	
	@Test
	public void testOverwriteStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1_256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.overwrite(jarSigner);
			
			jarResigner.resign(signed_sha1_256, options(MessageDigestAlgorithm.MD5));
			
			assertTrue(noSignatureFiles(signed_sha1_256));
			assertEquals(MessageDigestAlgorithm.MD5, jarSigner.latestRequestedDigestAlgorithm());
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOverwriteWithSameDigestAlgStrategyOnUnsignerd() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			JarResigner jarResigner = (JarResigner)JarResigner.overwriteWithSameDigestAlgorithm(new DummyJarSigner());
			jarResigner.resign(unsigned, options(MessageDigestAlgorithm.MD5));
		}
	}
	
	@Test
	public void testOverwriteWithSameDigestAlgStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.overwriteWithSameDigestAlgorithm(jarSigner);
			
			jarResigner.resign(signed_sha1, options(MessageDigestAlgorithm.MD5));
			
			assertTrue(noSignatureFiles(signed_sha1));
			assertEquals(MessageDigestAlgorithm.SHA_1, jarSigner.latestRequestedDigestAlgorithm());
		}
	}
	
	@Test
	public void testOverwriteWithSameDigestAlgStrategyOnSingleSigned2() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.overwriteWithSameDigestAlgorithm(jarSigner);
			
			jarResigner.resign(signed_sha256, options(MessageDigestAlgorithm.MD5));
			
			assertTrue(noSignatureFiles(signed_sha256));
			assertEquals(MessageDigestAlgorithm.SHA_256, jarSigner.latestRequestedDigestAlgorithm());
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOverwriteWithSameDigestAlgStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			JarResigner jarResigner = (JarResigner)JarResigner.overwriteWithSameDigestAlgorithm(new DummyJarSigner());
			
			jarResigner.resign(signed_sha1_sha256, options(MessageDigestAlgorithm.MD5));
		}
	}
	
	@Test
	public void testResignStrategyOnUnsigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.resign(jarSigner);
			jarResigner.resign(unsigned, options(MessageDigestAlgorithm.MD5));
			assertEquals(MessageDigestAlgorithm.MD5, jarSigner.latestRequestedDigestAlgorithm());
		}
	}
	
	@Test
	public void testResignStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.resign(jarSigner);
			jarResigner.resign(signed, options(MessageDigestAlgorithm.MD5));
			assertEquals(MessageDigestAlgorithm.MD5, jarSigner.latestRequestedDigestAlgorithm());
		}
	}
	
	@Test
	public void testResignStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.resign(jarSigner);
			jarResigner.resign(signed, options(MessageDigestAlgorithm.MD5));
			assertEquals(MessageDigestAlgorithm.MD5, jarSigner.latestRequestedDigestAlgorithm());
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResignWithSameDigestAlgStrategyOnUnsigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			JarResigner jarResigner = (JarResigner)JarResigner.resignWithSameDigestAlgorithm(new DummyJarSigner());
			jarResigner.resign(unsigned, options(MessageDigestAlgorithm.MD5));
		}
	}
	
	@Test
	public void testResignWithSameDigestAlgStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.resignWithSameDigestAlgorithm(jarSigner);
			jarResigner.resign(signed, options(MessageDigestAlgorithm.MD5));
			assertEquals(MessageDigestAlgorithm.SHA_1, jarSigner.latestRequestedDigestAlgorithm());
			assertFalse(noSignatureFiles(signed));
		}
	}
	
	@Test
	public void testResignWithSameDigestAlgStrategyOnSingleSigned2() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			DummyJarSigner jarSigner = new DummyJarSigner();
			JarResigner jarResigner = (JarResigner)JarResigner.resignWithSameDigestAlgorithm(jarSigner);
			jarResigner.resign(signed, options(MessageDigestAlgorithm.MD5));
			assertEquals(MessageDigestAlgorithm.SHA_256, jarSigner.latestRequestedDigestAlgorithm());
			assertFalse(noSignatureFiles(signed));
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testResignWithSameDigestAlgStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			JarResigner jarResigner = (JarResigner)JarResigner.resignWithSameDigestAlgorithm(new DummyJarSigner());
			jarResigner.resign(signed, options(MessageDigestAlgorithm.MD5));
		}
	}
	
	private boolean noSignatureFiles(Path jar) throws IOException {
		try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
			JarEntry jarEntry = jis.getNextJarEntry();
			while (jarEntry != null) {
				if (jarEntry.getName().startsWith("META-INF") && 
						(jarEntry.getName().endsWith(".SF") || jarEntry.getName().endsWith(".RSA") || jarEntry.getName().endsWith(".DSA") || jarEntry.getName().endsWith(".EC"))) {
					return false;
				}
				jarEntry = jis.getNextJarEntry();
			}
		}
		return true;
	}
	
	static Options options(MessageDigestAlgorithm digestAlgorithm) {
		return Options.builder().digestAlgorithm(digestAlgorithm).build();
	}

	static Path copyResource(String resourcePath, Path target) throws IOException {
		URL resource = JarResignerTest.class.getResource(resourcePath);
		try (InputStream is = new BufferedInputStream(resource.openStream())) {
			Files.copy(is, target);
			return target;
		}
	}
	
	private static class DummyJarSigner implements JarSigner {

		private MessageDigestAlgorithm latestRequestedDigestAlgorithm;

		@Override
		public int sign(Path jarfile, Options options) throws IOException {
			latestRequestedDigestAlgorithm = options.digestAlgorithm();
			return 1;
		}
		
		public MessageDigestAlgorithm latestRequestedDigestAlgorithm() {
			return latestRequestedDigestAlgorithm;
		}
		
	}
}
