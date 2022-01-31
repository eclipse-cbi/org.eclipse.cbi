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

import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * A enumeration of {@link MessageDigest} algorithm available in Java 8 as
 * specified in the document "<a href=
 * "http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#MessageDigest">
 * standard names for algorithms</a>".
 */
public enum MessageDigestAlgorithm {
	DEFAULT("JVM-Default-Message-Digest-Algorithm"), MD2("MD2"), MD5("MD5"), SHA_1("SHA-1"),
	/**
	 * For backward compatibility with frameworks only accepting "SHA1-Digest" from
	 * Java 6 and before and reject "SHA-1-Digest". See
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=483881 for details.
	 *
	 * @deprecated You should really consider using
	 *             {@link MessageDigestAlgorithm#SHA_1} instead except if you really
	 *             need to be compatible with some old frameworks (e.g., Eclipse
	 *             Equinox 3.7 / Indigo)
	 */
	SHA1("SHA1"), SHA_224("SHA-224"), SHA_256("SHA-256"), SHA_384("SHA-384"), SHA_512("SHA-512");

	private final String standardName;
	private final Set<String> aliases;

	private MessageDigestAlgorithm(String standardName, String... alias) {
		this.standardName = standardName;
		this.aliases = ImmutableSet.copyOf(alias);
	}

	/**
	 * Returns the standard name of the {@link MessageDigest} algorithm as specified
	 * in the document "<a href=
	 * "http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#MessageDigest">
	 * standard names for algorithms</a>"
	 *
	 * @return the standard name of the algorithm.
	 */
	public String standardName() {
		return this.standardName;
	}

	public static MessageDigestAlgorithm fromStandardName(final String digestAlgorithmName) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(digestAlgorithmName));
		Optional<MessageDigestAlgorithm> ret = Iterables.tryFind(EnumSet.allOf(MessageDigestAlgorithm.class),
				(@Nonnull MessageDigestAlgorithm d) -> digestAlgorithmName.equals(d.standardName)
						|| d.aliases.contains(digestAlgorithmName));
		if (!ret.isPresent()) {
			throw new IllegalArgumentException("Unknow digest algorithm '" + digestAlgorithmName + "'");
		} else {
			return ret.get();
		}
	}
}
