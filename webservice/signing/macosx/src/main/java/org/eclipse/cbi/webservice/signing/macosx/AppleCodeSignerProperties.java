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
package org.eclipse.cbi.webservice.signing.macosx;

import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.PropertiesReader;

/**
 * A reader of {@link Properties} of {@link SigningServer}. It provides
 * sanity checks and sensible default values for optional properties.
 */
public class AppleCodeSignerProperties {

	private static final long DEFAULT_SECURITY_UNLOCK_TIMEOUT = 20;
	private static final long DEFAULT_CODESIGN_TIMEOUT = TimeUnit.MINUTES.toSeconds(10);
	private static final String DEFAULT_CODESIGN_TIMESTAMP_AUTHORITY = "";

	private static final String IDENTITY_NAME_APPLICATION = "macosx.identity.application";
	private static final String IDENTITY_NAME_INSTALLER = "macosx.identity.installer";
	private static final String KEYCHAIN_PASSWORD_FILE = "macosx.keychain.password";
	private static final String KEYCHAIN_PATH = "macosx.keychain";
	private static final String SECURITY_UNLOCK_TIMEOUT = "macosx.security.unlock.timeout";
	private static final String CODESIGN_TIMEOUT = "macosx.codesign.timeout";
	private static final String CODESIGN_TIMESTAMP_AUTHORITY = "macosx.codesign.timestamp";

	private final PropertiesReader propertiesReader;

	public AppleCodeSignerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}

	public Path getKeyChain() {
		return propertiesReader.getRegularFile(KEYCHAIN_PATH);
	}

	public String getKeyChainPassword() {
		return propertiesReader.getFileContent(KEYCHAIN_PASSWORD_FILE);
	}

	public String getIdentityApplication() {
		return propertiesReader.getString(IDENTITY_NAME_APPLICATION);
	}

	public String getIdentityInstaller() {
		return propertiesReader.getString(IDENTITY_NAME_INSTALLER);
	}

	public long getSecurityUnlockTimeout() {
		return propertiesReader.getLong(SECURITY_UNLOCK_TIMEOUT, DEFAULT_SECURITY_UNLOCK_TIMEOUT);
	}

	public long getCodesignTimeout() {
		return propertiesReader.getLong(CODESIGN_TIMEOUT, DEFAULT_CODESIGN_TIMEOUT);
	}

	public String getTimeStampAuthority() {
		return propertiesReader.getString(CODESIGN_TIMESTAMP_AUTHORITY, DEFAULT_CODESIGN_TIMESTAMP_AUTHORITY);
	}
}
