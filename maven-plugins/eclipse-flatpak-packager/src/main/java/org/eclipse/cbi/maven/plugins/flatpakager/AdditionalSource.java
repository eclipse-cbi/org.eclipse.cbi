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
package org.eclipse.cbi.maven.plugins.flatpakager;

import java.io.File;

public class AdditionalSource {
	private File source;
	private File destination;

	public AdditionalSource() {
		// Default constructor is needed by maven
	}

	public AdditionalSource(File source, File destination) {
		setSource(source);
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

	public File getDestination() {
		return destination;
	}

	public void setDestination(File destination) {
		this.destination = destination;
	}
}
