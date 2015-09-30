package org.eclipse.cbi.maven.common.test.util;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.maven.common.FileProcessor;

public class DummyProcessor implements FileProcessor {
	@Override
	public boolean process(Path path, int retryLimit, int retryTimer, TimeUnit timeUnit) {
		return true;
	}

	@Override
	public boolean process(Path path) {
		return process(path, 0, 0, null);
	}
}
