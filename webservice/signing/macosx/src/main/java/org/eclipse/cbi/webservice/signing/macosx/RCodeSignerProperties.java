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

import org.eclipse.cbi.webservice.util.PropertiesReader;

import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * A reader of {@link Properties} of {@link SigningServer}. It provides
 * sanity checks and sensible default values for optional properties.
 */
public class RCodeSignerProperties {

	private static final long DEFAULT_CODESIGN_TIMEOUT = TimeUnit.MINUTES.toSeconds(10);
	private static final String DEFAULT_CODESIGN_TIMESTAMP_AUTHORITY = "";

	private static final String RCODESIGN = "macosx.rcodesign";
	private static final String IDENTITY_APPLICATION_KEYCHAIN_PATH = "macosx.identity.application.keychain";
	private static final String IDENTITY_APPLICATION_KEYCHAIN_PASSWORD_FILE = "macosx.identity.application.keychain.password-file";
	private static final String IDENTITY_INSTALLER_KEYCHAIN_PATH = "macosx.identity.installer.keychain";
	private static final String IDENTITY_INSTALLER_KEYCHAIN_PASSWORD_FILE = "macosx.identity.installer.keychain.password-file";
	private static final String CODESIGN_TIMEOUT = "macosx.codesign.timeout";
	private static final String CODESIGN_TIMESTAMP_AUTHORITY = "macosx.codesign.timestamp";

	private final PropertiesReader propertiesReader;

	public RCodeSignerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}

	public Path getRCodeSign() {
		return propertiesReader.getPath(RCODESIGN);
	}

	public Path getIdentityApplicationKeychainPath() {
		return propertiesReader.getRegularFile(IDENTITY_APPLICATION_KEYCHAIN_PATH);
	}

	public Path getIdentityApplicationKeychainPasswordFile() {
		return propertiesReader.getRegularFile(IDENTITY_APPLICATION_KEYCHAIN_PASSWORD_FILE);
	}

	public Path getIdentityInstallerKeychainPath() {
		return propertiesReader.getRegularFile(IDENTITY_INSTALLER_KEYCHAIN_PATH);
	}

	public Path getIdentityInstallerKeychainPasswordFile() {
		return propertiesReader.getRegularFile(IDENTITY_INSTALLER_KEYCHAIN_PASSWORD_FILE);
	}

	public long getCodesignTimeout() {
		return propertiesReader.getLong(CODESIGN_TIMEOUT, DEFAULT_CODESIGN_TIMEOUT);
	}

	public String getTimeStampAuthority() {
		return propertiesReader.getString(CODESIGN_TIMESTAMP_AUTHORITY, DEFAULT_CODESIGN_TIMESTAMP_AUTHORITY);
	}
}
