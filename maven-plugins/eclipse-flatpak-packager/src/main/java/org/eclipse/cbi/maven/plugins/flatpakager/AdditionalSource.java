/*******************************************************************************
 * Copyright (c) 2018, 2019 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.flatpakager;

import java.io.File;

import org.apache.maven.model.Dependency;

public class AdditionalSource {
	private File source;
	private Dependency artifact;
	private File destination;
	private String permissions = "644";

	public AdditionalSource() {
		// Default constructor is needed by maven
	}

	public AdditionalSource(File source, File destination, String permissions) {
		this(source, destination);
		setPermissions(permissions);
	}

	public AdditionalSource(Dependency artifact, File destination, String permissions) {
		this(artifact, destination);
		setPermissions(permissions);
	}

	public AdditionalSource(File source, File destination) {
		this(destination);
		setSource(source);
	}

	public AdditionalSource(Dependency artifact, File destination) {
		this(destination);
		setArtifact(artifact);
	}

	private AdditionalSource(File destination) {
		if (destination == null) {
			setDestination(new File(source.getName()));
		} else {
			setDestination(destination);
		}
	}

	public File getSource() {
		return source;
	}

	public void setSource(File source) {
		this.source = source;
	}

	public Dependency getArtifact() {
		return artifact;
	}

	public void setArtifact(Dependency artifact) {
		this.artifact = artifact;
	}

	public File getDestination() {
		return destination;
	}

	public void setDestination(File destination) {
		this.destination = destination;
	}

	public String getPermissions() {
		return permissions;
	}

	public void setPermissions(String permissions) {
		if (permissions != null && !permissions.isEmpty()) {
			this.permissions = permissions;
		}
	}
}
