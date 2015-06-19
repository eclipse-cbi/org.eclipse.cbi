/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.common.http;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Common interface for signers.
 */
public interface HttpPostFileSender {

	/**
	 * Sign the given file. It will retry a given number of time and will wait
	 * for a given amount time between each time. Returns true if the file has
	 * been successfully signed (the file is then modified), false otherwise
	 * (the file then stay untouched).
	 * 
	 * @param path
	 *            the file to be signed (must exists and be a file).
	 * @param partName
	 *            the name of the part that will be send
	 * @param maxRetry
	 *            the number of time the signing should be tried before
	 *            returning false. Must be positive.
	 * @param retryInterval
	 *            the time to wait between each signing tentative. Must be
	 *            positive.
	 * @param intervalUnit
	 *            unit of time for the {@code retryTimer} argument. Must not be
	 *            {@code null}.
	 * @return true if the file has been successfully signed, false otherwise.
	 * @throws IOException
	 *             if any IO exception occurs while signing (e.g., if the file
	 *             not writable)
	 */
	boolean post(Path path, String partName, int maxRetry, int retryInterval, TimeUnit intervalUnit) throws IOException;

	/**
	 * Sign the given file.Returns true if the file has been successfully signed
	 * (the file is then modified), false otherwise (the file then stay
	 * untouched).
	 * 
	 * @param path
	 *            the file to be signed (must exists and be a file).
	 * @param partName
	 *            the name of the part that will be send
	 * @return true if the file has been successfully signed, false otherwise.
	 * @throws IOException
	 *             if any IO exception occurs while signing (e.g., if the file
	 *             not writable)
	 */
	boolean post(Path path, String partName) throws IOException;
}