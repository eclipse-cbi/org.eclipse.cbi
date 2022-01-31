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

import java.util.Date;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

public class ExceptionHandler {

	private final Log log;
	private final boolean continueOnFail;

	public ExceptionHandler(Log log, boolean continueOnFail) {
		this.log = log;
		this.continueOnFail = continueOnFail;
	}

	public void handleError(String msg) throws MojoExecutionException {
		if (continueOnFail) {
			log.warn("[" + new Date() + "] " + msg);
		} else {
			throw new MojoExecutionException("[" + new Date() + "] " + msg);
		}
	}

	public void handleError(String msg, Exception e) throws MojoExecutionException {
		if (continueOnFail) {
			log.warn("[" + new Date() + "] " + msg, e);
		} else {
			throw new MojoExecutionException("[" + new Date() + "] " + msg, e);
		}
	}
}
