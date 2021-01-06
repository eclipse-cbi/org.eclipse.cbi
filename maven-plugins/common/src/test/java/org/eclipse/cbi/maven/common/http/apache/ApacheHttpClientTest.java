package org.eclipse.cbi.maven.common.http.apache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.apache.http.entity.ContentType;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.cbi.maven.common.test.util.NullJettyLogger;
import org.eclipse.cbi.maven.common.test.util.NullLog;
import org.eclipse.cbi.maven.http.CompletionListener;
import org.eclipse.cbi.maven.http.HttpClient;
import org.eclipse.cbi.maven.http.HttpRequest;
import org.eclipse.cbi.maven.http.HttpRequest.Builder;
import org.eclipse.cbi.maven.http.HttpResult;
import org.eclipse.cbi.maven.http.apache.ApacheHttpClient;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.log.Log;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApacheHttpClientTest {

	private NullLog log;

	@BeforeClass
	public static void beforeClass() {
		Log.setLog(new NullJettyLogger());
	}

	@Before
	public void before() {
		log = new NullLog();
	}

	@Test
	public void testProcessStandardFile() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Server server = createProcessingServer(createProcessingHandler());
			try {

				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("/pathto/fileToProcess"), 10);
				HttpClient client = ApacheHttpClient.create(log);
				HttpRequest request = newRequest("localhost", getPort(server)).withParam("file", path).build();
				assertTrue(client.send(request, new FailTestOnError()));
			} finally {
				server.stop();
			}
		}
	}

	@Test(expected = IOException.class)
	public void testProcessOfflineServer() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Path file = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("/pathto/fileToProcess"), 10);
			HttpClient client = ApacheHttpClient.create(log);
			HttpRequest request = newRequest("localhost", 8080).withParam("file", file).build();
			client.send(request, new FailTestOnError());
		}
	}

	@Test
	public void testProcessRequest() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Server server = createProcessingServer(createTestingHandler());
			try {
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("/pathto/fileToProcess"), 10);
				HttpClient client = ApacheHttpClient.create(log);
				HttpRequest request = newRequest("localhost", getPort(server)).withParam("file", path).build();
				assertTrue(client.send(request, new CompletionListener() {

					@Override
					public void onError(HttpResult error) throws IOException {
						Assert.fail();
					}

					@Override
					public void onSuccess(HttpResult result) throws IOException {
						assertEquals(200, result.statusCode());

						ByteArrayOutputStream response = new ByteArrayOutputStream();
						result.copyContent(response);
						assertArrayEquals("Valid!".getBytes(), response.toByteArray());
					}
				}));
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
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("/pathto/fileToProcess"), 10);
				HttpClient client = ApacheHttpClient.create(log);
				HttpRequest request = newRequest("localhost", getPort(server)).withParam("file", path).build();
				assertFalse(client.send(request, new FailTestOnSuccess()));
				assertEquals(1, handler.getRequestCount());
			} finally {
				server.stop();
			}
		}
	}

	private Builder newRequest(String host, int port) {
		return HttpRequest.on(URI.create("http://" + host + ":" + port + "/processing-service"));
	}

	private static Server createProcessingServer(Handler handler) throws Exception {
		Server server = new Server(0);
        server.setHandler(handler);
        server.start();
        return server;
	}

	private static int getPort(Server server) {
		return ((NetworkConnector)server.getConnectors()[0]).getLocalPort();
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

	private static final class FailTestOnError implements CompletionListener {
		@Override
		public void onError(HttpResult error) throws IOException {
			Assert.fail();
		}

		@Override
		public void onSuccess(HttpResult result) throws IOException {

		}
	}

	private static final class FailTestOnSuccess implements CompletionListener {
		@Override
		public void onError(HttpResult error) throws IOException {
		}

		@Override
		public void onSuccess(HttpResult result) throws IOException {
			Assert.fail();
		}
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
