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
package org.eclipse.cbi.webservice.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Utility class to execute a native command in a forked process.
 */
public interface ProcessExecutor {

	/**
	 * Execute the given command as a forked process. It will gather the
	 * standard output and standard err in the given {@link StringBuffer}. The
	 * process will be stopped after the given timeout.
	 *
	 * @param command
	 *            the command to execute
	 * @param processOutput
	 *            where the stdout and stderr will be written to
	 * @param timeout
	 *            the amount of time to wait before killing the subprocess
	 * @param timeoutUnit
	 *            the unit of the amount of time to wait before killing the
	 *            subprocess
	 * @return the exit value of the process
	 * @throws IOException
	 *             if the process can not be started or if it has been
	 *             {@link Process#destroyForcibly() destroyed} after the
	 *             timeout.
	 */
	int exec(ImmutableList<String> command, StringBuilder processOutput, long timeout, TimeUnit timeoutUnit) throws IOException;

	/**
	 * Execute the given command as a forked process. The process will be
	 * stopped after the given timeout. The stdout and stderr will not be
	 * retrievable with this method. If you need it, use 
	 * {@link #exec(ImmutableList, StringBuilder, long, TimeUnit)}
	 *
	 * @param command
	 *            the command to execute
	 * @param timeout
	 *            the amount of time to wait before killing the subprocess
	 * @param timeoutUnit
	 *            the unit of the amount of time to wait before killing the
	 *            subprocess
	 * @return the exit value of the process
	 * @throws IOException
	 *             if the process can not be started or if it has been
	 *             {@link Process#destroyForcibly() destroyed} after the
	 *             timeout.
	 */
	int exec(ImmutableList<String> command, long timeout, TimeUnit timeoutUnit) throws IOException;

	/**
	 * A basic implementation that will use a {@link ProcessBuilder} to build and run the {@link Process}.
	 */
	class BasicImpl implements ProcessExecutor {

		private static final Logger logger = LoggerFactory.getLogger(BasicImpl.class);

		private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("Process-Stream-Gobbler-%d").build());

		private static final int STREAM_GLOBBER_GRACETIME = 3; // in seconds

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int exec(ImmutableList<String> command, StringBuilder processOutput, long timeout, TimeUnit timeoutUnit) throws IOException {
			Objects.requireNonNull(command);
			Preconditions.checkArgument(!command.isEmpty(), "Command must not be empty");
			Objects.requireNonNull(processOutput);

            logger.debug("Will execute '{}'", String.join(" ", command));
			final String arg0 = command.iterator().next();

			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);

            logger.debug("Process '{}' starts", arg0);

			Process p = pb.start();

			// redirect output/error streams of process.
			Future<String> streamGobbler = executor.submit(new StreamRedirection(p.getInputStream()));

			try {
				if (!p.waitFor(timeout, timeoutUnit)) { //timeout
					gatherOutput(processOutput, streamGobbler);
					p.destroyForcibly();
					throw new IOException(Joiner.on('\n').join(
							"Process '" + arg0 + "' has been stopped forcibly. It did not complete in " + timeout + " " + timeoutUnit,
							"Process '" + arg0 + "' output: " + processOutput.toString()));
				}
				gatherOutput(processOutput, streamGobbler);
			} catch (InterruptedException e) { // we've been interrupted
				p.destroyForcibly(); // kill the subprocess

                logger.error("Thread '{}' has been interrupted while waiting for the process '{}' to complete.", Thread.currentThread().getName(), arg0, e);
				processOutput
						.append("Thread '")
						.append(Thread.currentThread().getName())
						.append("' has been interrupted while waiting for the process '")
						.append(arg0)
						.append("' to complete.\n");
				printStackTrace(e, processOutput);

				try {
					gatherOutput(processOutput, streamGobbler);
				} catch (@SuppressWarnings("unused") InterruptedException e1) {
					// we will restore the interrupted status soon.
				}

				if (!p.isAlive()) {
					logOutput(arg0, p.exitValue(), processOutput);
				} else {
                    logger.error("Process '{}' output: {}", arg0, processOutput);
				}

				// Restore the interrupted status
				Thread.currentThread().interrupt();
			}

			return logOutput(arg0, p.exitValue(), processOutput);
		}

		private static void printStackTrace(Exception e, StringBuilder output) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			output.append(stackTrace.getBuffer().toString());
		}

		private static void gatherOutput(StringBuilder processOutput, Future<String> streamGobbler) throws InterruptedException {
			try {
				// give 3sec to the stream gobbler to gather all the output.
				processOutput.append(streamGobbler.get(STREAM_GLOBBER_GRACETIME, TimeUnit.SECONDS));
			} catch (TimeoutException | InterruptedException | ExecutionException e) {
				processOutput.append("Process output can not be gathered'");
				printStackTrace(e, processOutput);
				streamGobbler.cancel(true);
				if (e instanceof InterruptedException) {
					throw new InterruptedException();
				}
			}
		}

		private static int logOutput(String arg0, final int exitValue, StringBuilder processOutput) {
			String output = processOutput.toString();
			if (exitValue == 0) {
                logger.debug("Process '{}' exited with value '{}'", arg0, exitValue);
				if (!output.isEmpty()) {
                    logger.debug("Process '{}' output:\n{}", arg0, output);
				} else {
                    logger.debug("Process '{}' exited with no output", arg0);
				}
			} else {
                logger.error("Process '{}' exited with value '{}'", arg0, exitValue);
				if (!output.isEmpty()) {
                    logger.error("Process '{}' output:\n{}", arg0, output);
				} else {
                    logger.error("Process '{}' exited with no output", arg0);
				}
			}
			return exitValue;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int exec(ImmutableList<String> command, long timeout, TimeUnit timeoutUnit) throws IOException {
			return exec(command, new StringBuilder(), timeout, timeoutUnit);
		}

		/**
		 * A runnable that will continuously read from an {@link InputStream}
		 */
		private static final class StreamRedirection implements Callable<String> {

			private static final String NL = System.lineSeparator();
			private final InputStream is;

			/**
			 * Creates a
			 *
			 * @param is
			 *            the input stream to read from (will be buffered).
			 */
			StreamRedirection(InputStream is) {
				this.is = is;
			}

			@Override
			public String call() throws Exception {
				final StringBuilder buffer = new StringBuilder();
				BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
				String line = null;
				while ((line = r.readLine()) != null && !Thread.currentThread().isInterrupted())
					buffer.append(line).append(NL);
				return buffer.toString();
			}
		}
	}
}