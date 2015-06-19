/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.common;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * An {@link IOException} that wraps a {@link MojoExecutionException}.
 */
public final class MojoExecutionExceptionWrapper extends IOException {

	private static final long serialVersionUID = 3043861903313797221L;

	public MojoExecutionExceptionWrapper(MojoExecutionException e) {
		super(e);
	}

	@Override
	public synchronized MojoExecutionException getCause() {
		return (MojoExecutionException) super.getCause();
	}
}
