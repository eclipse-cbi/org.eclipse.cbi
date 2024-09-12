/*******************************************************************************
 * Copyright (c) 2012 Eclipse Foundation and others
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

abstract class AbstractPluginScannerMojo extends AbstractMojo {
  /**
   * igorf: as of 2012-01-05, generated repository location is hardcoded to
   * target/repository in tycho
   **/
  @Parameter(defaultValue = "${project.build.directory}/repository")
  protected File repository;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      Properties properties = new Properties();

      File[] plugins = new File(repository, "plugins").listFiles();

      if (plugins != null) {
        Map<File, OsgiManifest> manifests = new HashMap<>();
        for (File plugin : plugins) {
          String fileName = plugin.getName();
          if (fileName.endsWith(".pack.gz") || fileName.endsWith(".asc")) {
            continue;
          }
          try {
            OsgiManifest manifest = DefaultBundleReader.loadManifest(plugin);
            manifests.put(plugin, manifest);
          } catch (OsgiManifestParserException e) {
            getLog().error(e);
          }
        }

        processPlugins(properties, manifests);
      }

      try (OutputStream os = new BufferedOutputStream(new FileOutputStream(getDestination()))) {
        properties.store(os, null);
      }
    } catch (Exception e) {
      throw new MojoExecutionException("Could not write plugin versions", e);
    }
  }

  protected abstract void processPlugins(Properties properties, Map<File, OsgiManifest> plugins) throws Exception;

  protected abstract File getDestination();

}
