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
package org.eclipse.cbi.common.signing;

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
			log.warn(new Date() + " " + msg);
		} else {
			throw new MojoExecutionException(new Date() + " " + msg);
		}
	}
	
	public void handleError(String msg, Exception e) throws MojoExecutionException {
		if (continueOnFail) {
			log.warn(new Date() + " " + msg, e);
		} else {
			throw new MojoExecutionException(new Date() + " " + msg, e);
		}
	}
}
