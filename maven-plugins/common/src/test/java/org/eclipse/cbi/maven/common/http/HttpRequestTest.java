package org.eclipse.cbi.maven.common.http;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.cbi.maven.http.HttpRequest;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class HttpRequestTest {

	@Test(expected=NullPointerException.class)
	public void testNullPathParam() throws IOException {
		HttpRequest.on(URI.create("localhost"))
			.withParam("file", (Path) null).build();
	}
	
	@Test(expected=NullPointerException.class)
	public void testNullPathParamName() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			HttpRequest.on(URI.create("localhost"))
				.withParam(null, Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("/file"))).build();
		}
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testEmptyPathParamName() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			HttpRequest.on(URI.create("localhost"))
				.withParam("", Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("/file"))).build();
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNonExistingPathParam() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			HttpRequest.on(URI.create("localhost"))
				.withParam("file", fs.getPath("/path/to/file")).build();
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testFolderPathParam() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			HttpRequest.on(URI.create("localhost"))
				.withParam("file", Files.createDirectories(fs.getPath("/path/to/folder"))).build();
		}
	}
	
	@Test
	public void testAddPathParam() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path file = Files.createFile(Files.createDirectories(fs.getPath("/path/to")).resolve("/file"));
			HttpRequest request = HttpRequest.on(URI.create("localhost"))
				.withParam("file", file).build();
			Assert.assertEquals(file, request.pathParameters().get("file"));
			Assert.assertEquals(1, request.pathParameters().size());
			Assert.assertTrue(request.stringParameters().isEmpty());
		}
	}
	
	@Test(expected=NullPointerException.class)
	public void testNullStringParam() throws IOException {
		HttpRequest.on(URI.create("localhost"))
			.withParam("param", (String) null).build();
	}
	
	@Test(expected=NullPointerException.class)
	public void testNullStringParamName() throws IOException {
		HttpRequest.on(URI.create("localhost"))
			.withParam(null, "value").build();
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testEmptyStringParamName() throws IOException {
		HttpRequest.on(URI.create("localhost"))
			.withParam("", "value").build();
	}
	
	@Test
	public void testAddStringParamName() throws IOException {
		HttpRequest request = HttpRequest.on(URI.create("localhost"))
				.withParam("name", "value").build();
		Assert.assertEquals("value", request.stringParameters().get("name"));
		Assert.assertEquals(1, request.stringParameters().size());
		Assert.assertTrue(request.pathParameters().isEmpty());
	}
	
	@Test
	public void testEmptyStringParam() throws IOException {
		HttpRequest request = HttpRequest.on(URI.create("localhost"))
				.withParam("name", "").build();
		Assert.assertEquals("", request.stringParameters().get("name"));
		Assert.assertEquals(1, request.stringParameters().size());
		Assert.assertTrue(request.pathParameters().isEmpty());
	}
}
