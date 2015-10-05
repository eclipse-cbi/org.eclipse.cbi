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
package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.IOException;
import java.nio.file.Path;

public abstract class FilteredJarSigner implements JarSigner {

	abstract Filter filter();

	public interface Filter {
		boolean shouldBeSigned(Path jar) throws IOException;
	}
	
	public enum Filters implements Filter {
		ALWAYS_SIGN {
			@Override
			public boolean shouldBeSigned(Path jar) throws IOException {
				return true;
			}
		},
		
		NEVER_SIGN {
			@Override
			public boolean shouldBeSigned(Path jar) throws IOException {
				return false;
			}
		}
		
	}
	
	@Override
	public final int sign(Path jar, Options options) throws IOException {
		if (filter().shouldBeSigned(jar)) {
			return doSignJar(jar, options);
		}
		return 0;
	}

	abstract int doSignJar(Path jarfile, Options options) throws IOException;
}
