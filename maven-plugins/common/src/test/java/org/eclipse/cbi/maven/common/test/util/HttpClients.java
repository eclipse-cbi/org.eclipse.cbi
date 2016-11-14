package org.eclipse.cbi.maven.common.test.util;

import java.io.IOException;

import org.eclipse.cbi.maven.http.CompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpRequest.Config;

public enum HttpClients implements HttpClient {

	DUMMY {
		@Override
		public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
			return send(request, Config.defaultConfig(), completionListener);
		}

		@Override
		public boolean send(HttpRequest request, Config config, CompletionListener completionListener)
				throws IOException {
			return true;
		}
	}, 
	ERROR {
		@Override
		public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
			return send(request, Config.defaultConfig(), completionListener);
		}

		@Override
		public boolean send(HttpRequest request, Config config, CompletionListener completionListener)
				throws IOException {
			throw new IOException("Something bad happened when sending the request to the server!");
		}
	}, 
	FAILING {
		@Override
		public boolean send(HttpRequest request, CompletionListener completionListener) throws IOException {
			return send(request, Config.defaultConfig(), completionListener);
		}

		@Override
		public boolean send(HttpRequest request, Config config, CompletionListener completionListener)
				throws IOException {
			return false;
		}
	}
}
