package org.eclipse.cbi.maven.plugins.macsigner;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.common.test.util.HttpClients;
import org.eclipse.cbi.maven.common.test.util.NullMavenLog;
import org.eclipse.cbi.maven.http.HttpClient;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class OSXAppSignerTest {

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
		OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
		assertEquals(0, osxAppSigner.signApplications(null, null));
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningEmptyFiles(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path fileToSign = SampleFilesGenerators.writeFile(fs.getPath("testFile.app"), "");
			assertEquals(0, osxAppSigner.signApplications(newSet(fileToSign), null));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningNonExistingFiles(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			osxAppSigner.signApplications(newSet(fs.getPath("testFile.txt")), null);
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningNonExistingAppFolder(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			osxAppSigner.signApplications(newSet(fs.getPath("testApp.app")), null);
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningTxtFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path fileToSign = SampleFilesGenerators.writeFile(fs.getPath("testFile.txt"), "content of the file");
			osxAppSigner.signApplications(newSet(fileToSign), null);
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningAppFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path fileToSign = SampleFilesGenerators.writeFile(fs.getPath("testFile.app"), "content of the file");
			osxAppSigner.signApplications(newSet(fileToSign), null);
		}
	}

	@Theory
	public void testSigningEmptyAppFolder(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path folderToSign = Files.createDirectories(fs.getPath("test", "testApp.app"));
			assertEquals(1, osxAppSigner.signApplications(newSet(folderToSign), null));
		}
	}

	@Theory
	public void testSigningAppFolder(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path app = Files.createDirectories(fs.getPath("test", "testApp.app"));
			Path file = SampleFilesGenerators.writeFile(app.resolve("testFile.txt"), "content of the file");
			assertEquals(1, osxAppSigner.signApplications(newSet(app),null));
			assertEquals("content of the file", new String(Files.readAllBytes(file)));
		}
	}

	@Theory
	public void testSigningAppFolder2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path app = Files.createDirectories(fs.getPath("test", "testApp.app"));
			Path file1 = SampleFilesGenerators.writeFile(app.resolve("testFile.txt"), "content of the file");
			Path file2 = SampleFilesGenerators.writeFile(app.resolve("Contents").resolve("testFile2.txt"), "content of the file 2");
			assertEquals(1, osxAppSigner.signApplications(newSet(app),null));
			assertEquals("content of the file", new String(Files.readAllBytes(file1)));
			assertEquals("content of the file 2", new String(Files.readAllBytes(file2)));
		}
	}

	@Theory
	public void testSigningNestedAppFolder(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path app = Files.createDirectories(fs.getPath("test", "testApp.app"));
			Files.createDirectories(app.resolve("anotherApp.app"));
			assertEquals(1, osxAppSigner.signApplications(newSet(app),null));
		}
	}

	@Theory
	public void testSigning2AppFolder(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			Path app2 = Files.createDirectories(fs.getPath("test", "testApp2.app"));
			assertEquals(2, osxAppSigner.signApplications(newSet(app1, app2),null));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigning2AppFolderAndAFile(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			Path app2 = Files.createDirectories(fs.getPath("test", "testApp2.app"));
			Path file = SampleFilesGenerators.writeFile(fs.getPath("testFile.txt"), "content of the file");
			osxAppSigner.signApplications(newSet(file, app1, app2),null);
		}
	}

	@Theory
	public void testSigning2AppFolderAndAFileButContinueOnFail(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, true);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			Path app2 = Files.createDirectories(fs.getPath("test", "testApp2.app"));
			Path file = SampleFilesGenerators.writeFile(fs.getPath("testFile.txt"), "content of the file");
			assertEquals(2, osxAppSigner.signApplications(newSet(file, app1, app2),null));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningWithNotSigningSigner(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.FAILING, false);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			osxAppSigner.signApplications(newSet(app1),null);
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningWithNotSigningSigner2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.FAILING, false);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			Path app2 = Files.createDirectories(fs.getPath("test", "testApp2.app"));
			osxAppSigner.signApplications(newSet(app1, app2),null);
		}
	}

	@Theory
	public void testSigningWithNotSigningSignerButContinueOnFail(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.FAILING, true);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			osxAppSigner.signApplications(newSet(app1),null);
		}
	}

	@Theory
	public void testSigningWithNotSigningSignerButContinueOnFail2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.FAILING, true);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			Path app2 = Files.createDirectories(fs.getPath("test", "testApp2.app"));
			osxAppSigner.signApplications(newSet(app1, app2),null);
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningWithErrorSigner(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.ERROR, false);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			osxAppSigner.signApplications(newSet(app1), null);
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningWithErrorSigner2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.ERROR, false);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			Path app2 = Files.createDirectories(fs.getPath("test", "testApp2.app"));
			osxAppSigner.signApplications(newSet(app1, app2), null);
		}
	}

	@Theory
	public void testSigningWithErrorSignerButContinuerOnFail(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.ERROR, true);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			osxAppSigner.signApplications(newSet(app1), null);
		}
	}

	@Theory
	public void testSigningWithErrorSignerButContinuerOnFail2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.ERROR, true);
			Path app1 = Files.createDirectories(fs.getPath("test", "testApp1.app"));
			Path app2 = Files.createDirectories(fs.getPath("test", "testApp2.app"));
			osxAppSigner.signApplications(newSet(app1, app2), null);
		}
	}

	@Theory
	@Test(expected=NullPointerException.class)
	public void testSigningNullDirectory(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			osxAppSigner.signApplications(null, new LinkedHashSet<PathMatcher>(), null);
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningWithLookupInNonExistingFolder(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			osxAppSigner.signApplications(fs.getPath("test"), new LinkedHashSet<PathMatcher>(),null);
		}
	}

	@Theory
	public void testSigningWithLookup(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			assertEquals(0, osxAppSigner.signApplications(Files.createDirectories(fs.getPath("test")), new LinkedHashSet<PathMatcher>(), null));
		}
	}

	@Theory
	@Test(expected=NullPointerException.class)
	public void testSigningWithLookupWithNullPatterns(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			assertEquals(0, osxAppSigner.signApplications(Files.createDirectories(fs.getPath("test")), null, null));
		}
	}

	@Theory
	public void testSigningWithLookupWithEmptyPatterns(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(0, osxAppSigner.signApplications(baseDir, new LinkedHashSet<PathMatcher>(), null));
		}
	}

	@Theory
	public void testSigningWithLookupWithDefaultMojoMatchers(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(2, osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, new LinkedHashSet<String>(), log), null));
		}
	}

	@Theory
	public void testSigningWithLookupWithMojoMatchers(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(3, osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("app1.app", "app5.app", "app3.app"), log), null));
		}
	}

	@Theory
	public void testSigningWithLookupWithAdvancedMojoMatchers(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(6, osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("app*.app"), log), null));
		}
	}

	@Theory
	public void testSigningWithLookupWithAdvancedMojoMatchers2(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(0, osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("subFolder2/*.app"), log), null));
		}
	}

	@Theory
	public void testSigningWithLookupWithAdvancedMojoMatchers4(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.DUMMY, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(4, osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("subSub/*.app"), log), null));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningWithLookupWithNonSigningSigner(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.FAILING, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("app1.app", "app5.app", "app3.app"), log), null);
		}
	}

	@Theory
	public void testSigningWithLookupWithNonSigningSignerButContinueOnFail(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.FAILING, true);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(0, osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("app1.app", "app5.app", "app3.app"), log), null));
		}
	}

	@Theory
	@Test(expected=MojoExecutionException.class)
	public void testSigningWithLookupWithErrorSigner(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.ERROR, false);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("app1.app", "app5.app", "app3.app"), log), null);
		}
	}

	@Theory
	public void testSigningWithLookupWithErrorSignerButContinueOnFail(Configuration fsConf) throws IOException, MojoExecutionException {
		try (FileSystem fs = Jimfs.newFileSystem(fsConf)) {
			OSXAppSigner osxAppSigner = createAppSigner(HttpClients.ERROR, true);
			Path baseDir = createTestAppFolders(fs.getPath("test"));
			assertEquals(0, osxAppSigner.signApplications(baseDir, SignMojo.getPathMatchers(fs, newSet("app1.app", "app5.app", "app3.app"), log), null));
		}
	}

	private Path createTestAppFolders(Path baseDir) throws IOException {
		Files.createDirectories(baseDir.resolve("app1.app"));
		Files.createDirectories(baseDir.resolve("app1.app/subFolder/appSUB.app"));
		Files.createDirectories(baseDir.resolve("app2.app"));
		Files.createDirectories(baseDir.resolve("Eclipse.app"));
		Files.createDirectories(baseDir.resolve("subFolder").resolve("app3.app"));
		Files.createDirectories(baseDir.resolve("subFolder2").resolve("subSub").resolve("app4.app"));
		Files.createDirectories(baseDir.resolve("subFolder2").resolve("subSub").resolve("app5.app"));
		Files.createDirectories(baseDir.resolve("subFolder2").resolve("subSub").resolve("app6.app"));
		Files.createDirectories(baseDir.resolve("subFolder2").resolve("subSub").resolve("Eclipse.app"));
		return baseDir;
	}

	private OSXAppSigner createAppSigner(HttpClient client, boolean continueOnFail) {
		OSXAppSigner osxAppSigner = OSXAppSigner.builder()
				.serverUri(URI.create("http://localhost"))
				.httpClient(client)
				.timeout(Duration.ZERO)
				.exceptionHandler(new ExceptionHandler(log, continueOnFail))
				.log(log)
				.build();
		return osxAppSigner;
	}
	
	private static <T> Set<T> newSet(@SuppressWarnings("unchecked") T... app) {
		return new LinkedHashSet<>(Arrays.asList(app));
	}
}
