/*******************************************************************************
 * Copyright (c) 2024 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Thomas Neidhart - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.windows;

import com.google.common.base.Strings;
import org.eclipse.cbi.webservice.util.PropertiesReader;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Properties reader of {@link JSigner}.
 */
public class JSignerProperties {

	private static final String JSIGN_URL = "windows.jsign.url";
	private static final String JSIGN_DESCRIPTION = "windows.jsign.description";

	private static final String JSIGN_STORETYPE = "windows.jsign.storetype";
	private static final String JSIGN_STOREPASS = "windows.jsign.storepass";
	private static final String JSIGN_KEYSTORE = "windows.jsign.keystore";
	private static final String JSIGN_KEY_ALIAS = "windows.jsign.keyalias";
	private static final String JSIGN_CERTCHAIN = "windows.jsign.certchain";
	private static final String JSIGN_KMS_CREDENTIALS = "windows.jsign.kms.credentials";

	private static final String JSIGN_TIMESTAMPURL = "windows.jsign.timestampurl";

	private final PropertiesReader propertiesReader;

	/**
	 * Default constructor.
	 *
	 * @param propertiesReader
	 *            the properties reader that will be used to read configuration
	 *            value.
	 */
	public JSignerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}

	public String getDescription() {
		return propertiesReader.getString(JSIGN_DESCRIPTION);
	}

	public URI getURI() {
		String value = propertiesReader.getString(JSIGN_URL);
		try {
			return new URI(value);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Property '" + JSIGN_URL + "' must be a valid URI (currently '" + value + "')", e);
		}
	}

	public String getStoreType() {
		return propertiesReader.getString(JSIGN_STORETYPE);
	}

	public String getStorePass() {
		return propertiesReader.getString(JSIGN_STOREPASS);
	}

	public String getKeystore() {
		return propertiesReader.getString(JSIGN_KEYSTORE);
	}

	/**
	 * Reads and returns the name of the alias of the key to be used in the
	 * keystore.
	 *
	 * @return the name of the alias of the key to be used in the keystore.
	 */
	public String getKeyAlias() {
		return propertiesReader.getString(JSIGN_KEY_ALIAS);
	}

	public Path getCertificateChain() {
		String certchain = propertiesReader.getString(JSIGN_CERTCHAIN, "");
		if (!Strings.isNullOrEmpty(certchain)) {
			return propertiesReader.getRegularFile(JSIGN_CERTCHAIN);
		} else {
			throw new IllegalArgumentException("No certificate chain file configured");
		}
	}

	public List<URI> getTimestampURIs() {
		List<URI> result = new ArrayList<>();

		int i = 1;
		String value;

		do {
			String propertyKey = JSIGN_TIMESTAMPURL + "." + i++;
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
			throw new IllegalArgumentException("No timeserver uri configured, at least property '" + JSIGN_TIMESTAMPURL + ".1' is missing");
		}

		return result;
	}

	public Path getGoogleCloudCredentials() {
		String credentials = propertiesReader.getString(JSIGN_KMS_CREDENTIALS, "");
		if (!Strings.isNullOrEmpty(credentials)) {
			return propertiesReader.getRegularFile(JSIGN_KMS_CREDENTIALS);
		} else {
			return null;
		}
	}
}
