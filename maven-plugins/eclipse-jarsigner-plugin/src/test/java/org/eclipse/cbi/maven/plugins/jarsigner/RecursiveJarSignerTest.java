package org.eclipse.cbi.maven.plugins.jarsigner;

import static org.eclipse.cbi.maven.plugins.jarsigner.RemoteJarSignerTest.dummyOptions;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.maven.common.test.util.NullMavenLog;
import org.junit.BeforeClass;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

@RunWith(Theories.class)
public class RecursiveJarSignerTest {

	private static Log log;

	@BeforeClass
	public static void beforeClass() {
		log = new NullMavenLog();
	}
	
	@DataPoints
	public static Configuration[] configurations() {
		return new Configuration[] {
				Configuration.unix(),
				Configuration.osX(),
				Configuration.windows(),
		};
	}
	
	@Theory
	public void testSigningNestedJarFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(0);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 1);
			assertEquals(1, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}

	@Theory
	public void testRecursiveSigningNestedJarFile1(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(Integer.MAX_VALUE);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 1);
			assertEquals(9, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}

	@Theory
	public void testRecursiveSigningNestedJarFile2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
			JarSigner jarSigner = createJarSigner(Integer.MAX_VALUE);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 2);
			assertEquals(21, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}

	@Theory
	public void testRecursiveSigningNestedJarFile3(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(Integer.MAX_VALUE);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(45, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}
	
	@Theory
	public void testRecursiveSigningNestedJarFile4(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(0);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(1, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}
	
	@Theory
	public void testRecursiveSigningNestedJarFile5(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(1);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(5, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}
	
	@Theory
	public void testRecursiveSigningNestedJarFile6(Configuration fsConf) throws IOException, MojoExecutionException {
			try (FileSystem fs= Jimfs.newFileSystem(fsConf)) {
				JarSigner jarSigner = createJarSigner(2);
			Path jarToSign = createJarWithNestedJars(fs.getPath("path").resolve("to").resolve("jarToSign.jar"), 3);
			assertEquals(13, jarSigner.sign(jarToSign, dummyOptions()));
		}
	}
	
	private JarSigner createJarSigner(int maxDepth) {
		return RecursiveJarSigner.builder()
				.log(log)
				.maxDepth(maxDepth)
				.filter(new EclipseJarSignerFilter(log))
				.delegate(new JarSigner() {
			@Override
			public int sign(Path jarfile, Options options) throws IOException {
				return 1;
			}
		}).build();
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

		RemoteJarSignerTest.createJar(tempDirectory.resolve("jar1.jar"));
		RemoteJarSignerTest.createJar(tempDirectory.resolve("jar2.jar"));
		EclipseJarSignerFilterTest.createJarWithEclipseInf(tempDirectory.resolve("jar3.jar"), "jarprocessor.exclude.sign=true");
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
}
