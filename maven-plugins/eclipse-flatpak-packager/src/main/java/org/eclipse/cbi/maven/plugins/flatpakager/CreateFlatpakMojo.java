/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.flatpakager;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.eclipse.cbi.maven.ExceptionHandler;
import org.eclipse.cbi.maven.MavenLogger;
import org.eclipse.cbi.maven.http.AbstractCompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpRequest.Builder;
import org.eclipse.cbi.maven.http.HttpResult;
import org.eclipse.cbi.maven.http.RetryHttpClient;
import org.eclipse.cbi.maven.http.apache.ApacheHttpClient;
import org.eclipse.cbi.maven.plugins.flatpakager.model.Manifest;
import org.eclipse.cbi.maven.plugins.flatpakager.model.Module;
import org.eclipse.cbi.maven.plugins.flatpakager.model.Source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Create a Flatpak application from the archived product output of the
 * tycho-p2-director-plugin and exports the application to a Flatpak repository.
 * Signing the Flatpak repository is optional, but highly recommended.
 * 
 * @since 1.1.5
 */
@Mojo(name = "package-flatpak", defaultPhase = LifecyclePhase.PACKAGE)
public class CreateFlatpakMojo extends AbstractMojo {

	/**
	 * A unique identifier for the Flatpak application, for example:
	 * "org.eclipse.Platform"
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true)
	private String id;

	// The generated idiomatic Flatpak-style application ID.
	private String flatpakId;

	/**
	 * A friendly name for the Flatpak application that will be shown to use in
	 * software centres and in desktop environments.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true)
	private String name;

	/**
	 * A short description of the Flatpak application that may be shown in distro
	 * software centres and in desktop environments.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true)
	private String summary;

	/**
	 * A longer description of the Flatpak application that may be shown in distro
	 * software centres.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true)
	private String description;

	/**
	 * An optional email address that can be used to contact the project about
	 * invalid or incomplete metadata.
	 * 
	 * @since 1.1.5
	 */
	@Parameter
	private String maintainer;

	/**
	 * An optional list of URLs to screenshots that may be shown in distro software
	 * centres.
	 * 
	 * @since 1.1.5
	 */
	@Parameter
	private List<String> screenshots;

	/**
	 * The branch of the application, defaults to "master" but can be set to
	 * identify the stream, for example: "Oxygen" or "4.7"
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = Manifest.DEFAULT_BRANCH)
	private String branch;

	/**
	 * The filename or path to the main binary of the application, defaults to
	 * "eclipse"
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = Manifest.DEFAULT_COMMAND)
	private String command;

	/**
	 * The version of the Gnome runtime on which to build the Flatpak application.
	 * Defaults to "3.28"
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = Manifest.DEFAULT_RUNTIMEVERSION)
	private String runtimeVersion;

	/**
	 * An {@code .tar.gz} or {@code .zip} file containing a Linux product from which
	 * to generate a Flatpak application.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true)
	private File source;

	/**
	 * An optional list of additional source files that should be installed into the
	 * Flatpak application. These files are simply copied into the sandbox at the
	 * given location. For example:
	 *
	 * <pre>
	 * &lt;additionalSources>
	 * 	&lt;additionalSource>
	 * 		&lt;source>/path/to/local/file&lt;/source>
	 * 		&lt;destination>/path/to/location/inside/the/sandbox&lt;/destination>
	 * 	&lt;/additionalSources>
	 * &lt;/additionalSources>
	 * </pre>
	 *
	 * @since 1.1.5
	 */
	@Parameter
	private final List<AdditionalSource> additionalSources = new ArrayList<>();

	/**
	 * The minimum version of Flatpak needed at runtime, that this application will
	 * support. Defaults to "0.8.8" (the version available on RHEL 7.5)
	 *
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = "0.8.8")
	private String minFlatpakVersion;

	/**
	 * The license that the Flatpak application is distributed under. It should be a
	 * valid <a href="https://spdx.org/specifications">SPDX license expression</a>.
	 * For example:
	 * <ul>
	 * <li>{@code EPL-1.0}</li>
	 * <li>{@code Apache-2.0 AND LGPL-3.0-or-later}</li>
	 * </ul>
	 * <p>
	 * A full list of recognized licenses and their identifiers can be found at the
	 * <a href="https://spdx.org/licenses/">SPDX OpenSource License Registry</a>.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = "EPL-1.0")
	private String license;

	/**
	 * The repository to which the new Flatpak application should be exported. If
	 * not specified, a new repository will be created inside the build directory.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.repo")
	private File repository;

	/**
	 * The URL at which the Flatpak repository will be available to users, this is
	 * embedded into generated "flatpakrepo" and "flatpakref" files.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.repoUrl", defaultValue = "http://www.example.com/flatpak/repo")
	private String repositoryUrl;

	/**
	 * Skips the execution of this plugin.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.skip", defaultValue = "false")
	private boolean skip;

	/**
	 * Whether the build should be stopped if the packaging process fails.
	 *
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.continueOnFail", defaultValue = "false")
	private boolean continueOnFail;

	/**
	 * Whether the Flatpak application should be GPG signed.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.sign", defaultValue = "false")
	private boolean sign;

	/**
	 * The GPG key to use when signing the Flatpak application.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.gpgkey")
	private String gpgKey;

	/**
	 * The location of the GPG secure keyring to use when signing the Flatpak
	 * application. Defaults to the ".gnupg" directory in the user's home.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.gpghome")
	private File gpgHome;

	/**
	 * An optional URL for the Flatpak application packaging web service.
	 * <p>
	 * By default, the Flatpak application will be generated locally. If the Flatpak
	 * tools are unavailable locally, or the build is running on an architecture or
	 * OS where Flatpak is not supported, then a URL may be specified that indicated
	 * the location of the Flatpak packaging web service. For example:
	 * <p>
	 * http://build.eclipse.org:31338/flatpak-packager
	 *
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.serviceUrl")
	private String serviceUrl;

	/**
	 * Defines the timeout in milliseconds for any communication with the
	 * packaging web service. Defaults to zero, which is interpreted as an infinite
	 * timeout. This only means something if a {@link #serviceUrl} is specified.
	 *
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.timeoutMillis", defaultValue = "0")
	private int timeoutMillis;

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException {
		if (skip) {
			getLog().info("Skipping packaging Flatpak application");
			return;
		}

		final ExceptionHandler exceptionHandler = new ExceptionHandler(getLog(), continueOnFail);
		try {
			// By convention, Flatpaks have a capitalised final segment in their IDs
			String segs[] = id.split("\\.");
			segs[segs.length - 1] = segs[segs.length - 1].substring(0, 1).toUpperCase()
					+ segs[segs.length - 1].substring(1);
			flatpakId = String.join(".", segs);

			// Ensure we have a valid archive for the source
			if (!source.exists()) {
				exceptionHandler.handleError("'source' file must exist");
				return;
			}
			if (!source.toPath().getFileName().toString().endsWith(".tar.gz")
					&& !source.toPath().getFileName().toString().endsWith(".zip")) {
				exceptionHandler.handleError("'source' file name must ends with '.tar.gz' or '.zip'");
				return;
			}

			// Setup some directories
			File targetDir = new File(project.getBuild().getDirectory(), "flatpak");
			if (!targetDir.isDirectory()) {
				targetDir.mkdirs();
			}
			if (repository == null) {
				repository = new File(targetDir, "repo");
			}
			if (gpgHome == null) {
				gpgHome = new File(System.getProperty("user.home"), ".gnupg");
			}

			String armouredGpgKey = fetchGpgKey(exceptionHandler);
			generateManifest(targetDir);
			buildAndSignRepo(exceptionHandler, targetDir);
			generateRefFiles(armouredGpgKey);
		} catch (IOException | InterruptedException e) {
			exceptionHandler.handleError("Packaging of Flatpak application failed", e);
		}
	}

	private String fetchGpgKey(final ExceptionHandler exceptionHandler) throws IOException, MojoExecutionException {
		if (sign) {
			try (FileInputStream in = new FileInputStream(new File(gpgHome, "pubring.gpg"))) {
				PGPPublicKeyRingCollection pgpKeyRings = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(in),
						new JcaKeyFingerprintCalculator());
				PGPPublicKey pgpKey = null;
				Iterator<PGPPublicKeyRing> keyRingIter = pgpKeyRings.getKeyRings();
				while (keyRingIter.hasNext()) {
					PGPPublicKeyRing keyRing = keyRingIter.next();
					Iterator<PGPPublicKey> keyIter = keyRing.getPublicKeys();
					while (keyIter.hasNext()) {
						PGPPublicKey key = keyIter.next();
						if (!key.hasRevocation() && gpgKey != null
								&& Long.toHexString(key.getKeyID()).toLowerCase().endsWith(gpgKey.toLowerCase())) {
							pgpKey = key;
						}
					}
				}
				if (pgpKey == null) {
					exceptionHandler.handleError("Unable to locate valid GPG key");
					sign = false;
				} else {
					return new String(Base64.getEncoder().encode(pgpKey.getEncoded()), StandardCharsets.UTF_8);
				}
			} catch (PGPException e) {
				exceptionHandler.handleError("Unable to locate valid GPG key", e);
				sign = false;
			}
		}
		return null;
	}

	private void generateManifest(File targetDir) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);

		// TODO: Remove this when flatpak SDK images are fixed upstream
		File cacertsFile = new File(System.getProperty("java.home"), "lib/security/cacerts");
		additionalSources.add(new AdditionalSource(cacertsFile, new File("/app/eclipse/java/lib/security/cacerts")));

		// Copy all sources into the target directory
		Files.copy(source.toPath(), Paths.get(targetDir.getAbsolutePath(), source.getName()));
		for (AdditionalSource addSource : additionalSources) {
			Files.copy(addSource.getSource().toPath(),
					Paths.get(targetDir.getAbsolutePath(), addSource.getSource().getName()));
		}

		// Generate freedesktop desktop file
		File desktopFile = new File(targetDir, flatpakId + ".desktop");
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(desktopFile), StandardCharsets.UTF_8))) {
			bw.write("[Desktop Entry]\n");
			bw.write("Type=Application\n");
			bw.write("Name=" + name + "\n");
			bw.write("Comment=" + summary + "\n");
			bw.write("Icon=" + flatpakId + "\n");
			bw.write("Exec=" + command + "\n");
			bw.write("Terminal=false\n");
			bw.write("Categories=Development;IDE;\n");
			bw.write("\n");
			bw.flush();
		}
		additionalSources
				.add(new AdditionalSource(desktopFile, new File("/app/share/applications", desktopFile.getName())));

		// Generate appstream metadata file
		File appdataFile = new File(targetDir, flatpakId + ".appdata.xml");
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(appdataFile), StandardCharsets.UTF_8))) {
			bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			bw.write("<component type=\"desktop-application\">\n");
			bw.write("  <id>" + flatpakId + ".desktop</id>\n");
			bw.write("  <launchable type=\"desktop-id\">" + flatpakId + ".desktop</launchable>\n");
			bw.write("  <metadata_license>" + license + "</metadata_license>\n");
			bw.write("  <project_license>" + license + "</project_license>\n");
			bw.write("  <name>" + name + "</name>\n");
			bw.write("  <summary>" + summary + "</summary>\n");
			bw.write("  <description><p>" + description + "</p></description>\n");
			if (screenshots != null) {
				bw.write("  <screenshots>\n");
				boolean defaultShot = true;
				for (String shot : screenshots) {
					if (defaultShot) {
						bw.write("    <screenshot type=\"default\">\n");
						defaultShot = false;
					} else {
						bw.write("    <screenshot>\n");
					}
					bw.write("      <image>" + shot + "</image>\n");
					bw.write("    </screenshot>\n");
				}
				bw.write("  </screenshots>\n");
			}
			if (maintainer != null) {
				bw.write("  <update_contact>" + maintainer + "</update_contact>\n");
			}
			if (project.getOrganization() != null && project.getOrganization().getName() != null) {
				bw.write("  <developer_name>" + project.getOrganization().getName() + "</developer_name>\n");
			}
			bw.write("  <kudos>\n");
			bw.write("    <kudo>HiDpiIcon</kudo>\n");
			bw.write("    <kudo>ModernToolkit</kudo>\n");
			bw.write("    <kudo>UserDocs</kudo>\n");
			bw.write("  </kudos>\n");
			if (project.getIssueManagement() != null && project.getIssueManagement().getUrl() != null) {
				bw.write("  <url type=\"bugtracker\">" + project.getIssueManagement().getUrl() + "</url>\n");
			}
			if (project.getOrganization() != null && project.getOrganization().getUrl() != null) {
				bw.write("  <url type=\"homepage\">" + project.getOrganization().getUrl() + "</url>\n");
			}
			bw.write("</component>\n");
			bw.write("\n");
			bw.flush();
		}
		additionalSources.add(new AdditionalSource(appdataFile, new File("/app/share/appdata", appdataFile.getName())));

		// Finally, generate the makefile that installs all the sources
		File makeFile = new File(targetDir, "Makefile");
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(makeFile), StandardCharsets.UTF_8))) {
			bw.write("all:\n");
			bw.write("	true\n");
			bw.write("\n");
			bw.write("install:\n");
			bw.write("	cp -pr /usr/lib/sdk/openjdk9/jvm/openjdk-9 eclipse/java\n");
			bw.write("	rm -rf eclipse/java/man eclipse/java/demo\n");
			bw.write("	mv eclipse /app\n");
			for (AdditionalSource addSource : additionalSources) {
				bw.write("	mkdir -p $(shell dirname \"" + addSource.getDestination() + "\")\n");
				bw.write("	mv " + addSource.getDestination().getName() + " " + addSource.getDestination() + "\n");
			}
			bw.write("	@mkdir -p /app/bin\n");
			bw.write("	@for bin in /app/eclipse/java/bin/* /app/eclipse/" + command
					+ " ; do ln -s $$bin /app/bin ; done\n");
			bw.write("	for px in 32 48 64 128 256 512 1024 ; do \\\n");
			bw.write("		mkdir -p /app/share/icons/hicolor/$${px}x$${px}/apps ; \\\n");
			bw.write("		cp -p /app/eclipse/plugins/" + id + "_*/" + command
					+ "$${px}.png /app/share/icons/hicolor/$${px}x$${px}/apps/" + flatpakId + ".png ; \\\n");
			bw.write("	done\n");
			bw.write("\n");
			bw.flush();
		}
		// Makefile doesn't need a real sandbox destination because we use it only at
		// build time and not runtime
		additionalSources.add(new AdditionalSource(makeFile, null));

		// Generate the Flatpak application manifest
		Module.Builder moduleBuilder = Module.builder().name("eclipse").noAutogen(true);

		Source sourceArchive = Source.builder().type("archive").path(source.getName()).stripComponents(0).build();
		moduleBuilder.addSource(sourceArchive);
		for (AdditionalSource addSource : additionalSources) {
			Source sourceFile = Source.builder().type("file").path(addSource.getSource().getName())
					.destFilename(addSource.getDestination().getName()).build();
			moduleBuilder.addSource(sourceFile);
		}

		Manifest manifest = Manifest.builder().id(flatpakId).branch(branch).runtimeVersion(runtimeVersion)
				.addModule(moduleBuilder.build()).addFinishArg("--require-version=" + minFlatpakVersion).build();

		mapper.writeValue(new File(targetDir, flatpakId + ".json"), manifest);
	}

	private void buildAndSignRepo(ExceptionHandler exceptionHandler, File targetDir)
			throws InterruptedException, IOException, MojoExecutionException {
		if (sign) {
			getLog().info("Creating and signing Flatpak application from " + source.getName());
		} else {
			getLog().info("Creating Flatpak application from " + source.getName());
		}

		if (serviceUrl == null || serviceUrl.isEmpty()) {
			// Generate locally
			List<String> builderArgs = new ArrayList<>();
			builderArgs.add("flatpak-builder");
			builderArgs.add("--disable-cache");
			builderArgs.add("--disable-download");
			builderArgs.add("--disable-updates");
			builderArgs.add("--repo=" + repository.getAbsolutePath());
			if (sign) {
				builderArgs.add("--gpg-sign=" + gpgKey);
				builderArgs.add("--gpg-homedir=" + gpgHome);
			}
			builderArgs.add(new File(targetDir, "build").getAbsolutePath());
			builderArgs.add(new File(targetDir, flatpakId + ".json").getAbsolutePath());
			executeProcess(exceptionHandler, builderArgs, targetDir);

			List<String> deltaArgs = new ArrayList<>();
			deltaArgs.add("flatpak");
			deltaArgs.add("build-update-repo");
			deltaArgs.add("--generate-static-deltas");
			if (sign) {
				deltaArgs.add("--gpg-sign=" + gpgKey);
				deltaArgs.add("--gpg-homedir=" + gpgHome);
			}
			deltaArgs.add(repository.getAbsolutePath());
			executeProcess(exceptionHandler, deltaArgs, targetDir);
		} else {
			// Generate remotely
			HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
					.maxRetries(3).waitBeforeRetry(10, TimeUnit.SECONDS).log(new MavenLogger(getLog())).build();
			Builder requestBuilder = HttpRequest.on(URI.create(serviceUrl));

			requestBuilder.withParam("manifest", new File(targetDir, flatpakId + ".json").toPath());
			requestBuilder.withParam("source", source.toPath());
			requestBuilder.withParam("additionalSources", Integer.toString(additionalSources.size()));
			for (int i = 0; i < additionalSources.size(); i++) {
				AdditionalSource addSource = additionalSources.get(i);
				requestBuilder.withParam("additionalSource" + i,
						Paths.get(targetDir.getAbsolutePath(), addSource.getSource().getName()));
			}
			requestBuilder.withParam("sign", Boolean.toString(sign));
			executeProcessOnRemoteServer(httpClient, requestBuilder.build());
		}
	}

	private void generateRefFiles(String armouredGpgKey) throws IOException {
		// Generate Flatpak ref file
		File flatpakrefFile = new File(repository.getParentFile(), flatpakId + ".flatpakref");
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(flatpakrefFile), StandardCharsets.UTF_8))) {
			bw.write("[Flatpak Ref]\n");
			bw.write("Title=" + name + "\n");
			bw.write("Name=" + flatpakId + "\n");
			bw.write("Branch=" + branch + "\n");
			if (repositoryUrl != null) {
				bw.write("Url=" + repositoryUrl + "\n");
			}
			bw.write("IsRuntime=False\n");
			bw.write("RuntimeRepo=https://flathub.org/repo/flathub.flatpakrepo\n");
			if (sign) {
				bw.write("GPGKey=" + armouredGpgKey + "\n");
			}
			bw.write("\n");
			bw.flush();
		}

		// Generate Flatpak repo file
		File flatpakrepoFile = new File(repository.getParentFile(), flatpakId + ".flatpakrepo");
		try (BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(flatpakrepoFile), StandardCharsets.UTF_8))) {
			bw.write("[Flatpak Repo]\n");
			bw.write("Title=" + name + " Repository\n");
			bw.write("Comment=" + summary + "\n");
			if (project.getOrganization() != null && project.getOrganization().getUrl() != null) {
				bw.write("Homepage=" + project.getOrganization().getUrl() + "\n");
			}
			if (repositoryUrl != null) {
				bw.write("Url=" + repositoryUrl + "\n");
			}
			if (sign) {
				bw.write("GPGKey=" + armouredGpgKey + "\n");
			}
			bw.write("\n");
			bw.flush();
		}
	}

	private void executeProcess(ExceptionHandler exceptionHandler, List<String> args, File targetDir)
			throws InterruptedException, IOException, MojoExecutionException {
		getLog().debug("Executing: " + String.join(" ", args));
		final ProcessBuilder p = new ProcessBuilder(args).directory(targetDir).redirectErrorStream(true);
		final Process process = p.start();
		final BufferedInputStream bis = new BufferedInputStream(process.getInputStream());
		final byte[] buffer = new byte[1024];
		int endOfStream = 0;
		do {
			endOfStream = bis.read(buffer);
			String output = new String(buffer, 0, endOfStream == -1 ? 0 : endOfStream, StandardCharsets.UTF_8);
			for (String s : output.split("\n")) {
				getLog().info(s);
			}
		} while (endOfStream != -1);

		if (process.waitFor() != 0) {
			exceptionHandler.handleError("Process exited abnormally");
		}
	}

	private void executeProcessOnRemoteServer(HttpClient httpClient, HttpRequest request) throws IOException {
		getLog().debug("Executing remotely: " + request.toString());
		final HttpRequest.Config config = HttpRequest.Config.builder().timeout(Duration.ofMillis(timeoutMillis))
				.build();
		httpClient.send(request, config,
				new AbstractCompletionListener(source.toPath().getParent(), source.toPath().getFileName().toString(),
						CreateFlatpakMojo.class.getSimpleName(), new MavenLogger(getLog())) {
					@Override
					public void onSuccess(HttpResult result) throws IOException {
						if (result.contentLength() == 0) {
							throw new IOException("Length of the returned content is 0");
						}
						// Tarball of generated Flatpak repository is sent back to us in the reply
						Path rPath = repository.toPath();
						Path tarballPath = rPath.getParent().resolve(rPath.getFileName().toString() + ".tar.gz");
						result.copyContent(tarballPath, StandardCopyOption.REPLACE_EXISTING);
						if (Files.size(tarballPath) == 0) {
							throw new IOException("Size of the returned Flatpak repo is 0");
						}
					}
				});
	}
}
