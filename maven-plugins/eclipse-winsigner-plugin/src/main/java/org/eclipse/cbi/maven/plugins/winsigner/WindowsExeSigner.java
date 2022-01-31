/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.winsigner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.auto.value.AutoValue;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.AbstractCompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpResult;

@AutoValue
public abstract class WindowsExeSigner {

	/**
	 * The name of the part as it will be send to the signing server.
	 */
	private static final String PART_NAME = "file";
	
	abstract HttpClient httpClient();
	abstract Log log();
	abstract ExceptionHandler exceptionHandler();
	abstract URI serverUri();
	abstract Duration timeout();
	
	WindowsExeSigner() {
		
	}
	
	public int signExecutables(Set<Path> exesToSign) throws MojoExecutionException {
		Objects.requireNonNull(exesToSign);
		int ret = 0;
		for (Path exe : exesToSign) {
			if (signExecutable(exe)) {
				ret++;
			}
    	}
		return ret;
	}

	/**
     * signs the file
     * @param signer
     * @param file
     * @throws MojoExecutionException
     */
    public boolean signExecutable(Path file) throws MojoExecutionException {
    	Objects.requireNonNull(file);
    	if (!Files.isRegularFile(file)) {
    		exceptionHandler().handleError("Path '" + file.toString() + "' does not exist or is not a file. It won't be signed.");
    		return false;
    	} else if (!Files.isWritable(file)) {
    		exceptionHandler().handleError("Path '" + file.toString() + "' is not writable. It won't be signed.");
    		return false;
    	}

    	boolean ret = false;
        try {
        	log().info("[" + new Date() + "] Signing Windows executable '" + file + "'");
            if (!processOnSigningServer(file)) {
            	exceptionHandler().handleError("Signing of Windows executable '" + file + "' failed.");
            } else {
            	ret = true;
            }
        } catch (IOException e) {
        	exceptionHandler().handleError("Signing of Windows executable '" + file + "' failed.", e);
        }
        return ret;
    }
    
    private boolean processOnSigningServer(final Path file) throws IOException {
		final HttpRequest request = HttpRequest.on(serverUri()).withParam(PART_NAME, file).build();
		HttpRequest.Config requestConfig = HttpRequest.Config.builder().timeout(timeout()).build();
		log().debug("Windows exe signing request: " + request.toString());
		boolean success = httpClient().send(request, requestConfig, new AbstractCompletionListener(file.getParent(), file.getFileName().toString(), WindowsExeSigner.class.getSimpleName(), new MavenLogger(log())) {
			@Override
			public void onSuccess(HttpResult result) throws IOException {
				if (result.contentLength() == 0) {
					throw new IOException("Length of the returned content is 0");
				}
				result.copyContent(file, StandardCopyOption.REPLACE_EXISTING);
				if (Files.size(file) == 0) {
					throw new IOException("Size of the returned signed executable is 0");
				}
			}
		});
		return success;
	}

    public static Builder builder() {
    	return new AutoValue_WindowsExeSigner.Builder();
    }

	/**
	 * A builder of {@link WindowsExeSigner}. Default value for options are:
	 * <ul>
	 * <li>{@link #continueOnFail()}: false</li>
	 * <li>{@link #logOn(Log)}: {@link SystemStreamLog}</li>
	 * <li>{@link #maxRetry(int)}: 0</li>
	 * <li>{@link #waitBeforeRetry(int, TimeUnit)}: 0 {@link TimeUnit#SECONDS seconds}</li>
	 * <ul>
	 */
	@AutoValue.Builder
	public static abstract class Builder {

		Builder() {
		}
		
		/**
		 * Configure the {@link WindowsExeSigner} to <strong>not</strong> throw
		 * {@link MojoExecutionException} if it can't sign the Jar. Instead, it
		 * will only log a warning.
		 *
		 * @return this builder for chained calls.
		 */
		public abstract Builder exceptionHandler(ExceptionHandler exceptionHandler);

		/**
		 * The {@link Log} onto which the feedback should be printed.
		 * @param log
		 * @return this builder for chained calls.
		 */
		public abstract Builder log(Log log);

		public abstract Builder serverUri(URI uri);
		public abstract Builder httpClient(HttpClient httpClient);
		public abstract Builder timeout(Duration timeout);

		/**
		 * Creates and returns a new WindowsExeSigner configured with the options
		 * specified to this builder.
		 *
		 * @return a new {@link WindowsExeSigner}.
		 */
		public abstract WindowsExeSigner build();
	}
}
