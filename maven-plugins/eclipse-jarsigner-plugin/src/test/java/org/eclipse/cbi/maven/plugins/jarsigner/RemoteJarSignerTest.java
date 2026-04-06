package org.eclipse.cbi.maven.plugins.jarsigner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.security.MessageDigestAlgorithm;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.maven.common.test.util.HttpClients;
import org.eclipse.cbi.maven.common.test.util.NullMavenLog;
import org.eclipse.cbi.maven.plugins.jarsigner.JarSigner.Options;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class RemoteJarSignerTest {

	private static Log log;

	public static Configuration[] configurations() {
		return new Configuration[] {
				Configuration.unix(),
				Configuration.osX(),
				Configuration.windows(),
		};
	}

	@BeforeAll
	public static void beforeClass() {
		log = new NullMavenLog();
	}

	@ParameterizedTest
	@MethodSource("configurations")
	public void testSigningSimpleJarFile(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			assertEquals(1, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}

	@ParameterizedTest
	@MethodSource("configurations")
	public void testSigningSimpleJarAbsoluteFile(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			assertEquals(1, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}

	@ParameterizedTest
	@MethodSource("configurations")
	public void testNotSigningSimpleJarFile1(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.ERROR);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			assertThrows(IOException.class, () -> jarSigner.sign(jarToSign, dummyOptions()));
		}
	}

	@ParameterizedTest
	@MethodSource("configurations")
	public void testNotSigningSimpleJarFile2(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.FAILING);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			assertEquals(0, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}

	static Path createJar(Path jarFile) throws IOException {
		if (jarFile.getParent() != null) {
			Files.createDirectories(jarFile.getParent());
		}
		Path tempDirectory = Files.createTempDirectory(Paths.getParent(jarFile), null);
		SampleFilesGenerators.writeFile(tempDirectory.resolve("aFile1"), "Content of the file 1");
		SampleFilesGenerators.writeFile(tempDirectory.resolve("aFile2"), "Content of the file 2");
		SampleFilesGenerators.writeFile(tempDirectory.resolve("META-INF").resolve("MANIFEST.MF"), "Manifest-Version: 1.0\n"+
				"Created-By: CBI Project!");
		Zips.packJar(tempDirectory, jarFile, false);
		Paths.delete(tempDirectory);
		return jarFile;
	}

	static Options dummyOptions() {
		return Options.builder().digestAlgorithm(MessageDigestAlgorithm.SHA_224).build();
	}
	
	private JarSigner createJarSigner(HttpClients client) {
		return createJarSigner(0, client);
	}
	
	private JarSigner createJarSigner(int maxDepth, HttpClients client) {
		JarSigner jarSigner = RemoteJarSigner.builder().httpClient(client)
				.serverUri(URI.create("http://localhost"))
				.log(log)
				.build();
		return jarSigner;
	}

}
