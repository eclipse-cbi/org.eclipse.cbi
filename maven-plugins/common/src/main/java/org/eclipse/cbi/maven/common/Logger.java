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
package org.eclipse.cbi.maven.common;

import java.io.PrintStream;

public interface Logger {

    /**
     * Send a message to the user in the <b>debug</b> error level.
     *
     * @param content
     */
    void debug( CharSequence content );

    /**
     * Send a message (and accompanying exception) to the user in the <b>debug</b> error level.
     * <br/>
     * The error's stacktrace will be output when this error level is enabled.
     *
     * @param content
     * @param error
     */
    void debug( CharSequence content, Throwable error );

    /**
     * Send an exception to the user in the <b>debug</b> error level.
     * <br/>
     * The stack trace for this exception will be output when this error level is enabled.
     *
     * @param error
     */
    void debug( Throwable error );

    /**
     * Send a message to the user in the <b>info</b> error level.
     *
     * @param content
     */
    void info( CharSequence content );

    /**
     * Send a message (and accompanying exception) to the user in the <b>info</b> error level.
     * <br/>
     * The error's stacktrace will be output when this error level is enabled.
     *
     * @param content
     * @param error
     */
    void info( CharSequence content, Throwable error );

    /**
     * Send an exception to the user in the <b>info</b> error level.
     * <br/>
     * The stack trace for this exception will be output when this error level is enabled.
     *
     * @param error
     */
    void info( Throwable error );

    /**
     * Send a message to the user in the <b>warn</b> error level.
     *
     * @param content
     */
    void warn( CharSequence content );

    /**
     * Send a message (and accompanying exception) to the user in the <b>warn</b> error level.
     * <br/>
     * The error's stacktrace will be output when this error level is enabled.
     *
     * @param content
     * @param error
     */
    void warn( CharSequence content, Throwable error );

    /**
     * Send an exception to the user in the <b>warn</b> error level.
     * <br/>
     * The stack trace for this exception will be output when this error level is enabled.
     *
     * @param error
     */
    void warn( Throwable error );

    /**
     * Send a message to the user in the <b>error</b> error level.
     *
     * @param content
     */
    void error( CharSequence content );

    /**
     * Send a message (and accompanying exception) to the user in the <b>error</b> error level.
     * <br/>
     * The error's stacktrace will be output when this error level is enabled.
     *
     * @param content
     * @param error
     */
    void error( CharSequence content, Throwable error );

    /**
     * Send an exception to the user in the <b>error</b> error level.
     * <br/>
     * The stack trace for this exception will be output when this error level is enabled.
     *
     * @param error
     */
    void error( Throwable error );

    public static class SystemLogger implements Logger {

    	private static final String ERROR = "[ERROR] ";
		private static final String WARNING = "[WARNING] ";
		private static final String DEBUG = "[DEBUG] ";
		private static final String INFO = "[INFO] ";

		private final boolean useSyserrForWarn;
		private final boolean useSyserrForErr;

		public SystemLogger(boolean useSyserrForWarn, boolean useSyserrForErr) {
			this.useSyserrForWarn = useSyserrForWarn;
			this.useSyserrForErr = useSyserrForErr;
    	}

		@Override
		public void debug(CharSequence content) {
			System.out.println(DEBUG + content);
		}

		@Override
		public void debug(CharSequence content, Throwable error) {
			System.out.println(DEBUG + content);
			error.printStackTrace(System.out);
		}

		@Override
		public void debug(Throwable error) {
			System.out.println(DEBUG + error.getMessage());
			error.printStackTrace(System.out);
		}

		@Override
		public void info(CharSequence content) {
			System.out.println(INFO + content);
		}

		@Override
		public void info(CharSequence content, Throwable error) {
			System.out.println(INFO + content);
			error.printStackTrace(System.out);
		}

		@Override
		public void info(Throwable error) {
			System.out.println(INFO + error.getMessage());
			error.printStackTrace(System.out);
		}

		@Override
		public void warn(CharSequence content) {
			PrintStream out;
			if (useSyserrForWarn) {
				out = System.err;
			} else {
				out = System.out;
			}
			out.println(WARNING + content);
		}

		@Override
		public void warn(CharSequence content, Throwable error) {
			PrintStream out;
			if (useSyserrForWarn) {
				out = System.err;
			} else {
				out = System.out;
			}
			out.println(WARNING + content);
			error.printStackTrace(out);
		}

		@Override
		public void warn(Throwable error) {
			PrintStream out;
			if (useSyserrForWarn) {
				out = System.err;
			} else {
				out = System.out;
			}
			out.println(WARNING + error.getMessage());
			error.printStackTrace(out);
		}

		@Override
		public void error(CharSequence content) {
			PrintStream out;
			if (useSyserrForErr) {
				out = System.err;
			} else {
				out = System.out;
			}
			out.println(ERROR + content);
		}

		@Override
		public void error(CharSequence content, Throwable error) {
			PrintStream out;
			if (useSyserrForErr) {
				out = System.err;
			} else {
				out = System.out;
			}
			out.println(ERROR + content);
			error.printStackTrace(out);
		}

		@Override
		public void error(Throwable error) {
			PrintStream out;
			if (useSyserrForErr) {
				out = System.err;
			} else {
				out = System.out;
			}
			out.println(ERROR + error.getMessage());
			error.printStackTrace(out);
		}

    }
}
