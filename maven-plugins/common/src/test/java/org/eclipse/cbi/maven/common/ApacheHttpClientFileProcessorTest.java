package org.eclipse.cbi.maven.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.maven.common.ApacheHttpClientFileProcessor;
import org.eclipse.cbi.maven.common.test.util.NullJettyLogger;
import org.eclipse.cbi.maven.common.test.util.NullLog;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.log.Log;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class ApacheHttpClientFileProcessorTest {

	@BeforeClass
	public static void beforeClass() {
		Log.setLog(new NullJettyLogger());
	}

	@Test(expected=IllegalArgumentException.class)
	public void testProcessNullFile() throws IOException {
		ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", 8080);
		processor.process(null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testProcessNonExistingFile() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", 8080);
			Path path = fs.getPath("fileToProcess");
			processor.process(path);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testProcessDirectory() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", 8080);
			Path path = fs.getPath("folderToProcess");
			Files.createDirectories(path);
			processor.process(path);
		}
	}

	public void testProcessOfflineServer() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientFileProcessor processor = createLocalProcessor("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
			assertFalse(processor.process(path));
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRetryNegativeTimes() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientFileProcessor processor = createLocalProcessor("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
			processor.process(path, -1, 0, TimeUnit.SECONDS);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testRetryNegativeInterval() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientFileProcessor processor = createLocalProcessor("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
			processor.process(path, 5, -4, TimeUnit.SECONDS);
		}
	}

	@Test(expected=NullPointerException.class)
	public void testRetryNullUnit() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientFileProcessor processor = createLocalProcessor("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
			processor.process(path, 5, 4, null);
		}
	}

	@Test
	public void testProcessStandardFile() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Server server = createProcessingServer(createProcessingHandler());
			try {
				ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
				assertTrue(processor.process(path));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					assertArrayEquals("Processed!".getBytes(), ByteStreams.toByteArray(newInputStream));
				}
			} finally {
				server.stop();
			}
		}
	}

	@Test
	public void testProcessRequest() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Server server = createProcessingServer(createTestingHandler());
			try {
				ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
				assertTrue(processor.process(path));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					assertArrayEquals("Valid!".getBytes(), ByteStreams.toByteArray(newInputStream));
				}
			} finally {
				server.stop();
			}
		}
	}

	@Test
	public void testServerError() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ServiceUnavailableHandler handler = createServiceUnavailableHandler();
			Server server = createProcessingServer(handler);
			try {
				ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
				assertFalse(processor.process(path));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					// check that the file content did not change
					assertArrayEquals(Files.readAllBytes(path), ByteStreams.toByteArray(newInputStream));
				}
				assertEquals(1, handler.getRequestCount());
			} finally {
				server.stop();
			}
		}
	}

	@Test
	public void testRetryOnServerError() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ServiceUnavailableHandler handler = createServiceUnavailableHandler();
			Server server = createProcessingServer(handler);
			try {
				ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
				assertFalse(processor.process(path, 3, 100, TimeUnit.MILLISECONDS));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					// check that the file content did not change
					assertArrayEquals(Files.readAllBytes(path), ByteStreams.toByteArray(newInputStream));
				}
				assertEquals(4, handler.getRequestCount());
			} finally {
				server.stop();
			}
		}
	}

	@Test(expected=IOException.class)
	public void testRetryOnException() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientFileProcessor processor = createLocalProcessor("localhost", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToProcess"), 10);
			processor.process(path, 3, 100, TimeUnit.MILLISECONDS);
		}
	}

	private static int getPort(Server server) {
		return ((NetworkConnector)server.getConnectors()[0]).getLocalPort();
	}

	private static ApacheHttpClientFileProcessor createLocalProcessor(String host, int port) {
		return new ApacheHttpClientFileProcessor(URI.create("http://"+host+":"+port+"/processing-service"), "file", new NullLog());
	}

	private static Server createProcessingServer(Handler handler) throws Exception {
		Server server = new Server(0);
        server.setHandler(handler);
        server.start();
        return server;
	}

	private static Handler createTestingHandler() {
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, new MultipartConfigElement(""));
				assertEquals("/processing-service", target);
				assertTrue(request.getContentType().startsWith(ContentType.MULTIPART_FORM_DATA.getMimeType()));
				assertTrue(HttpMethod.POST.is(request.getMethod()));
				assertEquals(1, baseRequest.getParts().size());
				assertEquals("fileToProcess", baseRequest.getPart("file").getSubmittedFileName());
				baseRequest.setHandled(true);
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("Valid!");
			}
		};
	}

	private static AbstractHandler createProcessingHandler() {
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				baseRequest.setHandled(true);
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("Processed!");
			}
		};
	}

	private static ServiceUnavailableHandler createServiceUnavailableHandler() {
		return new ServiceUnavailableHandler();
	}

	private static final class ServiceUnavailableHandler extends AbstractHandler {
		int requestCount;

		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
			requestCount++;
			baseRequest.setHandled(true);
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			response.getWriter().print("Some more explanations about the error from the server!");
		}

		public int getRequestCount() {
			return requestCount;
		}
	}
}
