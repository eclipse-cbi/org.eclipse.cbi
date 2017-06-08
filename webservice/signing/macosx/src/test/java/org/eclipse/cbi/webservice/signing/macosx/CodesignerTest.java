package org.eclipse.cbi.webservice.signing.macosx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.servlet.ServletException;

import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

@RunWith(MockitoJUnitRunner.class)
public class CodesignerTest {

	@Mock private ProcessExecutor processExecutor;

	@Test(expected=IllegalStateException.class)
	public void testNonExistingTempFolder() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Codesigner.builder()
					.certificateName("Cert")
					.keychain(Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("keychain")))
					.keychainPassword("password")
					.tempFolder(fs.getPath("/tmp"))
					.processExecutor(processExecutor)
					.build();
		}
	}

	@Test(expected=IllegalStateException.class)
	public void testNonExistingKeychain() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Codesigner.builder()
					.certificateName("Cert")
					.keychain(fs.getPath("/path/to/keychain"))
					.keychainPassword("password")
					.tempFolder(Files.createDirectory(fs.getPath("/tmp")))
					.processExecutor(processExecutor)
					.build();
		}
	}

	@Test(expected=NullPointerException.class)
	public void testNullSource() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Path source = null;
			Path target = fs.getPath("signed.zip");
			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target);
		}
	}

	@Test(expected=NullPointerException.class)
	public void testNullTarget() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Path source = fs.getPath("unsigned.zip");
			Path target = null;
			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testSourceNotExists() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Path source = fs.getPath("unsigned.zip");
			Path target = fs.getPath("signed.zip");
			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testSourceNotAZip() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Path source = Files.createFile(fs.getPath("unsigned.txt"));
			Path target = fs.getPath("signed.zip");
			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testTargetNotAZip() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Path source = Files.createFile(fs.getPath("unsigned.zip"));
			Path target = fs.getPath("signed.txt");
			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target);
		}
	}

	@Test(expected=IOException.class)
	public void testPlainTextFile() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			Path source = SampleFilesGenerators.writeFile(fs.getPath("unsigned.zip"), "file content");
			Path target = fs.getPath("signed.zip");
			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target);
		}
	}

	@Test
	public void testZipFileWithoutApp() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "t1", "Test1.java"), 3);
			SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "t2", "t3", "Test2.java"), 10);
			Path source = fs.getPath("testFile.zip");
			Zips.packZip(fs.getPath("folder"), source, true);

			Path target = fs.getPath("signed.zip");
			assertEquals(0, createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target));
			verify(processExecutor).exec(eq(ImmutableList.of("security", "unlock", "-p", "password", "/path/to/keychain")), any(), anyLong(), any());
			verifyZeroInteractions(processExecutor);

			verifyCleanedTempFolder(fs);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testZipFileWithOneApp() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			assertEquals(1, createCodesignerUnderTest(fs, processExecutor).signZippedApplications(createTestZipFile(fs), fs.getPath("signed.zip")));

			ArgumentCaptor<ImmutableList> listCaptor = ArgumentCaptor.forClass(ImmutableList.class);
			verify(processExecutor, times(2)).exec(listCaptor.capture(), any(), anyLong(), any());
			assertEquals("security", listCaptor.getAllValues().get(0).get(0));
			assertEquals("codesign", listCaptor.getAllValues().get(1).get(0));
			assertTrue(listCaptor.getAllValues().get(1).get(8).toString().startsWith("/tmp"));
			assertTrue(listCaptor.getAllValues().get(1).get(8).toString().endsWith("MyApp.app"));

			verifyZeroInteractions(processExecutor);

			verifyCleanedTempFolder(fs);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testSecurityTimeout() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			assertEquals(1, createCodesignerUnderTest(fs, processExecutor).signZippedApplications(createTestZipFile(fs), fs.getPath("signed.zip")));

			ArgumentCaptor<ImmutableList> listCaptor = ArgumentCaptor.forClass(ImmutableList.class);
			verify(processExecutor).exec(listCaptor.capture(), any(), eq(10L), any());
			assertEquals("security", listCaptor.getAllValues().get(0).get(0));

			listCaptor = ArgumentCaptor.forClass(ImmutableList.class);
			verify(processExecutor).exec(listCaptor.capture(), any(), eq(20L), any());
			assertEquals("codesign", listCaptor.getAllValues().get(0).get(0));

			verifyZeroInteractions(processExecutor);

			verifyCleanedTempFolder(fs);
		}
	}

	@Test(expected=IOException.class)
	public void testBadSecurityUnlockExec() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			when(processExecutor.exec(any(), any(), anyLong(), any())).thenReturn(127);

			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(createTestZipFile(fs), fs.getPath("signed.zip"));
		}
	}

	@Test(expected=IOException.class)
	public void testBadCodesignExec() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			when(processExecutor.exec(any(), any(), anyLong(), any())).thenReturn(0).thenReturn(127);

			createCodesignerUnderTest(fs, processExecutor).signZippedApplications(createTestZipFile(fs), fs.getPath("signed.zip"));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testZipFileWithThreeApps() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			assertEquals(2,
				createCodesignerUnderTest(fs, processExecutor).signZippedApplications(createTestZipFile2(fs), fs.getPath("signed.zip")));

			ArgumentCaptor<ImmutableList> listCaptor = ArgumentCaptor.forClass(ImmutableList.class);
			verify(processExecutor, times(3)).exec(listCaptor.capture(), any(), anyLong(), any());
			assertEquals("security", listCaptor.getAllValues().get(0).get(0));
			listCaptor.getAllValues().subList(1, listCaptor.getAllValues().size()).forEach(args -> {
				assertEquals("codesign", args.get(0));
			});
			assertTrue(listCaptor.getAllValues().get(1).get(8).toString().endsWith("MyApp.app"));
			assertTrue(listCaptor.getAllValues().get(2).get(8).toString().endsWith("MySecondApp.app"));

			verifyCleanedTempFolder(fs);
		}
	}

	@Test
	public void testZipFileNestedDotAppFolders() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MyApp.app", "t1", "Test1.java"), 3);
			SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MyApp.app", "t2", "t3", "Test2.java"), 10);
			SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MyApp.app", "t2", "AnotherApp.app", "Test2.java"), 10);
			SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MySecondApp.app", "t2", "t3", "Test2.java"), 10);
			SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "subFolder", "MyThirdApp.app", "t2", "t3", "Test2.java"), 10);
			Path zip = fs.getPath("testFile.zip");
			Zips.packZip(fs.getPath("folder"), zip, false);


			assertEquals(2,
					createCodesignerUnderTest(fs, processExecutor).signZippedApplications(zip, fs.getPath("signed.zip")));
			verifyCleanedTempFolder(fs);
		}
	}

	@SuppressWarnings({ "unchecked" })
	@Test
	public void testZipResult() throws IOException, ServletException {
		try(FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
			// let's add a new file a the root of the app to simulate signing
			when(processExecutor.exec(any(), any(), anyLong(), any())).then(new Answer<Integer>() {
				@Override
				public Integer answer(InvocationOnMock invocation) throws Throwable {
					ImmutableList<String> command = (ImmutableList<String>) invocation.getArguments()[0];
					if ("codesign".equals(command.get(0))) {
						Path app = fs.getPath(command.get(8).toString());
						Files.createFile(app.resolve("signed"));
					}
					return 0;
				}
			});

			Path source = createTestZipFile2(fs);
			Path target = fs.getPath("signed.zip");
			assertEquals(2,
				createCodesignerUnderTest(fs, processExecutor).signZippedApplications(source, target));

			assertEquals(2+Zips.unpackZip(source, fs.getPath("/unzipUnsigned")),
				Zips.unpackZip(target, fs.getPath("/unzipSigned")));

			assertTrue(Files.exists(fs.getPath("/unzipSigned/MyApp.app/signed")));
			assertTrue(Files.exists(fs.getPath("/unzipSigned/MySecondApp.app/signed")));

			verifyCleanedTempFolder(fs);
		}
	}

	private static void verifyCleanedTempFolder(FileSystem fs) throws IOException {
		try (Stream<Path> list = Files.list(fs.getPath("/tmp"))) {
			assertEquals(0, list.count());
		}
	}

	private static Codesigner createCodesignerUnderTest(FileSystem fs, ProcessExecutor processExecutor) throws IOException {
		return Codesigner.builder()
				.certificateName("Cert")
				.keychain(Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("keychain")))
				.keychainPassword("password")
				.tempFolder(Files.createDirectory(fs.getPath("/tmp")))
				.processExecutor(processExecutor)
				.codesignTimeout(20)
				.timeStampAuthority("")
				.securityUnlockTimeout(10)
				.build();
	}

	private static Path createTestZipFile(FileSystem fs) throws IOException {
		SampleFilesGenerators.createLoremIpsumFile(fs.getPath("MyApp.app", "t1", "Test1.java"), 3);
		SampleFilesGenerators.createLoremIpsumFile(fs.getPath("MyApp.app", "t2", "t3", "Test2.java"), 10);
		Path zip = fs.getPath("testFile.zip");
		Zips.packZip(fs.getPath("MyApp.app"), zip, true);
		return zip;
	}

	private static Path createTestZipFile2(FileSystem fs) throws IOException {
		SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MyApp.app", "t1", "Test1.java"), 3);
		SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MyApp.app", "t2", "t3", "Test2.java"), 10);
		SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MyApp.app", "t2", "t3", "Test2.java"), 10);
		SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "MySecondApp.app", "t2", "t3", "Test2.java"), 10);
		SampleFilesGenerators.createLoremIpsumFile(fs.getPath("folder", "subFolder", "MyThirdApp.app", "t2", "t3", "Test2.java"), 10);
		Path zip = fs.getPath("testFile.zip");
		Zips.packZip(fs.getPath("folder"), zip, false);
		return zip;
	}
}
