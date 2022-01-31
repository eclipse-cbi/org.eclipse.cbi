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

import org.apache.maven.plugin.logging.Log;

public class MavenLogger implements Logger {

	private final Log log;

	public MavenLogger(Log log) {
		this.log = log;
	}

	@Override
	public void debug(CharSequence content) {
		log.debug(content);
	}

	@Override
	public void debug(CharSequence content, Throwable error) {
		log.debug(content, error);
	}

	@Override
	public void debug(Throwable error) {
		log.debug(error);
	}

	@Override
	public void info(CharSequence content) {
		log.info(content);
	}

	@Override
	public void info(CharSequence content, Throwable error) {
		log.info(content, error);
	}

	@Override
	public void info(Throwable error) {
		log.info(error);
	}

	@Override
	public void warn(CharSequence content) {
		log.warn(content);
	}

	@Override
	public void warn(CharSequence content, Throwable error) {
		log.warn(content, error);
	}

	@Override
	public void warn(Throwable error) {
		log.warn(error);
	}

	@Override
	public void error(CharSequence content) {
		log.error(content);
	}

	@Override
	public void error(CharSequence content, Throwable error) {
		log.error(content, error);
	}

	@Override
	public void error(Throwable error) {
		log.error(error);
	}

}
