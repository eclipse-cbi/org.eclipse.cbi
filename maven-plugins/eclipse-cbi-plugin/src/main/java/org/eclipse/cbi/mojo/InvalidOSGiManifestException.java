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

/**
 * Exception signaling an otherwise valid MANIFEST does not have valid mandatory
 * OSGi headers
 * Bundle-SymbolicName or Bundle-Version.
 */
public class InvalidOSGiManifestException extends OsgiManifestParserException {

  private static final long serialVersionUID = -2887273472549676124L;

  public InvalidOSGiManifestException(String manifestLocation, String message) {
    super(manifestLocation, message);
  }

  public InvalidOSGiManifestException(String manifestLocation, Throwable cause) {
    super(manifestLocation, cause);
  }

}
