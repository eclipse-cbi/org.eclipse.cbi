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
import java.nio.file.Path;

public interface JarSignerConfiguration {

	/**
	 * Returns the path to the jarsigner executable.
	 * 
	 * @return the path to the jarsigner executable.
	 */
	Path getJarSigner();

	/**
	 * Returns extra arguments that are passed to the jarsigner binary as Java options
	 *
	 * @return extra java arguments
	 */
	String getJavaArgs();

	/**
	 * Returns the path to the keystore file.
	 * 
	 * @return the path to the keystore file.
	 */
	Path getKeystore();

	/**
	 * Returns the default sigfile name if none is provided.
	 *
	 * @return the default sigfile name.
	 */
	String getSigFileDefault();

	/**
	 * Returns the type of keystore.
	 * 
	 * @return the type of keystore.
	 */
	String getStoreType();

	/**
	 * Returns the name of the alias of the key to be used in the
	 * keystore.
	 * 
	 * @return the name of the alias of the key to be used in the keystore.
	 */
	String getKeystoreAlias();

	/**
	 * Returns the path to the file containing the password of the
	 * keystore.
	 * 
	 * @return the path to the file containing the password of the keystore.
	 */
	String getKeystorePassword();

	/**
	 * Returns the keystore provider class.
	 *
	 * @return the keystore provider class.
	 */
	String getProviderClass();

	/**
	 * Returns the argument to the keystore provider.
	 *
	 * @return the argument to the keystore provider.
	 */
	String getProviderArg();

	/**
	 * Returns the path to the certificate chain file.
	 *
	 * @return the path to the certificate chain file.
	 */
	Path getCertificateChain();

	/**
	 * Returns the path to the Google Cloud credentials file.
	 *
	 * @return the path to the Google Cloud credentials file.
	 */
	Path getGoogleCloudCredentials();

	/**
	 * Returns the URI of the timestamping authority to be used by the
	 * jarsigner command
	 * 
	 * @return the URI of the timestamping authority to be used by the jarsigner
	 *         command
	 */
	URI getTimeStampingAuthority();

	/**
	 * Returns the timeout of the jarsigner command.
	 * 
	 * @return the timeout of the jarsigner command.
	 */
	long getTimeout();

	String getHttpProxyHost();

	String getHttpsProxyHost();

	int getHttpProxyPort();

	int getHttpsProxyPort();

}