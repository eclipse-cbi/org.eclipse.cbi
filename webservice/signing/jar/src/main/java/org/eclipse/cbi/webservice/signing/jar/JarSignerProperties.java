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
package org.eclipse.cbi.webservice.signing.jar;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Strings;
import org.eclipse.cbi.webservice.util.PropertiesReader;

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
	private static final String JARSIGNER_STORETYPE = "jarsigner.storetype";
	private static final String JARSIGNER_KMS_CERTCHAIN = "jarsigner.kms.certchain";
	private static final String JARSIGNER_KMS_CREDENTIALS = "jarsigner.kms.credentials";
	private static final String JARSIGNER_PROVIDER_CLASS = "jarsigner.provider.class";
	private static final String JARSIGNER_PROVIDER_ARG = "jarsigner.provider.arg";

	private static final String JARSIGNER_BIN = "jarsigner.bin";
	private static final String JARSIGNER_JAVA_ARGS = "jarsigner.javaargs";

	private static final String JARSIGNER_SIGFILE_DEFAULT = "jarsigner.sigfile.default";

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

	@Override
	public String getJavaArgs() {
		return propertiesReader.getString(JARSIGNER_JAVA_ARGS, "");
	}

	@Override
	public String getSigFileDefault() {
		return propertiesReader.getString(JARSIGNER_SIGFILE_DEFAULT, "");
	}

	/**
	 * Reads and returns the path to the keystore file.
	 * 
	 * @return the path to the keystore file.
	 */
	@Override
	public Path getKeystore() {
		String keystore = propertiesReader.getString(JARSIGNER_KEYSTORE, "");
		if (!Strings.isNullOrEmpty(keystore)) {
			return propertiesReader.getRegularFile(JARSIGNER_KEYSTORE);
		} else {
			return null;
		}
	}

	@Override
	public String getStoreType() {
		return propertiesReader.getString(JARSIGNER_STORETYPE, "");
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
		String keystore = propertiesReader.getString(JARSIGNER_KEYSTORE_PASSWORD, "");
		if (!Strings.isNullOrEmpty(keystore)) {
			return propertiesReader.getFileContent(JARSIGNER_KEYSTORE_PASSWORD);
		} else {
			return null;
		}
	}

	@Override
	public String getProviderClass() {
		return propertiesReader.getString(JARSIGNER_PROVIDER_CLASS, "");
	}

	@Override
	public String getProviderArg() {
		return propertiesReader.getString(JARSIGNER_PROVIDER_ARG, "");
	}

	@Override
	public Path getCertificateChain() {
		String certchain = propertiesReader.getString(JARSIGNER_KMS_CERTCHAIN, "");
		if (!Strings.isNullOrEmpty(certchain)) {
			return propertiesReader.getRegularFile(JARSIGNER_KMS_CERTCHAIN);
		} else {
			return null;
		}
	}

	@Override
	public Path getGoogleCloudCredentials() {
		String credentials = propertiesReader.getString(JARSIGNER_KMS_CREDENTIALS, "");
		if (!Strings.isNullOrEmpty(credentials)) {
			return propertiesReader.getRegularFile(JARSIGNER_KMS_CREDENTIALS);
		} else {
			return null;
		}
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
