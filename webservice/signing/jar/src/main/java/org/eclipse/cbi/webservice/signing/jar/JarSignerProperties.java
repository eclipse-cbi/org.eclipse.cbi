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
package org.eclipse.cbi.webservice.signing.jar;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.util.PropertiesReader;

/**
 * Properties reader of {@link JarSigner}.
 */
public class JarSignerProperties implements JarSignerConfiguration {

	private static final long JARSIGNER_TIMEOUT_DEFAULT = TimeUnit.MINUTES.toSeconds(2);
	private static final String JARSIGNER_TIMEOUT = "jarsigner.timeout";
	private static final String JARSIGNER_TSA = "jarsigner.tsa";
	private static final String JARSIGNER_KEYSTORE_PASSWORD = "jarsigner.keystore.password";
	private static final String JARSIGNER_KEYSTORE_ALIAS = "jarsigner.keystore.alias";
	private static final String JARSIGNER_KEYSTORE = "jarsigner.keystore";
	private static final String JARSIGNER_BIN = "jarsigner.bin";
	
	private static final String JARSIGNER_HTTP_PROXY_HOST = "jarsigner.http.proxy.host";
	private static final String JARSIGNER_HTTPS_PROXY_HOST = "jarsigner.https.proxy.host";
	
	private static final String JARSIGNER_HTTP_PROXY_PORT = "jarsigner.http.proxy.port";
	private static final String JARSIGNER_HTTPS_PROXY_PORT = "jarsigner.https.proxy.port";
	
	private final PropertiesReader propertiesReader;

	/**
	 * Default constructor.
	 * 
	 * @param propertiesReader
	 *            the properties reader that will be used to read configuration
	 *            value.
	 */
	public JarSignerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}

	/**
	 * Reads and returns the path to the jarsigner executable.
	 * 
	 * @return the path to the jarsigner executable.
	 */
	@Override
	public Path getJarSigner() {
		return propertiesReader.getRegularFile(JARSIGNER_BIN);
	}

	/**
	 * Reads and returns the path to the keystore file.
	 * 
	 * @return the path to the keystore file.
	 */
	@Override
	public Path getKeystore() {
		return propertiesReader.getRegularFile(JARSIGNER_KEYSTORE);
	}

	/**
	 * Reads and returns the name of the alias of the key to be used in the
	 * keystore.
	 * 
	 * @return the name of the alias of the key to be used in the keystore.
	 */
	@Override
	public String getKeystoreAlias() {
		return propertiesReader.getString(JARSIGNER_KEYSTORE_ALIAS);
	}

	/**
	 * Reads and returns the path to the file containing the password of the
	 * keystore.
	 * 
	 * @return the path to the file containing the password of the keystore.
	 */
	@Override
	public String getKeystorePassword() {
		return propertiesReader.getFileContent(JARSIGNER_KEYSTORE_PASSWORD);
	}

	/**
	 * Reads and returns the URI of the timestamping authority to be used by the
	 * jarsigner command
	 * 
	 * @return the URI of the timestamping authority to be used by the jarsigner
	 *         command
	 */
	@Override
	public URI getTimeStampingAuthority() {
		String value = propertiesReader.getString(JARSIGNER_TSA);
		try {
			return new URI(value);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Property '" + JARSIGNER_TSA + "' must be a valid URI (currently '" + value + "')", e); 
		}
	}

	/**
	 * Reads and returns the timeout of the jarsigner command. If no 
	 * {@value #JARSIGNER_TIMEOUT} property can be found returns the default
	 * value '120' seconds.
	 */
	@Override
	public long getTimeout() {
		return propertiesReader.getLong(JARSIGNER_TIMEOUT, JARSIGNER_TIMEOUT_DEFAULT);
	}
	
	@Override
	public String getHttpProxyHost() {
		return propertiesReader.getString(JARSIGNER_HTTP_PROXY_HOST, "");
	}
	
	@Override
	public String getHttpsProxyHost() {
		return propertiesReader.getString(JARSIGNER_HTTPS_PROXY_HOST, "");
	}
	
	@Override
	public int getHttpProxyPort() {
		return propertiesReader.getInt(JARSIGNER_HTTP_PROXY_PORT, 0);
	}
	
	@Override
	public int getHttpsProxyPort() {
		return propertiesReader.getInt(JARSIGNER_HTTPS_PROXY_PORT, 0);
	}

}
