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
package org.eclipse.cbi.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * Utility class to execute a native command in a forked process.
 */
public interface ProcessExecutor {

	/**
	 * Execute the given command as a forked process. It will gather the
	 * standard output and standard err in the given {@link StringBuffer}. The
	 * process will be stopped after the given given timeout.
	 * 
	 * @param command
	 *            the command to execute
	 * @param processOutput
	 *            where the stdout and sterr will be written to
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
	int exec(ImmutableList<String> command, StringBuffer processOutput, long timeout, TimeUnit timeoutUnit) throws IOException;
	
	/**
	 * Execute the given command as a forked process. The process will be
	 * stopped after the given given timeout. The stdout and stderr will not be
	 * retrievable with this method. If you need it, use
	 * {@link #exec(ImmutableList, StringBuffer, long, TimeUnit)}
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
	public class BasicImpl implements ProcessExecutor {
		
		private static final Logger logger = LoggerFactory.getLogger(BasicImpl.class);

		private static final int STREAM_GLOBBER_GRACETIME = 3; // in seconds
		
		/**
		 * {@inheritDoc}
		 */
		public int exec(ImmutableList<String> command, StringBuffer processOutput, long timeout, TimeUnit timeoutUnit) throws IOException {
			Objects.requireNonNull(command);
			Preconditions.checkArgument(!command.isEmpty(), "Command must not be empty");
			Objects.requireNonNull(processOutput);
			
			final String arg0 = command.iterator().next();
			
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);

			logger.info("Process '" + arg0 + "' starts");
			
			Process p = pb.start();

			// redirect output/error streams of process.
			Thread streamGobbler = new Thread(new StreamRedirection(p.getInputStream(), processOutput));
			streamGobbler.start();

			try {
				if (!p.waitFor(timeout, timeoutUnit)) {
					p.destroyForcibly();
					throw new IOException(Joiner.on('\n').join(
							"Process '" + arg0 + "' has been stopped forcibly. It did not complete in " + timeout + " " + timeoutUnit,
							"Process '" + arg0 + "' output: " + processOutput.toString()));
				}
				
				streamGobbler.join(TimeUnit.SECONDS.toMillis(STREAM_GLOBBER_GRACETIME)); // give 3sec to the stream gobbler to gather all the output.
				streamGobbler.interrupt();
			} catch (InterruptedException e) {
				logger.error("Thread '" + Thread.currentThread().getName() + "' has been interrupted while waiting for the process '" + arg0 + "' to complete.", e);

				streamGobbler.interrupt();

				processOutput.append("Thread '" + Thread.currentThread().getName() + "' has been interrupted while waiting for the process '" + arg0 + "' to complete.\n");
				StringWriter stackTrace = new StringWriter();
				e.printStackTrace(new PrintWriter(stackTrace));
				processOutput.append(stackTrace.getBuffer().toString());
				
				if (p.isAlive())
					logOutput(arg0, p.exitValue(), processOutput);
				else 
					logger.error("Process '" + arg0 + "' output: " + processOutput.toString());
				
				// Restore the interrupted status
				Thread.currentThread().interrupt();
			}

			return logOutput(arg0, p.exitValue(), processOutput);
		}

		private int logOutput(String arg0, final int exitValue, StringBuffer processOutput) {
			if (exitValue == 0) {
				logger.info("Process '" + arg0 + "' exited with value '" + exitValue +"'");
				logger.info("Process '" + arg0 + "' output: " + processOutput.toString());
			} else {
				logger.error("Process '" + arg0 + "' exited with value '" + exitValue +"'");
				logger.error("Process '" + arg0 + "' output: " + processOutput.toString());
			}
			return exitValue;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int exec(ImmutableList<String> command, long timeout, TimeUnit timeoutUnit) throws IOException {
			return exec(command, new StringBuffer(), timeout, timeoutUnit);
		}

		/**
		 * A runnable that will continuously read from an {@link InputStream}
		 * and write the result in an {@link Appendable}.
		 */
		private static final class StreamRedirection implements Runnable {

			private static final String NL = System.getProperty("line.separator");
			private final InputStream is;
			private final Appendable appendable;

			/**
			 * Creates a
			 * 
			 * @param is
			 *            the input stream to read from (will be buffered).
			 * @param appendable
			 *            where the read characters will be written to. Should
			 *            be a thread safe implementation.
			 */
			StreamRedirection(InputStream is, Appendable appendable) {
				this.is = is;
				this.appendable = appendable;
			}

			/**
			 * {@inheritDoc}
			 */
		    @Override
		    public void run() {
		    	BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		        try {
		            String line = null;
		            while ((line = r.readLine()) != null && !Thread.currentThread().isInterrupted())
		            	appendable.append(line).append(NL);
		        } catch (IOException e) {
		            throw Throwables.propagate(e);
		        }
		    }
		}
	}
}