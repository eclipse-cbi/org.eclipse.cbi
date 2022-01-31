/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

@SuppressWarnings("javadoc")
public class ProcessExecutorTest {

	@Test
	public void testEcho() throws IOException {
		StringBuilder output = new StringBuilder();
		ProcessExecutor executor = new ProcessExecutor.BasicImpl();
		int exitValue = executor.exec(ImmutableList.of("echo", "Hello World"), output, 10, TimeUnit.SECONDS);
		assertEquals(0, exitValue);
		assertEquals("Hello World\n", output.toString());
	}

}
