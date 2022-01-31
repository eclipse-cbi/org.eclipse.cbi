/*******************************************************************************
 * Copyright (c) 2015, 2016 Eclipse Foundation and others.
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

public interface HttpClient {

	boolean send(HttpRequest request, CompletionListener completionListener) throws IOException;
	
	boolean send(HttpRequest request, HttpRequest.Config config, CompletionListener completionListener) throws IOException;

}