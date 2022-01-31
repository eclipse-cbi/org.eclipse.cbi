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

public interface CompletionListener {
	
	void onSuccess(HttpResult result) throws IOException;
	
	void onError(HttpResult error) throws IOException;
}