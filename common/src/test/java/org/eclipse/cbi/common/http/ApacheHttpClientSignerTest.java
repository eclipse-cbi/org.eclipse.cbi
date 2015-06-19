package org.eclipse.cbi.common.http;

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
import org.eclipse.cbi.common.http.ApacheHttpClientPostFileSender;
import org.eclipse.cbi.common.test.util.SampleFilesGenerators;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.log.Log;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class ApacheHttpClientSignerTest {

	@BeforeClass
	public static void beforeClass() {
		Log.setLog(new NullJettyLogger());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSignNullFile() throws IOException {
		ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", 8080);
		signer.post(null, null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSignNonExistingFile() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", 8080);
			Path path = fs.getPath("fileToSign");
			signer.post(path, "");
		}
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSignDirectory() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", 8080);
			Path path = fs.getPath("folderToSign");
			Files.createDirectories(path);
			signer.post(path, "file");
		}
	}
	
	public void testSignOfflineServer() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientPostFileSender signer = createLocalSigner("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
			assertFalse(signer.post(path, "file"));
		}
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testRetryNegativeTimes() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientPostFileSender signer = createLocalSigner("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
			signer.post(path, "file", -1, 0, TimeUnit.SECONDS);
		}
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testRetryNegativeInterval() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientPostFileSender signer = createLocalSigner("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
			signer.post(path, "file", 5, -4, TimeUnit.SECONDS);
		}
	}
	
	@Test(expected=NullPointerException.class)
	public void testRetryNullUnit() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			ApacheHttpClientPostFileSender signer = createLocalSigner("qwerty", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
			signer.post(path, "file", 5, 4, null);
		}
	}
	
	@Test
	public void testSignStandardFile() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Server server = createSigningServer(createSigningHandler());
			try {
				ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
				assertTrue(signer.post(path, "file"));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					assertArrayEquals("Signed!".getBytes(), SampleFilesGenerators.readAllBytes(newInputStream));
				}
			} finally {
				server.stop();
			}
		}
	}
	
	@Test
	public void testSignRequest() throws Exception {
		try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
			Server server = createSigningServer(createTestingHandler());
			try {
				ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
				assertTrue(signer.post(path, "file"));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					assertArrayEquals("Valid!".getBytes(), SampleFilesGenerators.readAllBytes(newInputStream));
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
			Server server = createSigningServer(handler);
			try {
				ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
				assertFalse(signer.post(path, "file"));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					// check that the file content did not change
					assertArrayEquals(Files.readAllBytes(path), SampleFilesGenerators.readAllBytes(newInputStream));
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
			Server server = createSigningServer(handler);
			try {
				ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", getPort(server));
				Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
				assertFalse(signer.post(path, "file", 3, 100, TimeUnit.MILLISECONDS));
				try (InputStream newInputStream = Files.newInputStream(path)) {
					// check that the file content did not change
					assertArrayEquals(Files.readAllBytes(path), SampleFilesGenerators.readAllBytes(newInputStream));
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
			ApacheHttpClientPostFileSender signer = createLocalSigner("localhost", 8080);
			Path path = SampleFilesGenerators.createLoremIpsumFile(fs.getPath("fileToSign"), 10);
			signer.post(path, "file", 3, 100, TimeUnit.MILLISECONDS);
		}
	}

	private static int getPort(Server server) {
		return ((NetworkConnector)server.getConnectors()[0]).getLocalPort();
	}

	private static ApacheHttpClientPostFileSender createLocalSigner(String host, int port) {
		return new ApacheHttpClientPostFileSender(URI.create("http://"+host+":"+port+"/signing-service"), new NullLog());
	}
	
	private static Server createSigningServer(Handler handler) throws Exception {
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
				assertEquals("/signing-service", target);
				assertTrue(request.getContentType().startsWith(ContentType.MULTIPART_FORM_DATA.getMimeType()));
				assertTrue(HttpMethod.POST.is(request.getMethod()));
				assertEquals(1, baseRequest.getParts().size());
				assertEquals("fileToSign", baseRequest.getPart("file").getSubmittedFileName());
				baseRequest.setHandled(true);
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("Valid!");
			}
		};
	}

	private static AbstractHandler createSigningHandler() {
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				baseRequest.setHandled(true);
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().print("Signed!");
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
