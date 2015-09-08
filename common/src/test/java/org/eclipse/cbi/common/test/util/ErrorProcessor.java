package org.eclipse.cbi.common.test.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.common.FileProcessor;

public class ErrorProcessor implements FileProcessor {
	@Override
	public boolean process(Path path, int retryLimit, int retryTimer, TimeUnit timeUnit) throws IOException {
		throw new IOException("Something bad happened in the processor!");
	}

	@Override
	public boolean process(Path path) throws IOException {
		return process(path, 0, 0, null);
	}
}