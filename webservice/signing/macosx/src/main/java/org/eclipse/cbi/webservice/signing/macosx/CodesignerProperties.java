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
package org.eclipse.cbi.webservice.signing.macosx;

import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.PropertiesReader;

/**
 * A reader of {@link Properties} of {@link SigningServer}. It provides
 * sanity checks and sensible default values for optional properties.
 */
public class CodesignerProperties {

	private static final long DEFAULT_SECURITY_UNLOCK_TIMEOUT = 20;
	private static final long DEFAULT_CODESIGN_TIMEOUT = TimeUnit.MINUTES.toSeconds(10);
	private static final String CERTIFICATE_NAME = "macosx.certificate";
	private static final String KEYCHAIN_PASSWORD_FILE = "macosx.keychain.password";
	private static final String KEYCHAIN_PATH = "macosx.keychain";
	private static final String SECURITY_UNLOCK_TIMEOUT = "macosx.security.unlock.timeout";
	private static final String CODESIGN_TIMEOUT = "macosx.codesign.timeout";

	private final PropertiesReader propertiesReader;

	public CodesignerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}

	public Path getKeychain() {
		return propertiesReader.getRegularFile(KEYCHAIN_PATH);
	}

	public String getKeychainPassword() {
		return propertiesReader.getFileContent(KEYCHAIN_PASSWORD_FILE);
	}

	public String getCertificate() {
		return propertiesReader.getString(CERTIFICATE_NAME);
	}

	public long getSecurityUnlockTimeout() {
		return propertiesReader.getLong(SECURITY_UNLOCK_TIMEOUT, DEFAULT_SECURITY_UNLOCK_TIMEOUT);
	}

	public long getCodesignTimeout() {
		return propertiesReader.getLong(CODESIGN_TIMEOUT, DEFAULT_CODESIGN_TIMEOUT);
	}
}
