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
package org.eclipse.cbi.webservice.signing.windows;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoValue
public abstract class OSSLCodesigner implements CodeSigner {

	private static final String TEMP_FILE_PREFIX = OSSLCodesigner.class.getSimpleName() + "-";
	private static final Logger logger = LoggerFactory.getLogger(OSSLCodesigner.class);

	public Path sign(Path file) throws IOException {
		Path out = null;
		try {
			out = Files.createTempFile(tempFolder(), TEMP_FILE_PREFIX, file.getFileName().toString());
			// osslsigncode requires non existent file
			Files.delete(out);
			StringBuilder output = new StringBuilder();
			int osslsigncodeExitValue = processExecutor().exec(createCommand(file, out), output, timeout(), TimeUnit.SECONDS);
			if (osslsigncodeExitValue != 0) {
				throw new IOException(Joiner.on('\n').join(
						"The '" + osslsigncode().toString() + "' command exited with value '" + osslsigncodeExitValue + "'",
						"'" + osslsigncode().toString() + "' output:",
						output));
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Output from osslsigncode:");
				logger.debug(output.toString());
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
				.add("sign")
				.add("-pkcs12", pkcs12().toString())
				.add("-pass", pkcs12Password())
				.add("-n", description())
				.add("-i", uri().toString())
				.addAll(timestampURIs().stream().map(x -> List.of("-t", x.toString())).flatMap(List::stream).collect(Collectors.toList()))
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
	abstract List<URI> timestampURIs();
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

		public abstract Builder timestampURIs(List<URI> timestampURIs);

		public abstract Builder tempFolder(Path tempFolder);

		public abstract Builder processExecutor(ProcessExecutor processExecutor);
	}
}
