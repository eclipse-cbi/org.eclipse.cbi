package org.eclipse.cbi.maven.common.http;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpRequest.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class HttpRequestTest {

	@Test
	public void testNullPathParam() {
		assertThrows(NullPointerException.class, () -> HttpRequest.on(URI.create("localhost"))
			.withParam("file", (Path) null).build());
	}
	
	@Test
	public void testNullPathParamName() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			assertThrows(NullPointerException.class, () -> HttpRequest.on(URI.create("localhost"))
				.withParam(null, Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("/file"))).build());
		}
	}
	
	@Test
	public void testEmptyPathParamName() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			assertThrows(IllegalArgumentException.class, () -> HttpRequest.on(URI.create("localhost"))
				.withParam("", Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("/file"))).build());
		}
	}

	@Test
	public void testNonExistingPathParam() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			assertThrows(IllegalArgumentException.class, () -> HttpRequest.on(URI.create("localhost"))
				.withParam("file", fs.getPath("/path/to/file")).build());
		}
	}

	@Test
	public void testFolderPathParam() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			assertThrows(IllegalArgumentException.class, () -> HttpRequest.on(URI.create("localhost"))
				.withParam("file", Files.createDirectories(fs.getPath("/path/to/folder"))).build());
		}
	}
	
	@Test
	public void testAddPathParam() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path file = Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("/file"));
			HttpRequest request = HttpRequest.on(URI.create("localhost"))
				.withParam("file", file).build();
			Assertions.assertEquals(file, request.pathParameters().get("file"));
			Assertions.assertEquals(1, request.pathParameters().size());
			Assertions.assertTrue(request.stringParameters().isEmpty());
		}
	}
	
	@Test
	public void testRequestConfigDefault() {
		Config requestConfig = HttpRequest.Config.defaultConfig();
		Assertions.assertEquals(Duration.ZERO, requestConfig.timeout());
	}
	
	@Test
	public void testRequestConfigDefaultTimeout() {
		Duration duration = Duration.ofMillis(500);
		Config requestConfig = HttpRequest.Config.builder().timeout(duration).build();
		Assertions.assertEquals(duration, requestConfig.timeout());
	}
	
	@Test
	public void testNullStringParam() {
		assertThrows(NullPointerException.class, () -> HttpRequest.on(URI.create("localhost"))
			.withParam("param", (String) null).build());
	}
	
	@Test
	public void testNullStringParamName() {
		assertThrows(NullPointerException.class, () -> HttpRequest.on(URI.create("localhost"))
			.withParam(null, "value").build());
	}
	
	@Test
	public void testEmptyStringParamName() {
		assertThrows(IllegalArgumentException.class, () -> HttpRequest.on(URI.create("localhost"))
			.withParam("", "value").build());
	}
	
	@Test
	public void testAddStringParamName() throws IOException {
		HttpRequest request = HttpRequest.on(URI.create("localhost"))
				.withParam("name", "value").build();
		Assertions.assertEquals("value", request.stringParameters().get("name"));
		Assertions.assertEquals(1, request.stringParameters().size());
		Assertions.assertTrue(request.pathParameters().isEmpty());
	}
	
	@Test
	public void testEmptyStringParam() throws IOException {
		HttpRequest request = HttpRequest.on(URI.create("localhost"))
				.withParam("name", "").build();
		Assertions.assertEquals("", request.stringParameters().get("name"));
		Assertions.assertEquals(1, request.stringParameters().size());
		Assertions.assertTrue(request.pathParameters().isEmpty());
	}
}
