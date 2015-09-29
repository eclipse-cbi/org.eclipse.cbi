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
import java.nio.file.Path;

public interface JarSignerConfiguration {

	/**
	 * Reads and returns the path to the jarsigner executable.
	 * 
	 * @return the path to the jarsigner executable.
	 */
	Path getJarSigner();

	/**
	 * Reads and returns the path to the keystore file.
	 * 
	 * @return the path to the keystore file.
	 */
	Path getKeystore();

	/**
	 * Reads and returns the name of the alias of the key to be used in the
	 * keystore.
	 * 
	 * @return the name of the alias of the key to be used in the keystore.
	 */
	String getKeystoreAlias();

	/**
	 * Reads and returns the path to the file containing the password of the
	 * keystore.
	 * 
	 * @return the path to the file containing the password of the keystore.
	 */
	String getKeystorePassword();

	/**
	 * Reads and returns the URI of the timestamping authority to be used by the
	 * jarsigner command
	 * 
	 * @return the URI of the timestamping authority to be used by the jarsigner
	 *         command
	 */
	URI getTimeStampingAuthority();

	/**
	 * Reads and returns the timeout of the jarsigner command. If no 
	 * {@value #JARSIGNER_TIMEOUT} property can be found returns the default
	 * value '120' seconds.
	 */
	long getTimeout();

	String getHttpProxyHost();

	String getHttpsProxyHost();

	int getHttpProxyPort();

	int getHttpsProxyPort();

}