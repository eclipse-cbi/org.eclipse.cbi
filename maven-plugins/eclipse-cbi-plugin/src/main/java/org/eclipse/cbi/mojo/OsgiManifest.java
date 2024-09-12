/*******************************************************************************
 * Copyright (c) 2011 SAP and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SAP - initial API and implementation
 *******************************************************************************/
package org.eclipse.cbi.mojo;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.osgi.container.builders.OSGiManifestBuilderFactory;
import org.eclipse.osgi.framework.util.CaseInsensitiveDictionaryMap;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * Convenience wrapper around {@link Headers} and {@link ManifestElement} which
 * adds typed getters
 * and value caching for commonly used headers. This is a read-only API.
 * 
 */
public class OsgiManifest {

  private final CaseInsensitiveDictionaryMap<String, String> headers;
  private final String bundleSymbolicName;
  private final String bundleVersion;
  private String location;

  OsgiManifest(InputStream stream, String location) throws OsgiManifestParserException {

    this.location = location;
    try {
      this.headers = new CaseInsensitiveDictionaryMap<>();
      ManifestElement.parseBundleManifest(stream, headers);
//      // this will do more strict validation of headers on OSGi semantical level
      this.bundleSymbolicName = OSGiManifestBuilderFactory.createBuilder(headers).getSymbolicName();
    } catch (IOException | BundleException e) {
      throw new OsgiManifestParserException(location, e);
    }
    if (this.bundleSymbolicName == null) {
      throw new InvalidOSGiManifestException(location, "Bundle-SymbolicName is missing");
    }

    this.bundleVersion = parseBundleVersion();
  }

  private String parseBundleVersion() {

    ManifestElement[] elements = parseHeader(Constants.BUNDLE_VERSION);
    if (elements != null) {
      for (ManifestElement element : elements) {
        String versionString = element.getValue();
        try {
          return Version.parseVersion(versionString).toString();
        } catch (NumberFormatException e) {
          throw new InvalidOSGiManifestException(location,
              "Bundle-Version '" + versionString + "' is invalid");
        } catch (IllegalArgumentException e) {
          throw new InvalidOSGiManifestException(location, e);
        }
      }
    }
    return Version.emptyVersion.toString();
  }

  public String getValue(String key) {
    return headers.get(key);
  }

//
  public String getBundleSymbolicName() {
    return bundleSymbolicName;
  }

//
  public String getBundleVersion() {
    return bundleVersion;
  }

  private ManifestElement[] parseHeader(String key) {
    String value = headers.get(key);
    if (value == null) {
      return null;
    }
    try {
      return ManifestElement.parseHeader(key, value);
    } catch (BundleException e) {
      throw new OsgiManifestParserException(location, e);
    }
  }

}
