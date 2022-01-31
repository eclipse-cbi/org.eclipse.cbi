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
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * An {@link IOException} that wraps a {@link MojoExecutionException}.
 */
public final class MojoExecutionIOExceptionWrapper extends IOException {

	private static final long serialVersionUID = 3043861903313797221L;

	public MojoExecutionIOExceptionWrapper(MojoExecutionException e) {
		super(e);
	}

	@Override
	public synchronized MojoExecutionException getCause() {
		return (MojoExecutionException) super.getCause();
	}
}
