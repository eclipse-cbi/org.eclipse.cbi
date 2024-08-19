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
package org.eclipse.cbi.webservice.util.function;

import java.io.Serial;

/**
 * A {@link RuntimeException} that wrapped another exception.
 */
public class WrappedException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 4165005396665474198L;

	/**
	 * Default constructor
	 *
	 * @param wrapped
	 *            the wrapped exception.
	 */
	public WrappedException(Throwable wrapped) {
		super(wrapped);
	}
}
