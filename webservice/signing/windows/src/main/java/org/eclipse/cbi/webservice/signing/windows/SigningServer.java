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

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.cbi.util.ProcessExecutor;
import org.eclipse.cbi.util.PropertiesReader;
import org.eclipse.cbi.webservice.server.EmbeddedServer;
import org.eclipse.cbi.webservice.server.EmbeddedServerProperties;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

public class SigningServer {

	@Option(name="-c",usage="configuration file")
	private String configurationFilePath = "windows-signing-service.properties";
	
	@Argument
	private List<String> arguments = new ArrayList<String>();
	
    public static void main(String[] args) throws Exception {
    	new SigningServer().doMain(FileSystems.getDefault(), args);
    }

	private void doMain(FileSystem fs, String[] args) throws Exception, InterruptedException {
		if (parseCmdLineArguments(fs, args)) {
			final Path confPath = fs.getPath(configurationFilePath);
			final EmbeddedServerProperties serverConf = new EmbeddedServerProperties(PropertiesReader.create(confPath));
			final OSSLSigncodeProperties conf = new OSSLSigncodeProperties(PropertiesReader.create(confPath));
			final Path tempFolder = serverConf.getTempFolder();

			final OSSLCodesigner codesigner = OSSLCodesigner.builder()
				.osslsigncode(conf.getOSSLSigncode())
				.timeout(conf.getTimeout())
				.pkcs12(conf.getPKCS12())
				.pkcs12Password(conf.getPKCS12Password())
				.description(conf.getDescription())
				.uri(conf.getURI())
				.timestampURI(conf.getTimestampURI())
				.tempFolder(tempFolder)
				.processExecutor(new ProcessExecutor.BasicImpl())
				.build();
			
			final SigningServlet codeSignServlet = SigningServlet.builder()
				.osslCodesigner(codesigner)
				.tempFolder(tempFolder)
				.build();
			
			final EmbeddedServer server = EmbeddedServer.builder()
				.port(serverConf.getServerPort())
				.accessLogFile(serverConf.getAccessLogFile())
				.servicePathSpec(serverConf.getServicePathSpec())
				.appendServiceVersionToPathSpec(serverConf.isServiceVersionAppendedToPathSpec())
				.servlet(codeSignServlet)
				.tempFolder(tempFolder)
				.log4jConfiguration(serverConf.getLog4jProperties())
				.build();

			server.start();
		}
	}

	private boolean parseCmdLineArguments(FileSystem fs, String[] args) {
		CmdLineParser parser = new CmdLineParser(this);
		parser.getProperties().withUsageWidth(80);

        try {
            // parse the arguments.
            parser.parseArgument(args);
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java -jar windows-signing-service-x.y.z.jar [options...]");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();

            // print option sample. This is useful some time
            System.err.println("  Example: java -jar windows-signing-service-x.y.z.jar " + parser.printExample(OptionHandlerFilter.REQUIRED));

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
