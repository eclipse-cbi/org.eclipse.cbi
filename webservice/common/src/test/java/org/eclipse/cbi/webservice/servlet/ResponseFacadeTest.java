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

import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.jimfs.Jimfs;
import com.google.common.net.HttpHeaders;

@SuppressWarnings("javadoc")
@RunWith(MockitoJUnitRunner.class)
public class ResponseFacadeTest {

	@Mock private HttpServletResponse response;
	@Mock private PrintWriter pw;
	private ByteArrayOutputStream baos;

	@Before
	public void before() throws IOException {
		baos = new ByteArrayOutputStream();
		when(response.getOutputStream()).thenReturn(new ForwardingServletOutputStream(baos));
	}

	@Test
	public void testReplyWithFile() throws IOException {
		try (FileSystem fs = Jimfs.newFileSystem()) {
			ResponseFacade responseFacade = ResponseFacade.builder().servletResponse(response).build();
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("aTestFile.txt"), 3);
			responseFacade.replyWithFile("application/mine", "test.ext", path);
			assertEquals(SampleFilesGenerators.loremIpsumRepeated(3), baos.toString());
			verify(response).setContentType("application/mine");
			verify(response).setStatus(200);
			verify(response).addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.ext\"");
			verify(response).addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0,must-revalidate,no-cache,no-store");
			verify(response).addHeader(HttpHeaders.PRAGMA, "no-cache");
		}
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
