/*******************************************************************************
 * Copyright (c) 2013, 2018 Red Hat, Inc. and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Krzysztof Daniel (Red Hat)  - initial API and implementation
 *   Jan Sievers (SAP)           - 381057 fix GenerateAPIBuildXMLMojo
 *******************************************************************************/

package org.eclipse.cbi.mojo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.tycho.ArtifactKey;
import org.eclipse.tycho.core.BundleProject;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.osgitools.OsgiBundleProject;
import org.eclipse.tycho.core.osgitools.project.BuildOutputJar;
import org.eclipse.tycho.core.osgitools.project.EclipsePluginProject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Mojo( name = "generate-api-build-xml", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe=true)
public class GenerateAPIBuildXMLMojo extends AbstractMojo {
	
	private static final String API_BUILD_XML_FILE = ".apibuild.xml";
	private static final String API_NATURE = "org.eclipse.pde.api.tools.apiAnalysisNature";
	
	@Parameter(defaultValue="${project}", required=true, readonly=true)
    protected MavenProject project;

	@Component(role=TychoProject.class)
    private Map<String, TychoProject> projectTypes;
	
	@Override
	public void execute() throws MojoExecutionException {
		File dotProject = new File(project.getBasedir(), ".project");
		if (!isRelevantPackaging(project.getPackaging()) || !dotProject.exists()) {
			// no .project
			project.getProperties().setProperty("eclipserun.skip", "true");
			return;
		}
		if (dotProjectContainsApiNature(dotProject)) {
			generateBuildXML();
		} else {
			project.getProperties().setProperty("eclipserun.skip", "true");
		}
	}

	private static boolean isRelevantPackaging(String packaging) {
		return "eclipse-plugin".equals(packaging)|| "eclipse-test-plugin".equals(packaging);
	}
	
	private static boolean dotProjectContainsApiNature(File f){
		try{
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(f);
			doc.getDocumentElement().normalize();
			NodeList natures = doc.getElementsByTagName("nature");
			for (int i = 0; i < natures.getLength(); i++) {
				 
				   Node nature = natures.item(i);
				   String sNature = nature.getTextContent();
				   if( sNature != null){
					   if(API_NATURE.equals(sNature.trim())){
						   return true;
					   }
				   }
			}
		} catch (Exception e){
			e.printStackTrace();
			return false;
		}
		return false;
	}
	
	private void generateBuildXML() throws MojoExecutionException{
		System.out.println("Generating target/.apibuild.xml");
		File targetDir = new File(project.getBuild().getDirectory());
		if (!targetDir.isDirectory()) {
			targetDir.mkdirs();
		}
		File dotApiBuildXML = new File(targetDir, API_BUILD_XML_FILE);
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dotApiBuildXML), StandardCharsets.UTF_8))) {
			bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			bw.write("<project name=\"apigen\" default=\"apigen\">\n");
			bw.write("  <target name=\"apigen\">\n");
			bw.write("  	<apitooling.apigeneration    \n");
			bw.write("      	projectname=\"" + calculateName() + "\"\n");
			bw.write("      	project=\"" + project.getBasedir() + "\"\n");
			bw.write("      	binary=\"" + getOutputFoldersAsPath() + "\"\n");
			bw.write("      	target=\"" + targetDir + "\"\n");
			if (getLog().isDebugEnabled()) {
				bw.write("      	debug=\"true\"\n");
			}
			bw.write("      \n");
			bw.write("      />\n");
			bw.write("  </target>\n");
			bw.write("</project>\n");
			bw.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String getOutputFoldersAsPath() throws MojoExecutionException {
		StringBuilder path = new StringBuilder();
		List<BuildOutputJar> outputJars = getEclipsePluginProject()
				.getOutputJars();
		for (int i = 0; i < outputJars.size(); i++) {
			if (i > 0) {
				path.append(File.pathSeparator);
			}
			path.append(outputJars.get(i).getOutputDirectory().getAbsolutePath());
		}
		return path.toString();
	}

    private EclipsePluginProject getEclipsePluginProject() throws MojoExecutionException {
        return ((OsgiBundleProject) getBundleProject()).getEclipsePluginProject(DefaultReactorProject.adapt(project));
    }

    private BundleProject getBundleProject() throws MojoExecutionException {
        TychoProject projectType = projectTypes.get(project.getPackaging());
        if (!(projectType instanceof BundleProject)) {
            throw new MojoExecutionException("Not a bundle project " + project.toString());
        }
        return (BundleProject) projectType;
    }

	private String calculateName() {
		TychoProject projectType = projectTypes.get(project.getPackaging());
		ArtifactKey artifactKey = projectType
				.getArtifactKey(DefaultReactorProject.adapt(project));
		String symbolicName = artifactKey.getId();
        // see org.eclipse.tycho.buildversion.BuildQualifierMojo
		String version = project.getProperties().getProperty(
				"qualifiedVersion");
		return symbolicName + "_" + version;
	}
}
