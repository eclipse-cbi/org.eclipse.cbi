package org.eclipse.cbi.maven.plugins.jarsigner;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.maven.common.ExceptionHandler;
import org.eclipse.cbi.maven.common.test.util.HttpClients;
import org.eclipse.cbi.maven.common.test.util.NullMavenLog;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

@RunWith(Theories.class)
public class JarSignerTest {

	private static Log log;

	@DataPoints
	public static Configuration[] configurations() {
		return new Configuration[] {
				Configuration.unix(),
				Configuration.osX(),
				Configuration.windows(),
		};
	}

	@BeforeClass
	public static void beforeClass() {
		log = new NullMavenLog();
	}

	@Test
	public void testSigningNullFile() throws MojoExecutionException {
		JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
		assertEquals(0, jarSigner.signJar(null));
	}

	@Theory
	public void testSigningTxtFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path fileToSign = fs.getPath("testFile.txt");
			SampleFilesGenerators.writeFile(fileToSign, "content of the file");
			assertEquals(0, jarSigner.signJar(fileToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar1(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude=true");
			assertEquals(0, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=true");
			assertEquals(0, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar3(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=true\n"
					+ "jarprocessor.exclude=true");
			assertEquals(0, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar4(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=false\n"
					+ "jarprocessor.exclude=true");
			assertEquals(0, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar5(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=true\n"
					+ "jarprocessor.exclude=false");
			assertEquals(0, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar6(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=false");
			assertEquals(1, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar7(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude=false");
			assertEquals(1, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningSimpleJarFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			assertEquals(1, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningSimpleJarAbsoluteFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			assertEquals(1, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testNotSigningSimpleJarFile1(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.FAILING, false);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			jarSigner.signJar(jarToSign);
		}
	}

	@Theory
	@Test
	public void testNotSigningSimpleJarFile2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.FAILING, true);
			Path jarToSign = createJar(fs.getPath("path").resolve("to").resolve("jarToSign.jar"));
			assertEquals(0, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testSigningNestedJarFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(HttpClients.DUMMY, false);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 1);
			assertEquals(1, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testRecursiveSigningNestedJarFile1(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(Integer.MAX_VALUE, HttpClients.DUMMY, false);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 1);
			assertEquals(9, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testRecursiveSigningNestedJarFile2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(Integer.MAX_VALUE, HttpClients.DUMMY, false);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 2);
			assertEquals(21, jarSigner.signJar(jarToSign));
		}
	}

	@Theory
	public void testRecursiveSigningNestedJarFile3(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(Integer.MAX_VALUE, HttpClients.DUMMY, false);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(45, jarSigner.signJar(jarToSign));
		}
	}
	
	@Theory
	public void testRecursiveSigningNestedJarFile4(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(0, HttpClients.DUMMY, false);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(1, jarSigner.signJar(jarToSign));
		}
	}
	
	@Theory
	public void testRecursiveSigningNestedJarFile5(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(1, HttpClients.DUMMY, false);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(5, jarSigner.signJar(jarToSign));
		}
	}
	
	@Theory
	public void testRecursiveSigningNestedJarFile6(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(2, HttpClients.DUMMY, false);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(13, jarSigner.signJar(jarToSign));
		}
	}

	private static Path createJarWithEclipseInf(Path jarFile, String eclipseInfContent) throws IOException {
		if (jarFile.getParent() != null) {
			Files.createDirectories(jarFile.getParent());
		}
		Path tempDirectory = Files.createTempDirectory(Paths.getParent(jarFile), null);
		Path eclipseInf = tempDirectory.resolve("META-INF").resolve("eclipse.inf");
		SampleFilesGenerators.writeFile(eclipseInf, eclipseInfContent);
		SampleFilesGenerators.writeFile(tempDirectory.resolve("aFile"), "Content of the file");
		SampleFilesGenerators.writeFile(tempDirectory.resolve("META-INF").resolve("MANIFEST.MF"), "Manifest-Version: 1.0\n"+
				"Created-By: CBI Project!");
		Zips.packJar(tempDirectory, jarFile, false);
		Paths.delete(tempDirectory);
		return jarFile;
	}

	private static Path createJar(Path jarFile) throws IOException {
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

	private Path createJarWithNestedJars(Path jarFile, int maxDepth) throws IOException {
		return createJarWithNestedJars(jarFile, maxDepth, 0, false);
	}

	private Path createJarWithNestedJars(Path jarFile, int maxDepth, int depth, boolean excludeFromSigning) throws IOException {
		if (jarFile.getParent() != null) {
			Files.createDirectories(jarFile.getParent());
		}
		Path tempDirectory = Files.createTempDirectory(Paths.getParent(jarFile), null);

		if (depth < maxDepth) {
			createJarWithNestedJars(tempDirectory.resolve("jarWithNested1.jar"), maxDepth, depth+1, false);
			createJarWithNestedJars(tempDirectory.resolve("jarWithNested2.jar"), maxDepth, depth+1, false);
			createJarWithNestedJars(tempDirectory.resolve("jarWithNested3.jar"), maxDepth, depth+1, true);
		}

		createJar(tempDirectory.resolve("jar1.jar"));
		createJar(tempDirectory.resolve("jar2.jar"));
		createJarWithEclipseInf(tempDirectory.resolve("jar3.jar"), "jarprocessor.exclude.sign=true");
		SampleFilesGenerators.writeFile(tempDirectory.resolve("aFile1"), "Content of the file 1");
		SampleFilesGenerators.writeFile(tempDirectory.resolve("aFile2"), "Content of the file 2");
		SampleFilesGenerators.writeFile(tempDirectory.resolve("META-INF").resolve("MANIFEST.MF"), "Manifest-Version: 1.0\n"+
				"Created-By: CBI Project!");

		if (excludeFromSigning) {
			SampleFilesGenerators.writeFile(tempDirectory.resolve("META-INF").resolve("eclipse.inf"), "jarprocessor.exclude.sign=true");
		}

		Zips.packJar(tempDirectory, jarFile, false);
		Paths.delete(tempDirectory);
		return jarFile;
	}
	
	private JarSigner createJarSigner(HttpClients client, boolean continueOnFail) {
		return createJarSigner(0, client, continueOnFail);
	}
	
	private JarSigner createJarSigner(int maxDepth, HttpClients client, boolean continueOnFail) {
		JarSigner jarSigner = JarSigner.builder().httpClient(client)
				.serverUri(URI.create("http://localhost"))
				.maxDepth(maxDepth)
				.log(log)
				.exceptionHandler(new ExceptionHandler(log, continueOnFail))
				.build();
		return jarSigner;
	}

}
