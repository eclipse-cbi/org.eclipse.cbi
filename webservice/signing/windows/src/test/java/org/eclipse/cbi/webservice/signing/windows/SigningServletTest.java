/*******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.windows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

public class SigningServletTest {

	@TempDir
	Path tempFolder;

	@ParameterizedTest
	@CsvSource({
		"test.exe, , false", "test.exe, false, false", "test.exe, true, true",
		"test.dll, , false", "test.dll, false, false", "test.dll, true, true",
		"test.msi, , false", "test.msi, false, false", "test.msi, true, true"
	})
	public void testReplaceParameter(String filename, String replace, boolean expectedReplace) throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		Part file = mock(Part.class);
		CodeSigner signer = mock(CodeSigner.class);
		when(request.getPart("file")).thenReturn(file);
		when(request.getParameter("replace")).thenReturn(replace);
		when(file.getSubmittedFileName()).thenReturn(filename);
		doAnswer(invocation -> {
			Files.writeString(tempFolder.resolve(invocation.<String>getArgument(0)), "executable");
			return null;
		}).when(file).write(anyString());
		when(signer.sign(any(Path.class), eq(expectedReplace))).thenAnswer(invocation -> invocation.getArgument(0));
		when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

		SigningServlet servlet = SigningServlet.builder().codesigner(signer).tempFolder(tempFolder).build();
		servlet.doPost(request, response);

		verify(signer).sign(any(Path.class), eq(expectedReplace));
		verify(response).setStatus(HttpServletResponse.SC_OK);
		verify(file).delete();
	}
}
