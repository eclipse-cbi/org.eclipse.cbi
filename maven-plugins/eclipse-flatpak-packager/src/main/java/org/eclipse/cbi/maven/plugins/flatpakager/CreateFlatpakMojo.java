/*******************************************************************************
 * Copyright (c) 2017, 2022 Red Hat, Inc. and others.
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
import java.io.FileNotFoundException;
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
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
	 * Creates an additional commit in the Flatpak repo for the application that was
	 * just built, but under a different branch name. This is useful if, for
	 * example, we want a branch that always points to the latest build, even if the
	 * stream changes.
	 * 
	 * @since 1.3.3
	 */
	@Parameter
	private String branchAlias;

	/**
	 * The filename or path to the main binary of the application, defaults to
	 * "eclipse"
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = Manifest.DEFAULT_COMMAND)
	private String command;

	/**
	 * An optional list of MIME types that the application should register with the
	 * desktop environment that it knows how to open. Desktop environments like
	 * Gnome may offer to use the Flatpak application to open files of the types
	 * listed here.
	 * 
	 * @since 1.3.0
	 */
	@Parameter
	private List<String> mimeTypes;

	/**
	 * The runtime on which to build the Flatpak application. Choices are
	 * "org.gnome.Platform" or "org.gnome.Sdk", defaults to "org.gnome.Platform"
	 * 
	 * @since 1.1.6
	 */
	@Parameter(required = true, defaultValue = Manifest.DEFAULT_RUNTIME)
	private String runtime;

	/**
	 * The version of the Gnome runtime on which to build the Flatpak application.
	 * Defaults to "41"
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = Manifest.DEFAULT_RUNTIMEVERSION)
	private String runtimeVersion;

	/**
	 * The minimum version of Flatpak needed at runtime, that this application will
	 * support. Defaults to "1.7.1"
	 *
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = Manifest.DEFAULT_FLATPAKVERSION)
	private String minFlatpakVersion;

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
	 * From 1.1.6, co-ordinates for a Maven artifact may be specified instead of a
	 * source file. For example:
	 * 
	 * <pre>
	 * &lt;additionalSources>
	 * 	&lt;additionalSource>
	 * 		&lt;artifact>
	 * 			&lt;artifactId>&lt;/artifactId>
	 * 			&lt;groupId>&lt;/groupId>
	 * 			&lt;version>&lt;/version>
	 * 		&lt;/artifact>
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
	 * The license that the Flatpak application is distributed under. It should be a
	 * valid <a href="https://spdx.org/specifications">SPDX license expression</a>.
	 * For example:
	 * <ul>
	 * <li>{@code EPL-2.0}</li>
	 * <li>{@code Apache-2.0 AND LGPL-3.0-or-later}</li>
	 * </ul>
	 * <p>
	 * A full list of recognized licenses and their identifiers can be found at the
	 * <a href="https://spdx.org/licenses/">SPDX OpenSource License Registry</a>.
	 * 
	 * @since 1.1.5
	 */
	@Parameter(required = true, defaultValue = "EPL-2.0")
	private String license;

	/**
	 * The license that the application metadata is distributed under. It should be
	 * a valid <a href="https://spdx.org/specifications">SPDX license
	 * expression</a>. For example, the default value is:
	 * <ul>
	 * <li>{@code CC0-1.0}</li>
	 * </ul>
	 * <p>
	 * A full list of recognized licenses and their identifiers can be found at the
	 * <a href="https://spdx.org/licenses/">SPDX OpenSource License Registry</a>.
	 * 
	 * @since 1.3.0
	 */
	@Parameter(required = true, defaultValue = "CC0-1.0")
	private String metadataLicense;

	/**
	 * An optional bundle symbolic name of the branding plug-in for the product. If
	 * not specified, the {@link #id} will be used. This plug-in should contain the
	 * icons that desktop environments may use for application launchers.
	 * 
	 * @since 1.1.6
	 */
	@Parameter
	private String brandingPlugin;

	/**
	 * Whether or not to append the set of default finish args, defaults to "true"
	 * and the default finish args are as follows:
	 * <ul>
	 * <li>--filesystem=host</li>
	 * <li>--share=network</li>
	 * <li>--share=ipc</li>
	 * <li>--socket=x11</li>
	 * <li>--socket=wayland</li>
	 * <li>--allow=devel</li>
	 * <li>--socket=session-bus</li>
	 * <li>--device=dri</li>
	 * <li>--env=PATH=/app/bin:/app/jdk/bin:/usr/bin</li>
	 * <li>--require-version=1.0.2</li>
	 * </ul>
	 * 
	 * @since 1.1.8
	 */
	@Parameter(defaultValue = "true")
	private boolean appendDefaultFinishArgs;

	/**
	 * An optional list of finish args to be used. The full set of finish args that
	 * may be used are detailed in the <a href=
	 * "http://docs.flatpak.org/en/latest/flatpak-command-reference.html#flatpak-build-finish">Flatpak
	 * command reference</a>. The given list of finish args will be appended to the
	 * default set if the {@link #appendDefaultFinishArgs} flag is also set to true.
	 * 
	 * @since 1.1.8
	 */
	@Parameter
	private List<String> finishArgs;

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
	 * Defines the timeout in milliseconds for any communication with the packaging
	 * web service. Defaults to zero, which is interpreted as an infinite timeout.
	 * This only means something if a {@link #serviceUrl} is specified.
	 *
	 * @since 1.1.5
	 */
	@Parameter(property = "cbi.flatpakager.timeoutMillis", defaultValue = "0")
	private int timeoutMillis;

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	@Parameter(defaultValue = "${session}", required = true, readonly = true)
	private MavenSession session;

	@Component
	private ArtifactResolver artifactResolver;

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
				// Check if GNUPGHOME is set in the environment, otherwise default to the standard location
				String gnupgHome = System.getenv("GNUPGHOME");
				if (gnupgHome != null) {
					gpgHome = new File(gnupgHome);
				} else {
					gpgHome = new File(System.getProperty("user.home"), ".gnupg");
				}
			}

			String armouredGpgKey = fetchGpgKey(exceptionHandler);
			generateManifest(targetDir);
			buildAndSignRepo(exceptionHandler, targetDir);
			generateRefFiles(armouredGpgKey);
		} catch (IOException | InterruptedException | ArtifactResolverException e) {
			exceptionHandler.handleError("Packaging of Flatpak application failed", e);
		}
	}

	private String fetchGpgKey(final ExceptionHandler exceptionHandler) throws IOException, MojoExecutionException {
		if (sign) {
			try (FileInputStream in = new FileInputStream(new File(gpgHome, "pubring.gpg"))) {
				PGPPublicKeyRingCollection pgpKeyRings = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(in),
						new JcaKeyFingerprintCalculator());
				PGPPublicKeyRing pgpKeyRing = null;
				Iterator<PGPPublicKeyRing> keyRingIter = pgpKeyRings.getKeyRings();
				while (keyRingIter.hasNext()) {
					PGPPublicKeyRing keyRing = keyRingIter.next();
					Iterator<PGPPublicKey> keyIter = keyRing.getPublicKeys();
					while (keyIter.hasNext()) {
						PGPPublicKey key = keyIter.next();
						if (!key.hasRevocation() && gpgKey != null && !gpgKey.isEmpty()
								&& Long.toHexString(key.getKeyID()).toLowerCase().endsWith(gpgKey.toLowerCase())) {
							pgpKeyRing = keyRing;
						}
					}
				}
				if (pgpKeyRing == null) {
					exceptionHandler.handleError("Unable to locate valid GPG key");
					sign = false;
				} else {
					return new String(Base64.getEncoder().encode(pgpKeyRing.getEncoded()), StandardCharsets.UTF_8);
				}
			} catch (FileNotFoundException | PGPException e) {
				exceptionHandler.handleError("Unable to locate valid GPG key", e);
				sign = false;
			}
		}
		return null;
	}

	private void generateManifest(File targetDir) throws IOException, ArtifactResolverException {
		ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

		// Copy all sources into the target directory
		Files.copy(source.toPath(), Paths.get(targetDir.getAbsolutePath(), source.getName()));
		for (AdditionalSource addSource : additionalSources) {
			if (addSource.getSource() == null) {
				getLog().debug("Resolving additional source from Maven: " + addSource.getArtifact().toString());
				DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
				artifactCoordinate.setGroupId(addSource.getArtifact().getGroupId());
				artifactCoordinate.setArtifactId(addSource.getArtifact().getArtifactId());
				artifactCoordinate.setVersion(addSource.getArtifact().getVersion());
				artifactCoordinate.setClassifier(addSource.getArtifact().getClassifier());
				artifactCoordinate.setExtension(addSource.getArtifact().getType());
				Artifact artifact = artifactResolver.resolveArtifact(buildingRequest, artifactCoordinate).getArtifact();
				addSource.setSource(artifact.getFile());
			}
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
			if (mimeTypes != null && mimeTypes.size() > 0) {
				bw.write("Exec=" + command + " %f\n");
			} else {
				bw.write("Exec=" + command + "\n");
			}
			bw.write("Terminal=false\n");
			bw.write("Categories=Development;IDE;\n");
			if (mimeTypes != null && mimeTypes.size() > 0) {
				String typeList = String.join(";", mimeTypes);
				bw.write("MimeType=" + typeList + ";\n");
			}
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
			bw.write("  <id>" + flatpakId + "</id>\n");
			bw.write("  <launchable type=\"desktop-id\">" + flatpakId + ".desktop</launchable>\n");
			bw.write("  <metadata_license>" + metadataLicense + "</metadata_license>\n");
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
		additionalSources
				.add(new AdditionalSource(appdataFile, new File("/app/share/metainfo", appdataFile.getName())));

		// Eclipse module
		Module.Builder eclipseModuleBuilder = Module.builder()
				.name("eclipse")
				.buildSystem("simple")
				.addbuildCommand("mv eclipse /app")
				.addbuildCommand("mkdir -p /app/bin")
				.addbuildCommand("ln -s /app/eclipse/" + command + " /app/bin");
		Source sourceArchive = Source.builder()
				.type("archive")
				.path(source.getName())
				.stripComponents(0)
				.build();
		eclipseModuleBuilder.addSource(sourceArchive);
		for (AdditionalSource addSource : additionalSources) {
			getLog().debug("Additional Source: " + addSource.getSource() + " -> " + addSource.getDestination());
			Source sourceFile = Source.builder()
					.type("file")
					.path(addSource.getSource().getName())
					.destFilename(addSource.getSource().getName())
					.build();
			eclipseModuleBuilder.addSource(sourceFile);
			eclipseModuleBuilder
					.addbuildCommand("install -Dm " + addSource.getPermissions() + " " + addSource.getSource()
							.getName() + " " + addSource.getDestination());
		}
		// Install icons from the branding plug-in
		if (brandingPlugin == null || brandingPlugin.isEmpty()) {
			brandingPlugin = id;
		}
		for (int px = 32; px < 512; px = px * 2) {
			String iconDir = "/app/share/icons/hicolor/" + px + "x" + px + "/apps";
			String icon = "/app/eclipse/plugins/" + brandingPlugin + "_*/" + command + px + ".png";
			eclipseModuleBuilder.addbuildCommand("mkdir -p " + iconDir);
			eclipseModuleBuilder.addbuildCommand(
					"if [ -f " + icon + " ] ; then cp -p " + icon + " " + iconDir + "/" + flatpakId + ".png ; fi");
		}

		// Generate the Flatpak application manifest
		Manifest.Builder manifestBuilder = Manifest.builder()
				.id(flatpakId)
				.branch(branch)
				.runtime(runtime)
				.runtimeVersion(runtimeVersion)
				.addModule(eclipseModuleBuilder.build());
		if (appendDefaultFinishArgs) {
			manifestBuilder.addFinishArg("--require-version=" + minFlatpakVersion);
			for (String fArg : Manifest.DEFAULT_FINISH_ARGS) {
				manifestBuilder.addFinishArg(fArg);
			}
		}
		if (finishArgs != null) {
			for (String fArg : finishArgs) {
				manifestBuilder.addFinishArg(fArg);
			}
		}

		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.writeValue(new File(targetDir, flatpakId + ".json"), manifestBuilder.build());
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
			builderArgs.add("--force-clean");
			builderArgs.add("--disable-cache");
			builderArgs.add("--disable-download");
			builderArgs.add("--disable-updates");
			builderArgs.add("--default-branch=" + branch);
			builderArgs.add("--repo=" + repository.getAbsolutePath());
			if (sign) {
				builderArgs.add("--gpg-sign=" + gpgKey);
				builderArgs.add("--gpg-homedir=" + gpgHome);
			}
			builderArgs.add(new File(targetDir, "build").getAbsolutePath());
			builderArgs.add(new File(targetDir, flatpakId + ".json").getAbsolutePath());
			executeProcess(exceptionHandler, builderArgs, targetDir);

		} else {
			// Generate remotely
			Builder requestBuilder = HttpRequest.on(URI.create(serviceUrl));
			requestBuilder.withParam("flatpakId", flatpakId);
			requestBuilder.withParam("branch", branch);
			requestBuilder.withParam("manifest", new File(targetDir, flatpakId + ".json").toPath());
			requestBuilder.withParam("source", source.toPath());
			requestBuilder.withParam("additionalSources", Integer.toString(additionalSources.size()));
			for (int i = 0; i < additionalSources.size(); i++) {
				AdditionalSource addSource = additionalSources.get(i);
				requestBuilder.withParam("additionalSource" + i,
						Paths.get(targetDir.getAbsolutePath(), addSource.getSource().getName()));
			}
			requestBuilder.withParam("sign", Boolean.toString(sign));
			executeProcessOnRemoteServer(requestBuilder.build());

			// The "build-import-bundle" command does not generate a repo if one does not
			// yet exist, so we need to pre-initialise it if necessary
			if (!Files.isDirectory(repository.toPath())) {
				List<String> ostreeInitArgs = new ArrayList<>();
				ostreeInitArgs.add("ostree");
				ostreeInitArgs.add("init");
				ostreeInitArgs.add("--mode=archive");
				ostreeInitArgs.add("--repo=" + repository.getAbsolutePath());
				executeProcess(exceptionHandler, ostreeInitArgs, targetDir);
			}

			// Import remotely generated Flatpak bundle into the repository
			List<String> importArgs = new ArrayList<>();
			importArgs.add("flatpak");
			importArgs.add("build-import-bundle");
			importArgs.add("--no-update-summary");
			if (sign) {
				importArgs.add("--gpg-sign=" + gpgKey);
				importArgs.add("--gpg-homedir=" + gpgHome);
			}
			importArgs.add(repository.getAbsolutePath());
			importArgs.add(new File(targetDir, flatpakId + ".flatpak").getAbsolutePath());
			executeProcess(exceptionHandler, importArgs, targetDir);
		}

		if (branchAlias != null && !branchAlias.isEmpty()) {
			// Adds a additional commit pointing to the build we just imported into the repo
			// using the given branch alias name
			List<String> aliasArgs = new ArrayList<>();
			aliasArgs.add("flatpak");
			aliasArgs.add("build-commit-from");
			aliasArgs.add("--no-update-summary");
			if (sign) {
				aliasArgs.add("--gpg-sign=" + gpgKey);
				aliasArgs.add("--gpg-homedir=" + gpgHome);
			}
			aliasArgs.add("--src-ref=app/" + flatpakId + "/x86_64/" + branch);
			aliasArgs.add(repository.getAbsolutePath());
			aliasArgs.add("app/" + flatpakId + "/x86_64/" + branchAlias);
			executeProcess(exceptionHandler, aliasArgs, targetDir);
		}

		// Update repository metadata (regenerates the ostree summary file and deltas)
		List<String> deltaArgs = new ArrayList<>();
		deltaArgs.add("flatpak");
		deltaArgs.add("build-update-repo");
		deltaArgs.add("--generate-static-deltas");
		deltaArgs.add("--prune");
		if (sign) {
			deltaArgs.add("--gpg-sign=" + gpgKey);
			deltaArgs.add("--gpg-homedir=" + gpgHome);
		}
		deltaArgs.add(repository.getAbsolutePath());
		executeProcess(exceptionHandler, deltaArgs, targetDir);
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

	private void executeProcessOnRemoteServer(HttpRequest request) throws IOException {
		getLog().debug("Executing remotely: " + request.toString());
		final HttpClient httpClient = RetryHttpClient.retryRequestOn(ApacheHttpClient.create(new MavenLogger(getLog())))
				.maxRetries(3).waitBeforeRetry(10, TimeUnit.SECONDS).log(new MavenLogger(getLog())).build();
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
						// Flatpak application bundle is sent back to us in the reply
						Path bundlePath = Paths.get(project.getBuild().getDirectory(), "flatpak",
								flatpakId + ".flatpak");
						result.copyContent(bundlePath, StandardCopyOption.REPLACE_EXISTING);
						if (Files.size(bundlePath) == 0) {
							throw new IOException("Size of the returned Flatpak repo is 0");
						}
					}
				});
	}
}
