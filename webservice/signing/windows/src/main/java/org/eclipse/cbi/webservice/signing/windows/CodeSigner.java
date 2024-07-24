/*******************************************************************************
 * Copyright (c) 2024 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Thomas Neidhart - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.windows;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for different tools to implement code signing.
 */
public interface CodeSigner {
	Path sign(Path file) throws IOException;
}
