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
	 * Gets the {@value #ACCESS_LOG_FILE} property from the properties and
	 * return the corresponding {@link Path}.
	 * <p>
	 * If the parent folder doesn't exist, it will be created.
	 *
	 * @return the path to the access log file
	 * @throws IllegalStateException
	 *             if the property is not specified or if the parent folder
	 *             doesn't exist and can't be created.
	 */
	Path getAccessLogFile();

	/**
	 * Gets the {@value #TEMP_FOLDER} property from the properties and return
	 * the corresponding {@link Path} or the default Java temporary folder (as
	 * denoted by {@value #JAVA_IO_TMPDIR} system property) if
	 * {@value #TEMP_FOLDER} is not specified in the properties.
	 * <p>
	 * If the folder doesn't exist, it will be created.
	 *
	 * @return the path to the temporary folder
	 * @throws IllegalStateException
	 *             if the folder doesn't exist and can't be created.
	 */
	Path getTempFolder();

	/**
	 * Gets the {@value #SERVER_PORT} property from the properties or
	 * {@value #DEFAULT_SERVER_PORT} if not specified in the properties.
	 *
	 * @return the server port
	 * @throws IllegalStateException
	 *             if the specified server port can not be parsed as a valid
	 *             integer.
	 */
	int getServerPort();

	/**
	 * Gets the {@value #SERVICE_PATH_SPEC} property from the properties
	 *
	 * @return the service path specification
	 * @throws IllegalStateException
	 *             if the property is not specified in the properties
	 */
	String getServicePathSpec();

	/**
	 * Gets the {@value #SERVICE_PATH_SPEC_VERSIONED} property from the
	 * properties
	 *
	 * @return true if the service version should be appended to service path
	 *         spec.
	 */
	boolean isServiceVersionAppendedToPathSpec();

	/**
	 * Gets all properties starting with {@code log4j.*}.
	 *
	 * @return all properties starting with {@code log4j.*}.
	 */
	Properties getLog4jProperties();

}