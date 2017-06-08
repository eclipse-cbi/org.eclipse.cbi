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
package org.eclipse.cbi.webservice.server;

import java.nio.file.Path;
import java.util.Properties;

public interface EmbeddedServerConfiguration {

	/**
	 * Returns the path to the server access log file property.
	 * @return the path to the server access log file property.
	 */
	Path getAccessLogFile();

	/**
	 * Returns the path to the temporary folder.
	 *
	 * @return the path to the temporary folder.
	 */
	Path getTempFolder();

	/**
	 * Returns the server port.
	 *
	 * @return the server port.
	 */
	int getServerPort();

	/**
	 * Returns the service path specification.
	 *
	 * @return the service path specification.
	 */
	String getServicePathSpec();

	/**
	 * Returns whether the service version should be appended to service path spec.
	 *
	 * @return true if the service version should be appended to service path
	 *         spec.
	 */
	boolean isServiceVersionAppendedToPathSpec();

	/**
	 * Returns Log4j related properties.
	 *
	 * @return Log4j related properties.
	 */
	Properties getLog4jProperties();

}