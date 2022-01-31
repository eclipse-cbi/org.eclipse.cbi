/*******************************************************************************
 * Copyright (c) 2014, 2015 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.Path;

public interface HttpResult {
	
	int statusCode();
	
	String reason();
	
	long copyContent(Path target, CopyOption... options) throws IOException;
	
	long copyContent(OutputStream output) throws IOException;

	long contentLength();

	Charset contentCharset();
}