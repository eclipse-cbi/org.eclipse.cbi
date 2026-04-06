package org.eclipse.cbi.maven.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.maven.common.test.util.HttpClients;
import org.eclipse.cbi.maven.common.test.util.NullLog;
import org.eclipse.cbi.maven.http.CompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpRequest.Config;
import org.eclipse.cbi.maven.http.HttpResult;
import org.eclipse.cbi.maven.http.RetryHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RetryHttpClientTest {

	@Test
	public void testRetryNegativeTimes() {
		assertThrows(IllegalArgumentException.class, () -> RetryHttpClient.retryRequestOn(HttpClients.DUMMY).maxRetries(-1).waitBeforeRetry(1, TimeUnit.SECONDS).log(new NullLog()).build());
	}

	@Test
	public void testRetryNegativeInterval() {
		assertThrows(IllegalArgumentException.class, () -> RetryHttpClient.retryRequestOn(HttpClients.DUMMY).maxRetries(5).waitBeforeRetry(-1, TimeUnit.SECONDS).log(new NullLog()).build());
	}

	@Test
	public void testRetryNullUnit() {
		assertThrows(NullPointerException.class, () -> RetryHttpClient.retryRequestOn(HttpClients.DUMMY).maxRetries(3).waitBeforeRetry(1, null).log(new NullLog()).build());
	}
	
	@Test
	public void testRetryOnServerError() throws Exception {
		FailureCountingHttpClient countingClient = new FailureCountingHttpClient(true);
		HttpClient client = RetryHttpClient.retryRequestOn(countingClient).maxRetries(3).waitBeforeRetry(1, TimeUnit.MILLISECONDS).log(new NullLog()).build();
		
		try {
			client.send(HttpRequest.on(URI.create("locahost")).build(), new CompletionListener() {
				@Override
				public void onError(HttpResult error) throws IOException {
					Assertions.fail();
				}
				
				@Override
				public void onSuccess(HttpResult result) throws IOException {
					Assertions.fail();
				}
			});
		} catch (IOException e) {
			assertEquals(4, countingClient.count());
			return;
		}
		Assertions.fail();
	}
	
	@Test
	public void testRetryOnServerFailure() throws Exception {
		FailureCountingHttpClient countingClient = new FailureCountingHttpClient(false);
		HttpClient client = RetryHttpClient.retryRequestOn(countingClient).maxRetries(3).waitBeforeRetry(1, TimeUnit.MILLISECONDS).log(new NullLog()).build();
		
		client.send(HttpRequest.on(URI.create("locahost")).build(), new CompletionListener() {
			@Override
			public void onError(HttpResult error) throws IOException {
				Assertions.fail();
			}
			
			@Override
			public void onSuccess(HttpResult result) throws IOException {
				Assertions.fail();
			}
		});
		assertEquals(4, countingClient.count());
	}

	
	private static class FailureCountingHttpClient implements HttpClient {

		private final boolean throwEx;
		private int count = 0;

		FailureCountingHttpClient(boolean throwEx) {
			this.throwEx = throwEx;
		}
		
		@Override
		public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
			return send(request, Config.defaultConfig(), completionListener);
		}

		@Override
		public boolean send(HttpRequest request, Config config, CompletionListener completionListener)
				throws IOException {
			count++;
			if (throwEx) {
				throw new IOException();
			} else {
				return false;
			}
		}
		
		int count() {
			return count;
		}
	}
}
