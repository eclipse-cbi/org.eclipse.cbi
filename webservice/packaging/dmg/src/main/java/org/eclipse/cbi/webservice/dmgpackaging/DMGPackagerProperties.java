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
package org.eclipse.cbi.webservice.dmgpackaging;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.PropertiesReader;

public class DMGPackagerProperties {

	private static final String DEFAULT_TIMEOUT = Long.toString(TimeUnit.MINUTES.toSeconds(3));
	private static final String TIMEOUT = "dmgpackager.timeout";
	
	// signing
	private static final long DEFAULT_SECURITY_UNLOCK_TIMEOUT = 20;
	private static final long DEFAULT_CODESIGN_TIMEOUT = TimeUnit.MINUTES.toSeconds(10);
	private static final String DEFAULT_CODESIGN_TIMESTAMP_AUTHORITY = "";
	
	private static final String CERTIFICATE_NAME = "macosx.certificate";
	private static final String KEYCHAIN_PASSWORD_FILE = "macosx.keychain.password";
	private static final String KEYCHAIN_PATH = "macosx.keychain";
	private static final String SECURITY_UNLOCK_TIMEOUT = "macosx.security.unlock.timeout";
	private static final String CODESIGN_TIMEOUT = "macosx.codesign.timeout";
	private static final String CODESIGN_TIMESTAMP_AUTHORITY = "macosx.codesign.timestamp";

	private final PropertiesReader propertiesReader;
	
	public DMGPackagerProperties(PropertiesReader propertiesReader) {
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
	
	public String getTimeStampAuthority() {
		return propertiesReader.getString(CODESIGN_TIMESTAMP_AUTHORITY, DEFAULT_CODESIGN_TIMESTAMP_AUTHORITY);
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
