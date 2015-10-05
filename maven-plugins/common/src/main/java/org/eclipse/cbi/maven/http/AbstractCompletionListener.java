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
package org.eclipse.cbi.maven.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Objects;

import org.eclipse.cbi.maven.Logger;

import com.google.common.base.Preconditions;

public abstract class AbstractCompletionListener implements CompletionListener {
	
	/**
	 * The log for providing {@code DEBUG} feedback about the process.
	 */
	private final Logger log;
	
	private final Path errorLogFolder;

	private final String erroLogPrefix;

	private final String errorLogSuffix;
	
	public AbstractCompletionListener(Path errorLogFolder, String errologPrefix, String errorLogSuffix, Logger log) {
		Preconditions.checkArgument(Files.isDirectory(errorLogFolder));
		this.errorLogFolder = errorLogFolder;
		this.erroLogPrefix = errologPrefix;
		this.errorLogSuffix = errorLogSuffix;
		this.log = Objects.requireNonNull(log);
	}
	
	@Override
	public void onError(HttpResult error) {
		final StringBuilder sb = new StringBuilder();
		sb.append("HTTP request failed.").append(System.lineSeparator());
		
		try {
			final Path errorMessagePath = Files.createTempFile(errorLogFolder, erroLogPrefix + "-", "-" + errorLogSuffix + ".log");
			error.copyContent(errorMessagePath, StandardCopyOption.REPLACE_EXISTING);
			sb.append(logErrorMessage(error, errorMessagePath, 25));
		} catch (IOException e) {
			log.debug("An error occured while reading the server response", e);
			sb.append("HTTP Error " + error.statusCode() + " (reason: " + error.reason() + ")").append(System.lineSeparator());
		}
		
		log.warn("[" + new Date() + "] " + sb.toString());
	}

	private String logErrorMessage(HttpResult error, final Path errorMessagePath, int maxLine) {
		final StringBuilder sb = new StringBuilder();
		
		sb.append("HTTP Error " + error.statusCode() + " (reason: " + error.reason() + ")").append(System.lineSeparator());
		
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(Files.newInputStream(errorMessagePath)), error.contentCharset()))) {
			sb.append(readLines(reader, maxLine, errorMessagePath));
		} catch (IOException e) {
			log.debug("An error occured while reading the server response", e);
		}
		
		return sb.toString();
	}

	private String readLines(BufferedReader reader, int maxLine, final Path errorMessagePath) throws IOException {
		StringBuilder sb = new StringBuilder();
		String line = reader.readLine();
		for (int i = 0; i < maxLine && line != null; i++, line = reader.readLine()) {
			sb.append(line);
		}

		sb.append(System.lineSeparator());
		if (line != null) {
			sb.append("...<troncated output>... For complete output, see '" + errorMessagePath.toAbsolutePath().toString() + "'");
		} else {
			sb.append("Server response has been saved to '" + errorMessagePath.toAbsolutePath().toString() + "'");
		}
		
		return sb.toString();
	}
}