package org.eclipse.cbi.maven.common.test.util;

import java.io.IOException;

import org.eclipse.cbi.maven.common.http.CompletionListener;
import org.eclipse.cbi.maven.common.http.HttpClient;
import org.eclipse.cbi.maven.common.http.HttpRequest;

public enum HttpClients implements HttpClient {

	DUMMY {
		@Override
		public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
			return true;
		}
	}, 
	ERROR {
		@Override
		public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
			throw new IOException("Something bad happened when sending the request to the server!");
		}
	}, 
	FAILING {
		@Override
		public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
			return false;
		}
	}
}
