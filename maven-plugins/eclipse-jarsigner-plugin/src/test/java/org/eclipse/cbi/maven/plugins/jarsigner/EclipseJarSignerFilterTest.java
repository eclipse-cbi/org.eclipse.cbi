package org.eclipse.cbi.maven.plugins.jarsigner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
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
public class EclipseJarSignerFilterTest {

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
	public void testSigningNullFile() throws IOException {
		assertFalse(new EclipseJarSignerFilter(log).shouldBeSigned(null));
	}

	@Theory
	public void testSigningTxtFile(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			Path fileToSign = fs.getPath("testFile.txt");
			SampleFilesGenerators.writeFile(fileToSign, "content of the file");
			assertFalse(new EclipseJarSignerFilter(log).shouldBeSigned(fileToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar1(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude=true");
			assertFalse(new EclipseJarSignerFilter(log).shouldBeSigned(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar2(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=true");
			assertFalse(new EclipseJarSignerFilter(log).shouldBeSigned(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar3(Configuration fsConf) throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=true\n"
					+ "jarprocessor.exclude=true");
			assertFalse(new EclipseJarSignerFilter(log).shouldBeSigned(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar4(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=false\n"
					+ "jarprocessor.exclude=true");
			assertFalse(new EclipseJarSignerFilter(log).shouldBeSigned(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar5(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=true\n"
					+ "jarprocessor.exclude=false");
			assertFalse(new EclipseJarSignerFilter(log).shouldBeSigned(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar6(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude.sign=false");
			assertTrue(new EclipseJarSignerFilter(log).shouldBeSigned(jarToSign));
		}
	}

	@Theory
	public void testSigningDeactivatedJar7(Configuration fsConf) throws IOException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			Path jarToSign = createJarWithEclipseInf(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), "jarprocessor.exclude=false");
			assertTrue(new EclipseJarSignerFilter(log).shouldBeSigned(jarToSign));
		}
	}
	
	static Path createJarWithEclipseInf(Path jarFile, String eclipseInfContent) throws IOException {
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
}
