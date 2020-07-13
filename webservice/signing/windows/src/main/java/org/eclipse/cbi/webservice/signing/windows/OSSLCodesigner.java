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
package org.eclipse.cbi.webservice.signing.windows;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class OSSLCodesigner {

	private static final String TEMP_FILE_PREFIX = OSSLCodesigner.class.getSimpleName() + "-";
	private static Logger logger = LoggerFactory.getLogger(OSSLCodesigner.class);

	public Path sign(Path file) throws IOException {
		Path out = null;
		try {
			out = Files.createTempFile(tempFolder(), TEMP_FILE_PREFIX, file.getFileName().toString());
			StringBuilder output = new StringBuilder();
			int osslsigncodeExitValue = processExecutor().exec(createCommand(file, out), output, timeout(), TimeUnit.SECONDS);
			if (osslsigncodeExitValue != 0) {
				throw new IOException(Joiner.on('\n').join(
						"The '" + osslsigncode().toString() + "' command exited with value '" + osslsigncodeExitValue + "'",
						"'" + osslsigncode().toString() + "' output:",
						output));
			}
			Files.move(out, file, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			if (out != null && Files.exists(out)) {
				try {
					Paths.delete(out);
				} catch (IOException e) {
					logger.error("Error occured while deleting temporary resource '"+out.toString()+"'", e);
				}
			}
		}
		return file;
	}
	
	private ImmutableList<String> createCommand(Path in, Path out) {
		return ImmutableList.<String>builder()
				.add(osslsigncode().toString())
				.add("-pkcs12", pkcs12().toString())
				.add("-pass", pkcs12Password())
				.add("-n", description())
				.add("-i", uri().toString())
				.add("-t", timestampURI().toString())
				.add("-in", in.toString())
				.add("-out", out.toString())
				.build();
	}
	
	public static Builder builder() {
		return new AutoValue_OSSLCodesigner.Builder();
	}
	
	abstract Path osslsigncode();
	abstract long timeout();
	abstract Path pkcs12();
	abstract String pkcs12Password();
	abstract String description();
	abstract URI uri();
	abstract URI timestampURI();
	abstract Path tempFolder();
	abstract ProcessExecutor processExecutor();
	
	@AutoValue.Builder
	public static abstract class Builder {
		public abstract OSSLCodesigner build();

		public abstract Builder osslsigncode(Path osslSigncode);

		public abstract Builder timeout(long osslSigncodeTimeout);

		public abstract Builder pkcs12(Path pkcs12);

		public abstract Builder pkcs12Password(String pkcs12Password);

		public abstract Builder description(String description);

		public abstract Builder uri(URI uri);

		public abstract Builder timestampURI(URI timestampURI);

		public abstract Builder tempFolder(Path tempFolder);

		public abstract Builder processExecutor(ProcessExecutor processExecutor);
		
		
	}
}
