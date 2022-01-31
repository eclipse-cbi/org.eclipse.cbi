/*******************************************************************************
 * Copyright (c) 2012, 2018 Eclipse Foundation and others 
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *   Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cbi.mojo;

import java.io.File;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.tycho.core.osgitools.OsgiManifest;

@Mojo(name = "plugin-versions", threadSafe = true)
public class PluginVersionsMojo extends AbstractPluginScannerMojo {
	@Parameter(defaultValue = "${project.build.directory}/plugin-versions.properties")
	protected File destination;

	@Override
	protected void processPlugins(Properties properties, Map<File, OsgiManifest> plugins) {
		for (OsgiManifest manifest : plugins.values()) {
			properties.put(manifest.getBundleSymbolicName(), manifest.getBundleVersion());
		}
	}

	@Override
	protected File getDestination() {
		return destination;
	}
}
