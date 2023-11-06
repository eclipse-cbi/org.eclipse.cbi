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
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.windows;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.cbi.webservice.util.PropertiesReader;

public class OSSLSigncodeProperties {

	private static final String OSSLSIGNCODE_TIMESTAMPURL = "windows.osslsigncode.timestampurl";
	private static final String OSSLSIGNCODE_URL = "windows.osslsigncode.url";
	private static final String OSSLSIGNCODE_DESCRIPTION = "windows.osslsigncode.description";
	private static final String OSSLSIGNCODE_PKC12_PASSWORD = "windows.osslsigncode.pkc12.password";
	private static final String OSSLSIGNCODE_PKCS12 = "windows.osslsigncode.pkcs12";
	private static final long OSSLSIGNCODE_TIMEOUT_DEFAULT = 120L;
	private static final String OSSLSIGNCODE_TIMEOUT = "windows.osslsigncode.timeout";
	private static final String OSSLSIGNCODE = "windows.osslsigncode";
	private final PropertiesReader propertiesReader;

	public OSSLSigncodeProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}

	public Path getOSSLSigncode() {
		return propertiesReader.getPath(OSSLSIGNCODE);
	}

	public long getTimeout() {
		return propertiesReader.getLong(OSSLSIGNCODE_TIMEOUT, OSSLSIGNCODE_TIMEOUT_DEFAULT);
	}

	public Path getPKCS12() {
		return propertiesReader.getPath(OSSLSIGNCODE_PKCS12);
	}

	public String getPKCS12Password() {
		return propertiesReader.getFileContent(OSSLSIGNCODE_PKC12_PASSWORD);
	}

	public String getDescription() {
		return propertiesReader.getString(OSSLSIGNCODE_DESCRIPTION);
	}

	public URI getURI() {
		String value = propertiesReader.getString(OSSLSIGNCODE_URL);
		try {
			return new URI(value);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Property '" + OSSLSIGNCODE_URL + "' must be a valid URI (currently '" + value + "')", e); 
		}
	}

	public List<URI> getTimestampURIs() {
		List<URI> result = new ArrayList<>();

		int i = 1;
		String value;

		do {
			String propertyKey = OSSLSIGNCODE_TIMESTAMPURL + "." + i++;
			value = propertiesReader.getString(propertyKey, "undefined");
			if (!value.equals("undefined")) {
				try {
					result.add(new URI(value));
				} catch (URISyntaxException e) {
					throw new IllegalArgumentException("Property '" + propertyKey + "' must be a valid URI (currently '" + value + "')", e);
				}
			}
		} while (!value.equals("undefined"));

		if (result.isEmpty()) {
			throw new IllegalArgumentException("No timeserver uri configured, at least property '" + OSSLSIGNCODE_TIMESTAMPURL + ".1' is missing");
		}

		return result;
	}

}
