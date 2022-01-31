/*******************************************************************************
 * Copyright (c) 2015, 2019 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.common.security;

import java.security.Signature;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * A enumeration of {@link Signature} algorithm available in Java 8 as specified
 * in the document "<a href=
 * "http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Signature">
 * standard names for algorithms</a>".
 */
public enum SignatureAlgorithm {

	DEFAULT("JVM-Default-Signature-Algorithm"),

	NONEwithRSA("NONEwithRSA"), MD2withRSA("MD2withRSA"), MD5withRSA("MD5withRSA"),

	SHA1withRSA("SHA1withRSA"), SHA224withRSA("SHA224withRSA"), SHA256withRSA("SHA256withRSA"),
	SHA384withRSA("SHA384withRSA"), SHA512withRSA("SHA512withRSA"),

	SHA1withDSA("SHA1withDSA"), SHA224withDSA("SHA224withDSA"), SHA256withDSA("SHA256withDSA"),

	NONEwithECDSA("NONEwithECDSA"), SHA1withECDSA("SHA1withECDSA"), SHA224withECDSA("SHA224withECDSA"),
	SHA256withECDSA("SHA256withECDSA"), SHA384withECDSA("SHA384withECDSA"), SHA512withECDSA("SHA512withECDSA");

	private final String standardName;
	private final Set<String> aliases;

	private SignatureAlgorithm(String standardName, String... alias) {
		this.standardName = standardName;
		this.aliases = ImmutableSet.copyOf(alias);
	}

	/**
	 * Returns the standard name of the {@link Signature} algorithm as specified in
	 * the document "<a href=
	 * "http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Signature">
	 * standard names for algorithms</a>"
	 *
	 * @return the standard name of the algorithm.
	 */
	public String standardName() {
		return this.standardName;
	}

	public static SignatureAlgorithm fromStandardName(final String signatureAlgorithmName) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(signatureAlgorithmName));
		Optional<SignatureAlgorithm> ret = Iterables.tryFind(EnumSet.allOf(SignatureAlgorithm.class),
				(@Nonnull SignatureAlgorithm d) -> signatureAlgorithmName.equals(d.standardName)
						|| d.aliases.contains(signatureAlgorithmName));
		if (!ret.isPresent()) {
			throw new IllegalArgumentException("Unknow signature algorithm '" + signatureAlgorithmName + "'");
		} else {
			return ret.get();
		}
	}
}
