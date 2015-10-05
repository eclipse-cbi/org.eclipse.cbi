package org.eclipse.cbi.webservice.signing.jar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class TestResigningStrategy {

	@Test(expected = IllegalStateException.class)
	public void testThrowException() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path jar = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			ResigningStrategy.throwException().resignJar(jar);
		}
	}
	
	@Test
	public void testDoNotResign() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path jar = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			byte[] before = Files.readAllBytes(jar);
			ResigningStrategy.doNotResign().resignJar(jar);
			byte[] after = Files.readAllBytes(jar);
			assertArrayEquals(before, after);
		}
	}
	
	@Test
	public void testGetAllUsedDigestAlgorithm() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			assertEquals(EnumSet.noneOf(MessageDigestAlgorithm.class), ResigningStrategy.getAllUsedDigestAlgorithm(unsigned));
			
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			assertEquals(EnumSet.of(MessageDigestAlgorithm.SHA_1), ResigningStrategy.getAllUsedDigestAlgorithm(signed_sha1));
			
			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			assertEquals(EnumSet.of(MessageDigestAlgorithm.SHA_256), ResigningStrategy.getAllUsedDigestAlgorithm(signed_sha256));
			
			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			assertEquals(EnumSet.of(MessageDigestAlgorithm.SHA_1, MessageDigestAlgorithm.SHA_256), ResigningStrategy.getAllUsedDigestAlgorithm(signed_sha1_sha256));
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testGetDigestAlgorithmToReuseEmpty() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			ResigningStrategy.getDigestAlgorithmToReuse(unsigned);
		}
	}
	
	@Test
	public void testGetDigestAlgorithmToReuseNormal() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			assertEquals(MessageDigestAlgorithm.SHA_1, ResigningStrategy.getDigestAlgorithmToReuse(signed_sha1));
			
			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			assertEquals(MessageDigestAlgorithm.SHA_256, ResigningStrategy.getDigestAlgorithmToReuse(signed_sha256));	
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testGetDigestAlgorithmToReuseTooMany() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			ResigningStrategy.getDigestAlgorithmToReuse(signed_sha1_sha256);
		}
	}
	
	@Test
	public void testOverwriteStrategyOnUnsignerd() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path tempFolder = Files.createDirectories(fs.getPath("/tmp"));

			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.overwrite(dummyJarSigner(fs, executor), MessageDigestAlgorithm.MD5, tempFolder);
			strategy.resignJar(unsigned);
			
			assertTrue(noSignatureFiles(unsigned));
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.MD5.standardName()));
		}
	}
	
	@Test
	public void testOverwriteStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path tempFolder = Files.createDirectories(fs.getPath("/tmp"));

			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.overwrite(dummyJarSigner(fs, executor), MessageDigestAlgorithm.MD5, tempFolder);
			
			strategy.resignJar(signed_sha1);
			
			assertTrue(noSignatureFiles(signed_sha1));
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.MD5.standardName()));
		}
	}
	
	@Test
	public void testOverwriteStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path tempFolder = Files.createDirectories(fs.getPath("/tmp"));

			Path signed_sha1_256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.overwrite(dummyJarSigner(fs, executor), MessageDigestAlgorithm.MD5, tempFolder);
			
			strategy.resignJar(signed_sha1_256);
			
			assertTrue(noSignatureFiles(signed_sha1_256));
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.MD5.standardName()));
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOverwriteWithSameDigestAlgStrategyOnUnsignerd() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path tempFolder = Files.createDirectories(fs.getPath("/tmp"));

			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.overwriteWithSameDigestAlgorithm(dummyJarSigner(fs, executor), tempFolder);
			strategy.resignJar(unsigned);
		}
	}
	
	@Test
	public void testOverwriteWithSameDigestAlgStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path tempFolder = Files.createDirectories(fs.getPath("/tmp"));

			Path signed_sha1 = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.overwriteWithSameDigestAlgorithm(dummyJarSigner(fs, executor), tempFolder);
			
			strategy.resignJar(signed_sha1);
			
			assertTrue(noSignatureFiles(signed_sha1));
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.SHA_1.standardName()));
		}
	}
	
	@Test
	public void testOverwriteWithSameDigestAlgStrategyOnSingleSigned2() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path tempFolder = Files.createDirectories(fs.getPath("/tmp"));

			Path signed_sha256 = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.overwriteWithSameDigestAlgorithm(dummyJarSigner(fs, executor), tempFolder);
			
			strategy.resignJar(signed_sha256);
			
			assertTrue(noSignatureFiles(signed_sha256));
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.SHA_256.standardName()));
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOverwriteWithSameDigestAlgStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path tempFolder = Files.createDirectories(fs.getPath("/tmp"));

			Path signed_sha1_sha256 = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.overwriteWithSameDigestAlgorithm(dummyJarSigner(fs, executor), tempFolder);
			
			strategy.resignJar(signed_sha1_sha256);
		}
	}
	
	@Test
	public void testResignStrategyOnUnsigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.resign(dummyJarSigner(fs, executor), MessageDigestAlgorithm.MD5);
			strategy.resignJar(unsigned);
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.MD5.standardName()));
		}
	}
	
	@Test
	public void testResignStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.resign(dummyJarSigner(fs, executor), MessageDigestAlgorithm.MD5);
			strategy.resignJar(signed);
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.MD5.standardName()));
		}
	}
	
	@Test
	public void testResignStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.resign(dummyJarSigner(fs, executor), MessageDigestAlgorithm.MD5);
			strategy.resignJar(signed);
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.MD5.standardName()));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResignWithSameDigestAlgStrategyOnUnsigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path unsigned = copyResource("/unsigned.jar", fs.getPath("/unsigned.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.resignWithSameDigestAlgorithm(dummyJarSigner(fs, executor));
			strategy.resignJar(unsigned);
		}
	}
	
	@Test
	public void testResignWithSameDigestAlgStrategyOnSingleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1.jar", fs.getPath("/signed-sha1.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.resignWithSameDigestAlgorithm(dummyJarSigner(fs, executor));
			strategy.resignJar(signed);
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.SHA_1.standardName()));
			assertFalse(noSignatureFiles(signed));
		}
	}
	
	@Test
	public void testResignWithSameDigestAlgStrategyOnSingleSigned2() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha256.jar", fs.getPath("/signed-sha256.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.resignWithSameDigestAlgorithm(dummyJarSigner(fs, executor));
			strategy.resignJar(signed);
			assertTrue(executor.getCommand().contains(MessageDigestAlgorithm.SHA_256.standardName()));
			assertFalse(noSignatureFiles(signed));
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testResignWithSameDigestAlgStrategyOnDoubleSigned() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path signed = copyResource("/signed-sha1-sha256.jar", fs.getPath("/signed-sha1-sha256.jar"));
			DummyProcessExecutor executor = new DummyProcessExecutor();
			ResigningStrategy strategy = ResigningStrategy.resignWithSameDigestAlgorithm(dummyJarSigner(fs, executor));
			strategy.resignJar(signed);
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

	private JarSigner dummyJarSigner(FileSystem fs, ProcessExecutor executor) {
		JarSigner jarSigner = JarSigner.builder()
				.processExecutor(executor)
				.keystore(fs.getPath("/path/to/keystore"))
				.keystorePassword("keyStorePassword")
				.keystoreAlias("keyStoreAlias")
				.timestampingAuthority(URI.create("http://timestamping.authority"))
				.jarSigner(fs.getPath("/path/to/jarsigner"))
				.timeout(100)
				.digestAlgorithm(MessageDigestAlgorithm.MD2)
				.build();
		return jarSigner;
	}
	
	static Path copyResource(String resourcePath, Path target) throws IOException {
		URL resource = TestResigningStrategy.class.getResource(resourcePath);
		try (InputStream is = new BufferedInputStream(resource.openStream())) {
			Files.copy(is, target);
			return target;
		}
	}
	
	private final class DummyProcessExecutor implements ProcessExecutor {
		private ImmutableList<String> command;

		@Override
		public int exec(ImmutableList<String> command, long timeout, TimeUnit timeoutUnit) throws IOException {
			this.command = command;
			return 0;
		}

		@Override
		public int exec(ImmutableList<String> command, StringBuilder processOutput, long timeout, TimeUnit timeoutUnit) throws IOException {
			this.command = command;
			return 0;
		}
		
		public ImmutableList<String> getCommand() {
			return command;
		}
	}
}
