package org.eclipse.cbi.common.test.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.common.http.HttpPostFileSender;

public class ErrorSigner implements HttpPostFileSender {
	@Override
	public boolean post(Path path, String partName, int retryLimit, int retryTimer, TimeUnit timeUnit) throws IOException {
		throw new IOException("Something bad happened in the signer!");
	}

	@Override
	public boolean post(Path path, String partName) throws IOException {
		return post(path, partName, 0, 0, null);
	}
}