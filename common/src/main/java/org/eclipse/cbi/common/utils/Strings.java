/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.common.utils;

import java.util.Iterator;

public class Strings {

	public static String join(String on, Iterable<?> iterable) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<?> it = iterable.iterator(); it.hasNext();) {
			String str = it.next().toString();
			sb.append("'" + str + "'");
			if (it.hasNext()) {
				sb.append(on);
			}
		}
		return sb.toString();
	}
}
