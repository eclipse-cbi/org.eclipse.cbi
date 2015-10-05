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
package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.cbi.maven.plugins.jarsigner.FilteredJarSigner.Filter;

/**
* Checks and returns whether the given file should be signed. The condition are:
* <ul>
* <li>the file is a readable file with the {@code .jar} file extension.</li>
* <li>the file Jar does not have a entry {@value #META_INF_ECLIPSE_INF} with the either
* the properties {@value #JARPROCESSOR_EXCLUDE_SIGN} or
* {@value #JARPROCESSOR_EXCLUDE}</li>
* </ul>
*/
public class EclipseJarSignerFilter implements Filter {

	/**
	 * {@code eclispe.inf} property to exclude this Jar from signing.
	 */
    private static final String JARPROCESSOR_EXCLUDE_SIGN = "jarprocessor.exclude.sign";

    /**
     * {@code eclispe.inf} property to exclude this Jar from processing (and thus signing).
     */
	private static final String JARPROCESSOR_EXCLUDE = "jarprocessor.exclude";

	/**
	 * Path of the {@code eclispe.inf} entry in a Jar
	 */
	private static final String META_INF_ECLIPSE_INF = "META-INF/eclipse.inf";
	
	/**
	 * Jar file extension.
	 */
	static final String DOT_JAR_GLOB_PATTERN = "glob:**.jar";

	private final Log log;

	public EclipseJarSignerFilter(Log log) {
		this.log = log;
	}
	
	@Override
	public boolean shouldBeSigned(Path file) throws IOException {
		final boolean ret;

		if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
			log.warn("Can not read file '" + file + "', it will not be signed");
			ret = false;
		} else if (!file.getFileSystem().getPathMatcher(DOT_JAR_GLOB_PATTERN).matches(file)) {
			log.info("Extension of file '" + file + "' is not 'jar', it will not be signed");
			ret = false;
		} else if (isDisabledInEclipseInf(file)) {
			log.info("Signing of file '" + file + "' is disabled in '" + META_INF_ECLIPSE_INF + "', it will not be signed.");
			ret = false;
		} else {
			ret = true;
		}

		return ret;
	}

	/**
	 * Checks and returns whether the given Jar file has signing disabled by an
	 * file {@value #META_INF_ECLIPSE_INF} with the either the properties
	 * {@value #JARPROCESSOR_EXCLUDE_SIGN} or {@value #JARPROCESSOR_EXCLUDE}.
	 *
	 * @param file
	 * @return true if it finds a property that excludes this file for signing.
	 * @throws MojoExecutionException 
	 */
	@SuppressWarnings("static-method")
	private boolean isDisabledInEclipseInf(final Path file) throws IOException {
		boolean isDisabled = false;

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file))) {
			boolean found = false;
			for(ZipEntry entry = zis.getNextEntry(); !found && entry != null; entry = zis.getNextEntry()) {
				if (META_INF_ECLIPSE_INF.equals(entry.getName())) {
					found = true;
					Properties eclipseInf = new Properties();
					eclipseInf.load(zis);

					isDisabled = Boolean.parseBoolean(eclipseInf.getProperty(JARPROCESSOR_EXCLUDE))
							|| Boolean.parseBoolean(eclipseInf.getProperty(JARPROCESSOR_EXCLUDE_SIGN));
				}
			}
		} catch (IOException e) {
			throw new IOException("Error occured while checking if the signing of jar '"+file+"' was disabled in '"+META_INF_ECLIPSE_INF+"'", e);
		}

		return isDisabled;
	}
}
