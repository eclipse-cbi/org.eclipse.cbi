/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.flatpakaging;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.cbi.webservice.server.EmbeddedServer;
import org.eclipse.cbi.webservice.server.EmbeddedServerConfiguration;
import org.eclipse.cbi.webservice.server.EmbeddedServerProperties;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.eclipse.cbi.webservice.util.PropertiesReader;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

/**
 * Entry point, starts an embedded web server to receive requests from the
 * "eclipse-flatpak-packager" maven plug-in.
 */
public class FlatpakagerServer {

	@Option(name = "-c", usage = "configuration file")
	private String configurationFilePath = "flatpak-packaging-service.properties";

	@Argument
	private List<String> arguments = new ArrayList<String>();

	public static void main(String[] args) throws Exception {
		new FlatpakagerServer().doMain(FileSystems.getDefault(), args);
	}

	private void doMain(FileSystem fs, String[] args) throws Exception, InterruptedException {
		if (parseCmdLineArguments(fs, args)) {
			final Path confPath = fs.getPath(configurationFilePath);
			final EmbeddedServerConfiguration serverConf = new EmbeddedServerProperties(
					PropertiesReader.create(confPath));
			final Path tempFolder = serverConf.getTempFolder();

			final ProcessExecutor executor = new ProcessExecutor.BasicImpl();
			final FlatpakagerProperties conf = new FlatpakagerProperties(PropertiesReader.create(confPath));
			final Flatpakager packager = Flatpakager.builder().processExecutor(executor).timeout(conf.getTimeout())
					.gpgHome(conf.getGpghome()).gpgKey(conf.getGpgkey()).repository(conf.getRepository())
					.work(tempFolder.resolve("work")).build();

			final FlatpakagerServlet createServlet = FlatpakagerServlet.builder().tempFolder(tempFolder)
					.packager(packager).build();

			final EmbeddedServer server = EmbeddedServer.builder().port(serverConf.getServerPort())
					.accessLogFile(serverConf.getAccessLogFile()).servicePathSpec(serverConf.getServicePathSpec())
					.appendServiceVersionToPathSpec(serverConf.isServiceVersionAppendedToPathSpec())
					.servlet(createServlet).tempFolder(tempFolder).log4jConfiguration(serverConf.getLog4jProperties())
					.build();

			server.start();
		}
	}

	private boolean parseCmdLineArguments(FileSystem fs, String[] args) {
		CmdLineParser parser = new CmdLineParser(this);
		parser.getProperties().withUsageWidth(80);

		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("java -jar flatpak-packaging-service-x.y.z.jar [options...]");
			parser.printUsage(System.err);
			System.err.println();
			System.err.println("  Example: java -jar flatpak-packaging-service-x.y.z.jar "
					+ parser.printExample(OptionHandlerFilter.REQUIRED));
			return false;
		}

		if (!Files.exists(fs.getPath(configurationFilePath))) {
			System.err.println("Configuration file does not exist: '" + configurationFilePath + "'");
			parser.printUsage(System.err);
			System.err.println();
		}

		return true;
	}
}
