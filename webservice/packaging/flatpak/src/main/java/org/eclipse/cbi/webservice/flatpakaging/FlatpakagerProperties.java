/*******************************************************************************
 * Copyright (c) 2018, 2022 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.flatpakaging;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.PropertiesReader;

/**
 * Reads the Flatpak-specific server properties from the configuration file.
 */
public class FlatpakagerProperties {

	private static final String DEFAULT_TIMEOUT = Long.toString(TimeUnit.MINUTES.toSeconds(5));
	private static final String TIMEOUT = "flatpak.timeout";

	private static final String DEFAULT_GPGHOME = Paths.get(System.getProperty("user.home"), ".gnupg").toString();
	private static final String GPGHOME = "flatpak.gpghome";
	private static final String GPGKEY = "flatpak.gpgkey";

	private final PropertiesReader propertiesReader;

	public FlatpakagerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}

	public Path getGpghome() {
		return propertiesReader.getPath(GPGHOME, DEFAULT_GPGHOME);
	}

	public String getGpgkey() {
		return propertiesReader.getString(GPGKEY);
	}

	public long getTimeout() {
		String timeout = propertiesReader.getString(TIMEOUT, DEFAULT_TIMEOUT);
		try {
			return Long.parseLong(timeout);
		} catch (NumberFormatException e) {
			throw new IllegalStateException("'" + TIMEOUT + "' '" + timeout + "' must be a valid long integer", e);
		}
	}
}
