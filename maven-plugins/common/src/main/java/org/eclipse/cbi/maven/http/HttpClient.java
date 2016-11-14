/*******************************************************************************
 * Copyright (c) 2015, 2016 Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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