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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;

import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

public class OSSLCodesignerTest {

	@TempDir
	Path tempFolder;

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void testReplaceSignatures(boolean replace) throws IOException {
		Path file = Files.writeString(tempFolder.resolve("test.exe"), "executable");
		ProcessExecutor executor = mock(ProcessExecutor.class);
		when(executor.exec(any(), any(StringBuilder.class), eq(30L), eq(TimeUnit.SECONDS)))
				.thenAnswer(invocation -> {
					ImmutableList<String> command = invocation.getArgument(0);
					Path out = Path.of(command.get(command.indexOf("-out") + 1));
					Files.writeString(out, "signed executable");
					return 0;
				});
		OSSLCodesigner signer = OSSLCodesigner.builder()
				.osslsigncode(Path.of("osslsigncode"))
				.pkcs12(tempFolder.resolve("test.p12"))
				.pkcs12Password("password")
				.description("test")
				.uri(URI.create("https://eclipse.org"))
				.timestampURIs(List.of(URI.create("http://timestamp.digicert.com")))
				.timeout(30)
				.tempFolder(tempFolder)
				.processExecutor(executor)
				.build();

		assertEquals(file, signer.sign(file, replace));

		ArgumentCaptor<ImmutableList<String>> command = ArgumentCaptor.captor();
		verify(executor).exec(command.capture(), any(StringBuilder.class), eq(30L), eq(TimeUnit.SECONDS));
		assertEquals(!replace, command.getValue().contains("-nest"));
		assertEquals(file.toString(), command.getValue().get(command.getValue().indexOf("-in") + 1));
		assertEquals("signed executable", Files.readString(file));
	}
}
