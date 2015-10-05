/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.dmgpackaging;

import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.PropertiesReader;

public class DMGPackagerProperties {

	private static final String DEFAULT_TIMEOUT = Long.toString(TimeUnit.MINUTES.toSeconds(3));
	private static final String TIMEOUT = "dmgpackager.timeout";

	private final PropertiesReader propertiesReader;
	
	public DMGPackagerProperties(PropertiesReader propertiesReader) {
		this.propertiesReader = propertiesReader;
	}
	
	public long getTimeout() {
		String timeout = propertiesReader.getString(TIMEOUT, DEFAULT_TIMEOUT);
		try {
			return Long.parseLong(timeout);
		} catch (NumberFormatException e) {
			throw new IllegalStateException("'" + TIMEOUT + "' '" + timeout + "' must be a valid long integer", e);
		}
	}
}
