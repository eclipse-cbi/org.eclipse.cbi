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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.webservice.servlet.ResponseFacade;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.jimfs.Jimfs;
import com.google.common.net.HttpHeaders;

@RunWith(MockitoJUnitRunner.class)
public class ResponseFacadeTest {

	@Mock private HttpServletResponse response;
	@Mock private HttpSession session;
	@Mock private PrintWriter pw;
	private ByteArrayOutputStream baos;
	
	@SuppressWarnings("resource")
	@Before
	public void before() throws IOException {
		baos = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new ForwardingServletOutputStream(baos));
		when(response.getWriter()).thenReturn(pw);
		when(session.getId()).thenReturn("12345");
	}
	
	@Test
	public void testReplyPlain() throws IOException {
		ResponseFacade responseFacade = ResponseFacade.builder().servletResponse(response).session(session).build();
		responseFacade.replyPlain(300, "a message");
		verify(pw).println("a message [SESSION=12345]");
		verify(response).setContentType("text/plain");
		verify(response).setStatus(300);
	}
	
	@Test
	public void testReplyPlainMultipleMessage() throws IOException {
		ResponseFacade responseFacade = ResponseFacade.builder().servletResponse(response).session(session).build();
		responseFacade.replyPlain(200, "a message", "a second message");
		verify(pw).println("a message\na second message [SESSION=12345]");
		verify(response).setContentType("text/plain");
		verify(response).setStatus(200);
	}
	
	@Test
	public void testReplyWithFile() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			ResponseFacade responseFacade = ResponseFacade.builder().servletResponse(response).session(session).build();
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("aTestFile.txt"), 3);
			responseFacade.replyWithFile("application/mine", "test.ext", path);
			assertEquals(SampleFilesGenerators.loremIpsumRepeated(3), baos.toString());
			verify(response).setContentType("application/mine");
			verify(response).setStatus(200);
			verify(response).addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=test.ext");
			verify(response).addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0, no-cache, no-store");
			verify(response).addHeader(HttpHeaders.PRAGMA, "no-cache");
		}
	}
	
	@Test
	public void internalServerError() throws IOException {
		ResponseFacade responseFacade = ResponseFacade.builder().servletResponse(response).session(session).build();
		responseFacade.internalServerError("a message", "a second message");
		verify(pw).println("a message\na second message [SESSION=12345]");
		verify(response).setContentType("text/plain");
		verify(response).setStatus(500);
	}
	
	@Test
	public void internalServerErrorWithException() throws IOException {
		ResponseFacade responseFacade = ResponseFacade.builder().servletResponse(response).session(session).build();
		Exception thrownException = new NullPointerException();
		responseFacade.internalServerError(thrownException, "a message", "a second message");
		verify(pw).println("a message\na second message [SESSION=12345]");
		verify(pw).println(thrownException);
		verify(response).setContentType("text/plain");
		verify(response).setStatus(500);
	}

	private static final class ForwardingServletOutputStream extends ServletOutputStream {
		private final ByteArrayOutputStream baos;
		
		ForwardingServletOutputStream(ByteArrayOutputStream baos) { this.baos = baos; }
		
		@Override
		public void write(int b) throws IOException { this.baos.write(b); }
	
		@Override
		public void setWriteListener(WriteListener writeListener) {}
	
		@Override
		public boolean isReady() { return false; }
	}
}
