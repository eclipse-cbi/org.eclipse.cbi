package org.eclipse.cbi.common;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.common.signing.Signer;

public class NotSigningSigner implements Signer {
	@Override
	public boolean sign(Path path, int retryLimit, int retryTimer,
			TimeUnit timeUnit) {
		return false;
	}

	@Override
	public boolean sign(Path path) {
		return sign(path, 0, 0, null);
	}
}