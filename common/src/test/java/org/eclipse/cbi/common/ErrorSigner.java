package org.eclipse.cbi.common;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.common.signing.Signer;

public class ErrorSigner implements Signer {
	@Override
	public boolean sign(Path path, int retryLimit, int retryTimer,
			TimeUnit timeUnit) throws IOException {
		throw new IOException("Something bad happened in the signer!");
	}

	@Override
	public boolean sign(Path path) throws IOException {
		return sign(path, 0, 0, null);
	}
}