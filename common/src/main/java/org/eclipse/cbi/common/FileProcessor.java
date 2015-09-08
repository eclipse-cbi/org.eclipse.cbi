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
package org.eclipse.cbi.common;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Common interface for file processors.
 */
public interface FileProcessor {

	/**
	 * Process the given file. If it fails, it will retry a given number of time
	 * and will wait for a given amount time between each time. Returns true if
	 * the file has been successfully processed (the file is then modified),
	 * false otherwise (the file then stay untouched).
	 * 
	 * @param path
	 *            the file to be processed (must exists and be a file).
	 * @param maxRetry
	 *            the number of time the processing should be tried before
	 *            returning false. Must be positive.
	 * @param retryInterval
	 *            the time to wait between each processing tentative. Must be
	 *            positive.
	 * @param intervalUnit
	 *            unit of time for the {@code retryTimer} argument. Must not be
	 *            {@code null}.
	 * @return true if the file has been successfully processed, false
	 *         otherwise.
	 * @throws IOException
	 *             if any IO exception occurs while processing (e.g., if the
	 *             file not writable)
	 */
	boolean process(Path path, int maxRetry, int retryInterval, TimeUnit intervalUnit)
			throws IOException;

	/**
	 * Process the given file. Returns true if the file has been successfully
	 * processed (the file is then modified), false otherwise (the file then
	 * stay untouched).
	 * 
	 * @param path
	 *            the file to be processed (must exists and be a file).
	 * @return true if the file has been successfully processed, false
	 *         otherwise.
	 * @throws IOException
	 *             if any IO exception occurs while processing (e.g., if the
	 *             file not writable)
	 */
	boolean process(Path path) throws IOException;
}