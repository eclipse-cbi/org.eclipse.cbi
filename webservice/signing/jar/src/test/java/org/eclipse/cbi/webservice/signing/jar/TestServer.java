package org.eclipse.cbi.webservice.signing.jar;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.PropertyConfigurator;
import org.eclipse.cbi.webservice.server.EmbeddedServerConfiguration;
import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;


public class TestServer {
	
	public static void main(String[] args) throws Exception {
		new TestServer().start(FileSystems.getDefault(), args);
	}
	
	private void start(FileSystem fileSystem, String[] args) throws Exception {
		CLIOption options = CLIOption.getOptions(fileSystem, args);
		if (options == null || options.isHelpMode()) {
			return;
		}
		
		System.out.println("Starting test signing server at http://localhost:" + options.getServerPort() + options.getServicePathSpec());
		System.out.println("Dummy certificates, temporary files and logs are stored in folder: " + options.getDirectory());
		System.out.println("Jarsigner executable is: " + options.getJarSigner());
		new SigningServer().startServer(options, options);
	}

	private static final class CLIOption implements EmbeddedServerConfiguration, JarSignerConfiguration {
		
		@Option(name="-directory", usage="directory where certificates, temporary files and logs will be stored")
		private String directory;
		
		@Option(name="-jarsigner", usage="path to the jarsigner executable to use")
		private String jarsigner;
		
		@Option(name="-tsa", usage="timestamp authority to ne used by jarsigner")
		private String tsa = "https://timestamp.geotrust.com/tsa";
		
		@Option(name="-port", usage="port number this server listen to request")
		private int port = 3138;
		
		@Option(name="-h", aliases={"-help",}, usage="print the help")
		private boolean help = false;

		private Path keystore;
		
		private Path directoryPath;
		
		private final FileSystem fs;

		private Path jarSignerPath;

		private CLIOption(FileSystem fs) {
			this.fs = fs;
		}

		public static CLIOption getOptions(FileSystem fs, String[] args) {
			final CLIOption ret = new CLIOption(fs);
			final CmdLineParser parser = new CmdLineParser(ret);
			parser.getProperties().withUsageWidth(80);

			try {
	            // parse the arguments.
	            parser.parseArgument(args);
	        } catch( CmdLineException e ) {
	        	printUsage(parser, System.err);
	        	e.printStackTrace();
	            return null;
	        }
	        
	        if (ret.help) {
	        	ret.help = false; // workaround display default value;
	            printUsage(parser, System.out);
	            ret.help = true;
	        } else {
	        	try {
		        	ret.directoryPath = ret.checkDirectoryPath();
		        	ret.jarSignerPath = ret.checkJarSignerPath();
		        	
					PropertyConfigurator.configure(ret.getLog4jProperties());
		        	ret.keystore = ret.createKeyAndStore(ret.getDirectory().resolve("keystore.jks"));
	        	} catch (Exception e) {
	        		printUsage(parser, System.err);
	        		e.printStackTrace();
	        		return null;
	        	}
	        }

	        return ret;
		}

		private static void printUsage(final CmdLineParser parser, PrintStream stream) {
			stream.println("java -jar dummy-signing-server-x.y.z.jar [options...]");
			// print the list of available options
			parser.printUsage(stream);
			stream.println();

			// print option sample. This is useful some time
			stream.println("  Example: java -jar dummy-signing-server-x.y.z.jar " + parser.printExample(OptionHandlerFilter.REQUIRED));
		}
		
		public boolean isHelpMode() {
			return help;
		}
		
		private Path createKeyAndStore(Path keystore) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InvalidKeyException, SignatureException, NoSuchProviderException {
			ProcessExecutor processExecutor = new ProcessExecutor.BasicImpl();
			
			Path keytool = getJarSigner().getParent().resolve("keytool");
			
			ImmutableList<String> command = ImmutableList.<String>builder()
					.add(keytool.toAbsolutePath().toString())
					.add("-genkey")
					.add("-keyalg", "RSA")
					.add("-keystore", keystore.toAbsolutePath().toString())
					.add("-storepass", getKeystorePassword())
					.add("-keypass", getKeystorePassword())
					.add("-alias", getKeystoreAlias())
					.add("-dname", "CN=localhost, O=acme.org")
					.build();
			
			processExecutor.exec(command, 20L, TimeUnit.SECONDS);
			
			return keystore;
		}

		private Path getDirectory() {
			return directoryPath;
		}

		private Path checkDirectoryPath() {
			final Path ret;
			
			if (Strings.isNullOrEmpty(directory)) {
				try {
					ret = Files.createTempDirectory(TestServer.class.getSimpleName() + "-");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				ret = fs.getPath(directory);
			}
			
			if (!Files.exists(ret)) {
	        	try {
					Files.createDirectories(ret);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
	        }
			
			return ret;
		}
		
		public Path getJarSigner() {
			return jarSignerPath;
		}

		@Override
		public String getJavaArgs() {
			return "";
		}

		private Path checkJarSignerPath() {
			final Path ret;
			
			if (Strings.isNullOrEmpty(jarsigner)) {
				Path javaHome = fs.getPath(System.getProperty("java.home"));
				if ("jre".equals(javaHome.getFileName().toString())) {
					javaHome = javaHome.getParent();
				}
				ret = javaHome.resolve("bin").resolve("jarsigner");
			} else {
				ret = fs.getPath(jarsigner);
			}
			
			if (!Files.exists(ret)) {
	        	throw new IllegalArgumentException("Path to jarsigner '" + ret + "' does not exists");
	        }
			
			return ret;
		}

		@Override
		public Path getKeystore() {
			return keystore;
		}
		
		@Override
		public String getStoreType() {
			return null;
		}

		@Override
		public String getKeystoreAlias() {
			return "acme.org";
		}

		@Override
		public String getKeystorePassword() {
			return "keystorePassword";
		}

		@Override
		public URI getTimeStampingAuthority() {
			return URI.create(tsa);
		}

		@Override
		public long getTimeout() {
			return TimeUnit.MINUTES.toSeconds(2);
		}

		@Override
		public String getProviderClass() {
			return null;
		}

		@Override
		public String getProviderArg() {
			return null;
		}

		@Override
		public Path getCertificateChain() {
			return null;
		}

		@Override
		public Path getGoogleCloudCredentials() {
			return null;
		}

		@Override
		public String getHttpProxyHost() {
			return getProxyHostFromEnv("http");
		}
		
		private String getProxyHostFromEnv(String protocol) {
			final String ret;
			final String prop = System.getProperty(protocol + ".proxyHost");
			if (Strings.isNullOrEmpty(prop)) {
				URI env = getEnvProxy(protocol);
				if (env == null) {
					ret = "";
				} else {
					StringBuilder sb = new StringBuilder();
					if (env.getScheme() == null) {
						sb.append(protocol).append("://");
					}
					sb.append(env.getHost());
					ret = sb.toString();
				}
			} else {
				ret = prop;
			}
			return ret;
		}
		
		private int getProxyPortFromEnv(String protocol) {
			final int ret;
			final String prop = System.getProperty(protocol + ".proxyPort");
			if (Strings.isNullOrEmpty(prop)) {
				URI env = getEnvProxy(protocol);
				if (env == null) {
					ret = -1;
				} else {
					ret = env.getPort();
				}
			} else {
				ret = Integer.valueOf(prop);
			}
			return ret;
		}
		
		private static URI getEnvProxy(String scheme) {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(scheme));
			final URI ret;
			String envVariableName = scheme.toLowerCase() + "_proxy";
			String env = System.getenv(envVariableName);
			if (Strings.isNullOrEmpty(env)) {
				envVariableName = scheme.toUpperCase() + "_PROXY";
				env = System.getenv(envVariableName);
				if (Strings.isNullOrEmpty(env)) {
					if (!scheme.equals("all")) {
						ret = getEnvProxy("all");
					} else {
						ret = null;
                                        }
				} else {
					ret = URI.create(env);
					if (ret.getScheme() != null && !scheme.equalsIgnoreCase(ret.getScheme())) {
						System.err.println("Scheme specified in environment variable '" + envVariableName + "' is not equals to '" + scheme + "'");
					}
				}
			} else {
				ret = URI.create(env);
				if (ret.getScheme() != null && !scheme.equalsIgnoreCase(ret.getScheme())) {
					System.err.println("Scheme specified in environment variable '" + envVariableName + "' is not equals to '" + scheme + "'");
				}
			}
			
			return ret;
		}

		@Override
		public String getHttpsProxyHost() {
			return getProxyHostFromEnv("https");
		}

		@Override
		public int getHttpProxyPort() {
			return getProxyPortFromEnv("http");
		}

		@Override
		public int getHttpsProxyPort() {
			return getProxyPortFromEnv("https");
		}

		@Override
		public Path getAccessLogFile() {
			return getDirectory().resolve("access.log");
		}

		@Override
		public Path getTempFolder() {
			final Path tempFolder = getDirectory().resolve("temp");
			if (!Files.exists(tempFolder)) {
				try {
					Files.createDirectories(tempFolder);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			return tempFolder;
		}

		@Override
		public int getServerPort() {
			return port;
		}

		@Override
		public String getServicePathSpec() {
			return "/jarsigner";
		}

		@Override
		public boolean isServiceVersionAppendedToPathSpec() {
			return false;
		}

		@Override
		public Properties getLog4jProperties() {
			final Properties log4jConf = new Properties();
			log4jConf.setProperty("log4j.rootLogger", "INFO, file");

			log4jConf.setProperty("log4j.appender.file", "org.apache.log4j.RollingFileAppender");
			log4jConf.setProperty("log4j.appender.file.File", getDirectory().resolve("server.log").toAbsolutePath().toString());
			log4jConf.setProperty("log4j.appender.file.MaxFileSize", "10MB");
			log4jConf.setProperty("log4j.appender.file.MaxBackupIndex", "10");
			log4jConf.setProperty("log4j.appender.file.layout", "org.apache.log4j.PatternLayout");
			log4jConf.setProperty("log4j.appender.file.layout.ConversionPattern",
					"%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n");
			return log4jConf;
		}
	}
}
