/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.servlet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.jimfs.Jimfs;

@SuppressWarnings("javadoc")
@RunWith(MockitoJUnitRunner.class)
public class RequestFacadeTest {

	@Mock HttpServletRequest request;
	@Mock Part part;

	@Test(expected=NullPointerException.class)
	public void testNullTempFolder() {
		RequestFacade.builder(null).request(request).build();
	}

	@Test(expected=IllegalStateException.class)
	public void testNonExistingTempFolder() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			RequestFacade.builder(fs.getRootDirectories().iterator().next().resolve("tmp")).request(request).build();
		}
	}

	@Test(expected=IllegalStateException.class)
	public void testPlainFileTempFolder() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			RequestFacade.builder(Files.createFile(fs.getRootDirectories().iterator().next().resolve("tmp"))).request(request).build();
		}
	}

	@Test(expected=NullPointerException.class)
	public void testNullRequest() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			RequestFacade.builder(Files.createDirectory(fs.getRootDirectories().iterator().next().resolve("tmp"))).request(null).build();
		}
	}

	@Test
	public void testNullParameter() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			when(request.getParameter("testParam")).thenReturn(null);
			assertEquals(Optional.empty(), facade.getParameter("testParam"));
		}
	}

	@Test
	public void testParameter() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			when(request.getParameter("testParam")).thenReturn("paramValue");
			assertEquals(Optional.of("paramValue"), facade.getParameter("testParam"));
		}
	}

	@Test
	public void testNullPartInputStream() throws IOException, ServletException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			assertEquals(Optional.empty(), facade.getPartInputStream("testPart"));
		}
	}

	@Test
	public void testPartInputStream() throws IOException, ServletException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
			when(request.getPart("testPart")).thenReturn(part);
			when (part.getInputStream()).thenReturn(stream);

			assertEquals(Optional.of(stream), facade.getPartInputStream("testPart"));
		}
	}

	@Test
	public void testPartPath() throws IOException, ServletException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			when(request.getPart("testPart")).thenReturn(part);
			when(part.getSubmittedFileName()).thenReturn("submittedFilename.txt");
			doAnswer(invocation -> {
				String filename = invocation.getArgument(0);
				Files.createFile(fs.getRootDirectories().iterator().next().resolve("tmp").resolve(filename));
				return null;
			}).when(part).write(anyString());

			Path path = facade.getPartPath("testPart").get();
			assertTrue(path.toString().startsWith(fs.getRootDirectories().iterator().next() + "tmp/"));
			assertTrue(path.toString().endsWith("submittedFilename.txt"));

			Path pathWithPrefix = facade.getPartPath("testPart", "prefix-").get();
			assertTrue(pathWithPrefix.toString().startsWith(fs.getRootDirectories().iterator().next() + "tmp/prefix-"));
			assertTrue(pathWithPrefix.toString().endsWith("submittedFilename.txt"));

			Path pathWithPrefixAndSuffix = facade.getPartPath("testPart", "prefix-", "-suffix").get();
			assertTrue(pathWithPrefixAndSuffix.toString().startsWith(fs.getRootDirectories().iterator().next() + "tmp/prefix-"));
			assertTrue(pathWithPrefixAndSuffix.toString().endsWith("submittedFilename.txt-suffix"));
		}
	}

	@Test
	public void testPartPathCantWrite() throws IOException, ServletException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			when(request.getPart("testPart")).thenReturn(part);
			when(part.getSubmittedFileName()).thenReturn("submittedFilename");
			doNothing().when(part).write(anyString());

			assertEquals(Optional.empty(), facade.getPartPath("testPart"));
		}
	}

	@Test
	public void testNullPartSubmittedFilename() throws IOException, ServletException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			when(request.getPart("testPart")).thenReturn(null);

			assertEquals(Optional.empty(), facade.getSubmittedFileName("testPart"));
		}
	}

	@Test
	public void testPartSubmittedFilename() throws IOException, ServletException {
		try (FileSystem fs = Jimfs.newFileSystem(); RequestFacade facade = createRequestFacadeUnderTest(fs)) {
			when(request.getPart("testPart")).thenReturn(part);
			when(part.getSubmittedFileName()).thenReturn("submittedFilename");

			assertEquals(Optional.of("submittedFilename"), facade.getSubmittedFileName("testPart"));
		}
	}

	private RequestFacade createRequestFacadeUnderTest(FileSystem fs)
			throws IOException {
		return RequestFacade.builder(Files.createDirectory(fs.getRootDirectories().iterator().next().resolve("tmp"))).request(request).build();
	}
}
