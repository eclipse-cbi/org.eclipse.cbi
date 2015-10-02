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
package org.eclipse.cbi.maven.common.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.maven.common.Logger;

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;

@AutoValue
public abstract class RetryHttpClient implements HttpClient {

	abstract int maxRetries();
	abstract long retryInterval();
	abstract TimeUnit retryIntervalUnit();
	abstract HttpClient delegate();
	abstract Logger log();

	RetryHttpClient() {
	}
	
	@Override
	public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
		boolean sucess = false;
		Exception lastThrownException = null;

		for (int attemptCount = 0; !sucess && attemptCount <= maxRetries(); attemptCount++) {
			if (!sucess && attemptCount > 0) {
				try {
					if (lastThrownException != null) {
						log().warn("An exception has been thrown, but the request will be retried", lastThrownException);
						lastThrownException = null;
					}
					retryIntervalUnit().sleep(retryInterval());
				} catch (InterruptedException e) {
					log().warn("Thread '" + Thread.currentThread().getName() + "' has been interrupted", e);
					Thread.currentThread().interrupt();
					break;
				}
			}

			try {
				sucess = delegate().send(request, completionListener);
			} catch (Exception e) {
				lastThrownException = e;
			}
		}

		if (lastThrownException != null) {
			Throwables.propagateIfInstanceOf(lastThrownException, IOException.class);
			throw Throwables.propagate(lastThrownException);
		}

		return sucess;
	}
	
	public static Builder retryRequestOn(HttpClient client) {
		return new AutoValue_RetryHttpClient.Builder().delegate(client);
	}
	
	@AutoValue.Builder
	public static abstract class Builder {
		
		public abstract Builder maxRetries(int maxRetries);
		abstract Builder retryInterval(long retryInterval);
		abstract Builder retryIntervalUnit(TimeUnit retryIntervalUnit);
		
		public Builder waitBeforeRetry(long retryInterval, TimeUnit retryIntervalUnit) {
			return retryInterval(retryInterval).retryIntervalUnit(retryIntervalUnit);
		}
		
		abstract Builder delegate(HttpClient httpClient);
		public abstract Builder log(Logger log);
		
		abstract RetryHttpClient autoBuild();
		
		public HttpClient build() {
			RetryHttpClient ret = autoBuild();
			checkPositive(ret.maxRetries(), "'maxRetries' must be positive");
			checkPositive(ret.retryInterval(), "'retryInterval' must be positive");
			return ret;
		}
		
		private static long checkPositive(long n, String msg) {
			if (n < 0) {
				throw new IllegalArgumentException(msg);
			} else {
				return n;
			}
		}
	}

}
