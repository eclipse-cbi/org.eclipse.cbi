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
package org.eclipse.cbi.webservice.signing.jar;

import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * A enumeration of {@link MessageDigest} algorithm available in Java 8 as
 * specified in the document "<a href=
 * "http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#MessageDigest">
 * standard names for algorithms</a>".
 */
public enum MessageDigestAlgorithm {
	DEFAULT("JVM-Default-Message-Digest-Algorithm"), MD2("MD2"), MD5("MD5"), SHA_1("SHA-1"), SHA_224(
			"SHA-224"), SHA_256("SHA-256"), SHA_384("SHA-384"), SHA_512("SHA-512");

	private final String standardName;

	private MessageDigestAlgorithm(String standardName) {
		this.standardName = standardName;
	}

	/**
	 * Returns the standard name of the {@link MessageDigest} algorithm as
	 * specified in the document "<a href=
	 * "http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#MessageDigest">
	 * standard names for algorithms</a>"
	 * 
	 * @return the standard name of the algorithm.
	 */
	public String standardName() {
		return this.standardName;
	}

	public static MessageDigestAlgorithm fromStandardName(String digestAlg) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(digestAlg));
		Optional<MessageDigestAlgorithm> ret = EnumSet.allOf(MessageDigestAlgorithm.class).stream().filter(d -> digestAlg.equals(d.standardName))
				.findFirst();
		if (!ret.isPresent()) {
			throw new IllegalArgumentException("Unknow digest algorithm '" + digestAlg + "'");
		} else {
			return ret.get();
		}
	}
}
