/*******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.jar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JarSignerTest {

	@TempDir
	Path tempDir;

	@Test
	public void classpathEntries() {
		String sep = File.pathSeparator;
		assertEquals(List.of(), JarSigner.classpathEntries(null));
		assertEquals(List.of(), JarSigner.classpathEntries(""));
		assertEquals(List.of(), JarSigner.classpathEntries("--add-modules java.sql"));
		assertEquals(List.of("/a.jar"), JarSigner.classpathEntries("-cp /a.jar --add-modules java.sql"));
		assertEquals(List.of("/a.jar", "/b/*"), JarSigner.classpathEntries("-Xmx1g -classpath /a.jar" + sep + "/b/*"));
		assertEquals(List.of("/a.jar"), JarSigner.classpathEntries("--class-path  /a.jar"));
		assertEquals(List.of("/a.jar", "/b.jar"), JarSigner.classpathEntries("--class-path=/a.jar" + sep + sep + "/b.jar"));
		assertEquals(List.of(), JarSigner.classpathEntries("-cp"));
	}

	@Test
	public void buildWithExistingClasspathEntries() throws IOException {
		Path jar = Files.createFile(tempDir.resolve("provider.jar"));
		assertDoesNotThrow(() -> build("-cp " + jar + File.pathSeparator + tempDir.resolve("*") + " --add-modules java.sql"));
	}

	@Test
	public void buildWithMissingClasspathEntry() {
		// e.g., the jar of the previous version of the service, as configured by a stale ConfigMap
		Path missing = tempDir.resolve("jar-signing-service-1.5.7.jar");
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> build("-cp " + missing + " --add-modules java.sql"));
		assertTrue(e.getMessage().contains(missing.toString()), e.getMessage());
	}

	@Test
	public void buildWithMissingWildcardDirectory() {
		assertThrows(IllegalStateException.class, () -> build("-cp " + tempDir.resolve("missing").resolve("*")));
	}

	private static JarSigner build(String javaArgs) {
		JarSignerConfiguration configuration = mock(JarSignerConfiguration.class);
		when(configuration.getTimeout()).thenReturn(120L);
		when(configuration.getJavaArgs()).thenReturn(javaArgs);
		return JarSigner.builder()
				.configuration(configuration)
				.processExecutor(mock(ProcessExecutor.class))
				.build();
	}
}
