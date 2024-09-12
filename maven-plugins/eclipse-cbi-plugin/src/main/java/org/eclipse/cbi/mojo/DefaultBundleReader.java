/*******************************************************************************
 * Copyright (c) 2008, 2022 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Christoph Läubrich - Issue #663 - Access to the tycho .cache directory is not properly synchronized 
 *******************************************************************************/
package org.eclipse.cbi.mojo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class DefaultBundleReader {

  public static OsgiManifest loadManifest(File bundleLocation) {
    try {
      if (bundleLocation.isDirectory()) {
        return loadManifestFromDirectory(bundleLocation);
      } else if (bundleLocation.isFile()) {
        return loadManifestFromFile(bundleLocation);
      } else {
        // file does not exist
        throw new OsgiManifestParserException(bundleLocation.getAbsolutePath(), "Manifest file not found");
      }
    } catch (IOException e) {
      throw new OsgiManifestParserException(bundleLocation.getAbsolutePath(), e);
    }
  }

  private static OsgiManifest loadManifestFromFile(File bundleLocation) throws IOException {
    if (!bundleLocation.getName().toLowerCase().endsWith(".jar")) {
      // file but not a jar, assume it is MANIFEST.MF
      return loadManifestFile(bundleLocation);
    }
    try ( // it is a jar, let's see if it has OSGi bundle manifest
        ZipFile jar = new ZipFile(bundleLocation, ZipFile.OPEN_READ)) {
      ZipEntry manifestEntry = jar.getEntry(JarFile.MANIFEST_NAME);
      if (manifestEntry != null) {
        InputStream stream = jar.getInputStream(manifestEntry);
        return new OsgiManifest(stream, bundleLocation.getAbsolutePath() + "!/" + JarFile.MANIFEST_NAME);
      }
    }
    throw new OsgiManifestParserException(bundleLocation.getAbsolutePath(),
        "Manifest file not found in JAR archive");
  }

  private static OsgiManifest loadManifestFromDirectory(File directory) throws IOException {
    File manifestFile = new File(directory, JarFile.MANIFEST_NAME);
    if (!manifestFile.isFile()) {
      throw new OsgiManifestParserException(manifestFile.getAbsolutePath(), "Manifest file not found");
    }
    return loadManifestFile(manifestFile);
  }

  private static OsgiManifest loadManifestFile(File manifestFile) throws IOException, OsgiManifestParserException {
    return new OsgiManifest(new FileInputStream(manifestFile), manifestFile.getAbsolutePath());
  }

}
