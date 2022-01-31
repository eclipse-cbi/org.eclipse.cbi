/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
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
package org.eclipse.cbi.webservice.util.function;

import java.util.function.Predicate;

import static com.google.common.base.Throwables.throwIfUnchecked;

/**
 * Functional interface similar to {@link Predicate} but its
 * {@link #test(Object)} method can throws exception.
 *
 * @param <T>
 *            the type of the input to the predicate
 */
@FunctionalInterface
public interface UnsafePredicate<T> {

	/**
     * Evaluates this predicate on the given argument.
     *
     * @param t the input argument
     * @return {@code true} if the input argument matches the predicate,
     * otherwise {@code false}
	 * @throws Exception if the given test throws exception. 
	 */
	boolean test(T t) throws Exception;

	/**
	 * Convert an {@link UnsafePredicate} to a safe {@link Predicate}, i.e. that
	 * does not throw exception. The safe {@link Predicate} will rethrow any
	 * exception as a {@link WrappedException} (or as is if it is an
	 * {@link Error} or a {@link RuntimeException}.
	 * 
	 * @param <T>
	 *            the type of the input to the predicate
	 *
	 * @param p
	 *            the predicate to be decorated.
	 * @return a predicate that does not throw exception.
	 */
	static <T> Predicate<T> safePredicate(UnsafePredicate<T> p) {
		return s -> {
			try {
				return p.test(s);
			} catch (Exception e) {
				throwIfUnchecked(e);
				throw new WrappedException(e);
			}
		};
	}
}