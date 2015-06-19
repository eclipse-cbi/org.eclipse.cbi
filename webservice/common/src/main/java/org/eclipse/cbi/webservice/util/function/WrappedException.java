/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.util.function;

/**
 * A {@link RuntimeException} that wrapped another exception.
 */
public class WrappedException extends RuntimeException {

	private static final long serialVersionUID = 4165005396665474198L;

	/**
	 * Default construtor
	 * 
	 * @param wrapped
	 *            the wrapped exception.
	 */
	public WrappedException(Throwable wrapped) {
		super(wrapped);
	}
}
