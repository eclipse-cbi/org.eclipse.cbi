package org.eclipse.cbi.maven.plugins.winsigner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.maven.ExceptionHandler;
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
public class WindowsExeSignerTest {

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

	@Test(expected=NullPointerException.class)
	public void testSigningNullFiles() throws MojoExecutionException {
		WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
		winExeSigner.signExecutable(null);
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningNonExistingFile(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			winExeSigner.signExecutable(fs.getPath("test"));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningFolder(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			winExeSigner.signExecutable(Files.createDirectories(fs.getPath("test")));
		}
	}

	@Theory
	public void testSigningFile(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path path = SampleFilesGenerators.writeFile(fs.getPath("path").resolve("to").resolve("file.txt"), "content of the file");
			assertTrue(winExeSigner.signExecutable(path));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningFileWithNotSigningSigner(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.FAILING);
			Path path = SampleFilesGenerators.writeFile(fs.getPath("path").resolve("to").resolve("file.txt"), "content of the file");
			assertFalse(winExeSigner.signExecutable(path));
		}
	}

	@Theory
	public void testSigningFileWithNotSigningSignerButContinueOnFail(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.FAILING, true);
			Path path = SampleFilesGenerators.writeFile(fs.getPath("path").resolve("to").resolve("file.txt"), "content of the file");
			assertFalse(winExeSigner.signExecutable(path));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningFileWithErrorSigner(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.ERROR);
			Path path = SampleFilesGenerators.writeFile(fs.getPath("path").resolve("to").resolve("file.txt"), "content of the file");
			assertFalse(winExeSigner.signExecutable(path));
		}
	}

	@Theory
	public void testSigningFileWithErrorSignerButContinueOnFail(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.ERROR, true);
			Path path = SampleFilesGenerators.writeFile(fs.getPath("path").resolve("to").resolve("file.txt"), "content of the file");
			assertFalse(winExeSigner.signExecutable(path));
		}
	}

	@Theory
	public void testSigningFiles(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(1, winExeSigner.signExecutables(newSet(baseDir.resolve("app1.exe"))));
		}
	}

	@Theory
	public void testSigningFiles2(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(2, winExeSigner.signExecutables(newSet(baseDir.resolve("app1.exe"), baseDir.resolve("subFolder/app3.exe"))));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningFiles3(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			winExeSigner.signExecutables(newSet(baseDir.resolve("app1.exe"), baseDir.resolve("subFolder/appX.exe")));
		}
	}

	@Theory
	public void testSigningFiles3ButContinueOnFail(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY, true);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(1, winExeSigner.signExecutables(newSet(baseDir.resolve("app1.exe"), baseDir.resolve("subFolder/appX.exe"))));
		}
	}

	@Theory
	public void testSigningFilesWithLookup(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(0, winExeSigner.signExecutables(baseDir, new LinkedHashSet<PathMatcher>()));
		}
	}

	@Theory
	public void testSigningFilesWithLookupWithMojoMatchers(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(1, winExeSigner.signExecutables(baseDir, SignMojo.getPathMatchers(fs, newSet("app4.exe"), log)));
		}
	}

	@Theory
	public void testSigningFilesWithLookupWithMojoMatchers2(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(4, winExeSigner.signExecutables(baseDir, SignMojo.getPathMatchers(fs, new LinkedHashSet<String>(), log)));
		}
	}

	@Theory
	public void testSigningFilesWithLookupPattern(Configuration fsConf) throws MojoExecutionException, IOException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			WindowsExeSigner winExeSigner = createSigner(HttpClients.DUMMY);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(10, winExeSigner.signExecutables(baseDir, SignMojo.getPathMatchers(fs, newSet("*.exe"), log)));
		}
	}

	private WindowsExeSigner createSigner(HttpClients client) {
		return createSigner(client, false);
	}
	
	private WindowsExeSigner createSigner(HttpClients client, boolean continueOnFail) {
		return WindowsExeSigner.builder()
				.serverUri(URI.create("http://localhost"))
				.httpClient(client)
				.connectTimeoutMillis(0)
				.exceptionHandler(new ExceptionHandler(log, continueOnFail))
				.log(log)
				.build();
	}

	private Path createTestAppFolders(Path baseDir) throws IOException {
		Files.createDirectories(baseDir);
		Files.createFile(baseDir.resolve("app1.exe"));
		Files.createFile(baseDir.resolve("app2.exe"));
		Files.createFile(baseDir.resolve("eclipse.exe"));
		Files.createFile(baseDir.resolve("eclipsec.exe"));
		Path subFolder = Files.createDirectories(baseDir.resolve("subFolder"));
		Files.createFile(subFolder.resolve("app3.exe"));
		Path subSubFolder2 = Files.createDirectories(baseDir.resolve("subFolder2").resolve("subSub"));
		Files.createFile(subSubFolder2.resolve("app4.exe"));
		Files.createFile(subSubFolder2.resolve("app5.exe"));
		Files.createFile(subSubFolder2.resolve("app6.exe"));
		Files.createFile(subSubFolder2.resolve("eclipse.exe"));
		Files.createFile(subSubFolder2.resolve("eclipsec.exe"));
		return baseDir;
	}

	private static <T> Set<T> newSet(@SuppressWarnings("unchecked") T... app) {
		return new LinkedHashSet<>(Arrays.asList(app));
	}
}
