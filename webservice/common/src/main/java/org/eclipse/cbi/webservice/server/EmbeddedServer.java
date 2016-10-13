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
package org.eclipse.cbi.webservice.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;

/**
 * Base class to create a simple (one servlet) embedded Jetty server.
 */
@AutoValue
public abstract class EmbeddedServer {

	private static final int FILE_SIZE_THRESHOLD = 8*1024*1024; // 8MiB
	private static final int DEFAULT_PORT = 8080;
	private static final boolean DEFAULT_APPEND_SERVICE_VERSION_TO_PATH_SPEC = true;
	private static final String CONTEXT_PATH = "/";
	private Server server;

	EmbeddedServer() {} // prevents instantiation and subclassing outside the package

	/**
	 * Returns the servlet that will served the single service of this server.
	 *
	 * @return the servlet that will served the single service of this server.
	 */
	abstract Servlet servlet();

	/**
	 * Returns the port that this server will listen to.
	 *
	 * @return the port that this server will listen to.
	 */
	abstract int port();

	/**
	 * Returns the temporary folder that this server will use.
	 *
	 * @return the temporary folder that this server will use.
	 */
	abstract Path tempFolder();

	/**
	 * Returns the path spec that will be associated with the servlet.
	 *
	 * @return the path spec that will be associated with the servlet.
	 */
	abstract String servicePathSpec();

	/**
	 * Returns whether the version of the servlet should be appended to the
	 * service path spec.
	 *
	 * @return whether the version of the servlet should be appended to the
	 *         service
	 */
	abstract boolean appendServiceVersionToPathSpec();

	/**
	 * Returns the file where this server will log all access.
	 *
	 * @return the file where this server will log all access.
	 */
	abstract Path accessLogFile();

	/**
	 * Returns the properties containing the log4j configuration, e.g.:
	 * <p>
	 *
	 * <pre>
	 * # Root logger option
	 * log4j.rootLogger=INFO, file
	 *
	 * # Redirect log messages to a log file, support file rolling.
	 * log4j.appender.file=org.apache.log4j.RollingFileAppender
	 * log4j.appender.file.File=/var/log/jetty-access.log
	 * log4j.appender.file.MaxFileSize=10MB
	 * log4j.appender.file.MaxBackupIndex=10
	 * log4j.appender.file.layout=org.apache.log4j.PatternLayout
	 * log4j.appender.file.layout.ConversionPattern=%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
	 * </pre>
	 *
	 * @return the properties containing the log4j configuration.
	 */
	abstract Properties log4jConfiguration();

	/**
	 * Creates and returns a new builder for this class.
	 *
	 * @return a new builder for this class.
	 */
	public static Builder builder() {
		return new AutoValue_EmbeddedServer.Builder()
			.port(DEFAULT_PORT)
			.appendServiceVersionToPathSpec(DEFAULT_APPEND_SERVICE_VERSION_TO_PATH_SPEC);
	}

	/**
	 * A builder of {@link EmbeddedServer}.
	 */
	@AutoValue.Builder
	public abstract static class Builder {
		Builder() {}

		/**
		 * Sets the servlet to be used by the to-be build server.
		 *
		 * @param servlet
		 *            the servlet to be used by the to-be build server. Must not
		 *            be null.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder servlet(Servlet servlet);

		/**
		 * Sets the port number to be used by the to-be build server.
		 *
		 * @param port
		 *            the port number to be used by the to-be build server. Must
		 *            not be null.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder port(int port);

		/**
		 * Sets the temporary folder to be used by the to-be build server.
		 *
		 * @param tempFolder
		 *            the temporary folder to be used by the to-be build server.
		 *            Must not be null.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder tempFolder(Path tempFolder);

		/**
		 * Sets the service path specification for the servlet of the to-be
		 * build server.
		 *
		 * @param servicePathSpec
		 *            the service path specification for the servlet. Must not
		 *            be null.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder servicePathSpec(String servicePathSpec);

		/**
		 * Configure the to-be created server to append or not the version of
		 * the offered service.
		 *
		 * @param appendServiceVersionToPathSpec
		 *            whether the version should be appended or not.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder appendServiceVersionToPathSpec(boolean appendServiceVersionToPathSpec);

		/**
		 * Sets the access log file to be used by the to-be build server.
		 *
		 * @param accessLogFile
		 *            the access log file to be used by the to-be build server.
		 *            Must not be null.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder accessLogFile(Path accessLogFile);

		/**
		 * Sets the file containing the log4j configuration of the to-be build
		 * server.
		 *
		 * @param configuration
		 *            the file containing the log4j configuration of the to-be
		 *            build server. Must not be null.
		 * @return this builder for daisy chaining.
		 */
		public abstract Builder log4jConfiguration(Properties configuration);

		abstract EmbeddedServer autoBuild();

		/**
		 * Creates and returns a new {@link EmbeddedServer}. The following checks are done:
		 * <ul>
		 * <li>The port number must be stricly positive</li>
		 * <li>The trimmed service path spec must not be empty</li>
		 * <li>The temporary folder must be an existing directory</li>
		 * <li>The parent folder of the access log fil must exist</li>
		 * </ul>
		 * @return a new {@link EmbeddedServer}.
		 */
		public EmbeddedServer build() {
			EmbeddedServer server = autoBuild();
			Preconditions.checkState(server.port() > 0, "Server port must be stricly positive");
			Preconditions.checkState(!server.servicePathSpec().trim().isEmpty(), "Service path spec must not be empty");
			Preconditions.checkState(Files.exists(server.tempFolder()), "Temp folder must exists");
			Preconditions.checkState(Files.isDirectory(server.tempFolder()), "Temp folder must be a directory");
			Preconditions.checkState(Files.exists(server.accessLogFile().normalize().getParent()), "Parent folder of access log file must exists");
			return server;
		}
	}

	/**
	 * Starts and joins the embedded Jetty server. This method will block until
	 * the server is stopped.
	 *
	 * @throws Exception
	 */
	public void start() throws Exception {
		PropertyConfigurator.configure(log4jConfiguration());

		server = new Server(port());

		ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		contextHandler.setContextPath(CONTEXT_PATH);
		contextHandler.setErrorHandler(new EmbeddedErrorHandler());
		ServletHolder servletHolder = new ServletHolder(servlet());
		servletHolder.getRegistration().setMultipartConfig(new MultipartConfigElement(tempFolder().toString(), -1L, -1L, FILE_SIZE_THRESHOLD));
		final String fullPathSpec;
		if (appendServiceVersionToPathSpec()) {
			fullPathSpec = servicePathSpec() + "/" + servlet().getClass().getPackage().getImplementationVersion();
		} else {
			fullPathSpec = servicePathSpec();
		}
		contextHandler.addServlet(servletHolder, fullPathSpec);
		contextHandler.addServlet(createHearbeatServlet(), "/heartbeat");
		contextHandler.addServlet(createVersionServlet(), "/version");

		RequestLogHandler requestLogHandler = new RequestLogHandler();
		NCSARequestLog requestLog = new NCSARequestLog(accessLogFile().toString());
		requestLog.setRetainDays(90);
		requestLog.setAppend(true);
		requestLog.setExtended(false);
		requestLogHandler.setRequestLog(requestLog);

		HandlerCollection handlers = new HandlerCollection();
		handlers.setHandlers(new Handler[] {
				contextHandler,
				new DefaultHandler(),
				requestLogHandler
		});
		server.setHandler(handlers);

		server.start();
		server.join();
	}

	private ServletHolder createVersionServlet() {
		ServletHolder versionServlet = new ServletHolder(new HttpServlet() {
			private static final long serialVersionUID = -6359788282999575281L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				String version = servlet().getClass().getPackage().getImplementationVersion();
				if (Strings.isNullOrEmpty(version)) {
					version = "dev";
				}

				resp.setStatus(HttpServletResponse.SC_OK);
				resp.setContentType("text/plain");
				resp.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0,must-revalidate,no-cache,no-store");
				resp.addHeader(HttpHeaders.PRAGMA, "no-cache");
				resp.getWriter().println(version);
			}
		});
		return versionServlet;
	}

	private ServletHolder createHearbeatServlet() {
		ServletHolder hearbeatServlet = new ServletHolder(new HttpServlet() {
			private static final long serialVersionUID = -6359788282999575281L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				doHead(req, resp);
				resp.getWriter().println("Server is online");
			}

			@Override
			protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.setStatus(HttpServletResponse.SC_OK);
				resp.setContentType("text/plain");
				resp.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0,must-revalidate,no-cache,no-store");
				resp.addHeader(HttpHeaders.PRAGMA, "no-cache");
			}
		});
		return hearbeatServlet;
	}

	/**
	 * Stops the embedded Jetty server.
	 *
	 * @throws Exception
	 */
	public void stop() throws Exception {
		server.stop();
	}
}
