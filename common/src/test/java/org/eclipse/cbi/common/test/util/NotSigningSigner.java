package org.eclipse.cbi.common.test.util;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.common.http.HttpPostFileSender;

public class NotSigningSigner implements HttpPostFileSender {
	@Override
	public boolean post(Path path, String partName, int retryLimit, int retryTimer, TimeUnit timeUnit) {
		return false;
	}

	@Override
	public boolean post(Path path, String partName) {
		return post(path, partName, 0, 0, null);
	}
}