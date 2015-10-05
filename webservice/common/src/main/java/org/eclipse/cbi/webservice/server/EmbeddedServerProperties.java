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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.eclipse.cbi.webservice.util.PropertiesReader;

/**
 * A reader of {@link Properties} of {@link EmbeddedServer}. It provides
 * sanity checks and sensible default values for optional properties.
 */
public final class EmbeddedServerProperties implements EmbeddedServerConfiguration {

	private static final int DEFAULT_SERVER_PORT = 8080;
	private static final boolean DEFAULT_SERVICE_PATH_SPEC_VERSIONED = true;
	
	private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
	
	/** The key for the server access log file property  */
	public static final String ACCESS_LOG_FILE = "server.access.log";
	/** The key for the server temporary folder property  */
	public static final String TEMP_FOLDER = "server.temp.folder";
	/** The key for the server port number property  */
	public static final String SERVER_PORT = "server.port";
	/** The key for the server service path specification property  */
	public static final String SERVICE_PATH_SPEC = "server.service.pathspec";
	/** The key for the server option whether the service version should be happened to the service path spec. */
	public static final String SERVICE_PATH_SPEC_VERSIONED = "server.service.pathspec.versioned";
	
	private final PropertiesReader propertiesReader;

	/**
	 * Default constructor.
	 * 
	 * @param propertiesReader
	 *            the properties from which configuration will be retrieved.
	 */
	public EmbeddedServerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}
	
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
	@Override
	public Path getAccessLogFile() {
		final Path logFilePath = propertiesReader.getPath(ACCESS_LOG_FILE);
		final Path logFileParent = logFilePath.getParent();
		if (!Files.exists(logFileParent)) {
			try { 
				Files.createDirectories(logFileParent);
			} catch (IOException e) {
				throw new IllegalStateException("Folder '" + logFileParent + "' can not be created to contain the log files", e);
			}
		}
		return logFilePath;
	}

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
	@Override
	public Path getTempFolder() {
		Path tempFolder = propertiesReader.getPath(TEMP_FOLDER, System.getProperty(JAVA_IO_TMPDIR));
		if (!Files.exists(tempFolder)) {
			try {
				Files.createDirectories(tempFolder);
			} catch (IOException e) {
				throw new IllegalStateException("Temporary folder '" + tempFolder + "' can not be created", e);
			}
		} else if (!Files.isDirectory(tempFolder)) {
			throw new IllegalStateException("Temporary folder '" + tempFolder + "' must be a directory");
		} 
		return tempFolder;
	}

	/**
	 * Gets the {@value #SERVER_PORT} property from the properties or
	 * {@value #DEFAULT_SERVER_PORT} if not specified in the properties.
	 * 
	 * @return the server port
	 * @throws IllegalStateException
	 *             if the specified server port can not be parsed as a valid
	 *             integer.
	 */
	@Override
	public int getServerPort() {
		return propertiesReader.getInt(SERVER_PORT, DEFAULT_SERVER_PORT);
	}

	/**
	 * Gets the {@value #SERVICE_PATH_SPEC} property from the properties
	 * 
	 * @return the service path specification
	 * @throws IllegalStateException
	 *             if the property is not specified in the properties
	 */
	@Override
	public String getServicePathSpec() {
		return propertiesReader.getString(SERVICE_PATH_SPEC);
	}
	
	/**
	 * Gets the {@value #SERVICE_PATH_SPEC_VERSIONED} property from the properties
	 * 
	 * @return true if the service version should be appended to service path spec.
	 */
	@Override
	public boolean isServiceVersionAppendedToPathSpec() {
		return propertiesReader.getBoolean(SERVICE_PATH_SPEC_VERSIONED, DEFAULT_SERVICE_PATH_SPEC_VERSIONED);
	}
	
	/**
	 * Gets all properties starting with {@code log4j.*}.
	 * 
	 * @return all properties starting with {@code log4j.*}.
	 */
	@Override
	public Properties getLog4jProperties() {
		Properties p = new Properties();
		propertiesReader.toMap().entrySet().stream()
			.filter(e -> e.getKey().startsWith("log4j."))
			.forEach(e -> p.setProperty(e.getKey(), e.getValue()));
		return p;
	}
}