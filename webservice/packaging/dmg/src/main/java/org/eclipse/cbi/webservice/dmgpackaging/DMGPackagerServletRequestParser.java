/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikaël Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.webservice.dmgpackaging;

import static com.google.common.base.Preconditions.checkState;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.servlet.ServletException;

import org.eclipse.cbi.common.util.Paths;
import org.eclipse.cbi.common.util.Zips;
import org.eclipse.cbi.webservice.servlet.RequestFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;

/**
 * A parser for request to {@link DMGPackagerServlet}.
 * <p>
 * It will create temporary resources during call to some methods. Calling
 * {@link #close()} method will remove these temporary resources.
 */
@AutoValue
public abstract class DMGPackagerServletRequestParser implements Closeable {

	private final static Logger logger = LoggerFactory.getLogger(DMGPackagerServletRequestParser.class);
	
	private static final String DOT_APP_GLOB_PATTERN = "glob:**.app";
	static final String SOURCE_PART_NAME = "source";
	private static final String DOT_TAR_GZ = ".tar.gz";
	private static final String TEMP_FILE_PREFIX = DMGPackagerServletRequestParser.class.getSimpleName() + "-";
	
	private final Set<Path> tempPaths;
	
	DMGPackagerServletRequestParser() {
		tempPaths = new LinkedHashSet<Path>();
	}
	
	@Override
	public void close() throws IOException {
		Set<Path> pathToClean = new LinkedHashSet<>(tempPaths);
		tempPaths.clear();
		
		pathToClean.forEach(p -> {
			try {
				if (p != null && Files.exists(p)) {
					Paths.delete(p);
				}
			} catch (IOException e) {
				logger.error("Error occured while deleting temporary resource '" + p.toString() + "'", e);
			}
		});
	}
	
	public Path getSource() throws RequestParserException, IOException, ServletException {
		if (requestFacade().hasPart(SOURCE_PART_NAME)) {
			if (requestFacade().getSubmittedFileName(SOURCE_PART_NAME).get().endsWith(DOT_TAR_GZ)) {
				Optional<Path> sourcePath = requestFacade().getPartPath(SOURCE_PART_NAME, TEMP_FILE_PREFIX);
				if (sourcePath.isPresent()) {
					return extractApp(sourcePath.get());
				} else {
					throw new RequestParserException("An error occured while retrieving the content of the part named '" + SOURCE_PART_NAME + "'");
				}
			} else {
				throw new RequestParserException("The file name of the part named '" + SOURCE_PART_NAME + "' must have a 'tar.gz' extension");
			}
		} else {
			throw new RequestParserException("The request must contain a part named '" + SOURCE_PART_NAME + "'");
		}
	}
	
	private Path extractApp(Path sourcePath) throws IOException, ServletException, RequestParserException {
		Path untarFolder = createTempDirectory(tempFolder(), TEMP_FILE_PREFIX);
		untar(sourcePath, untarFolder);
		return findFirstAppInFolder(untarFolder);
	}
	
	private Path findFirstAppInFolder(Path folder) throws IOException, RequestParserException {
		final PathMatcher dotAppPathMatcher = folder.getFileSystem().getPathMatcher(DOT_APP_GLOB_PATTERN);
		
		try (Stream<Path> stream = Files.walk(folder, 1)) {
			final Optional<Path> firstAppFolder = stream.filter(p -> 
				Files.isDirectory(p) && dotAppPathMatcher.matches(p)
			).findFirst();
			
			if (!firstAppFolder.isPresent()) {
				throw new RequestParserException("Can't find a '.app' folder in the submitted '" + SOURCE_PART_NAME + "' tar.gz file ");
			} else {
				return firstAppFolder.get();
			}
		}
	}
	
	private void untar(Path sourcePath, Path extractFolder) throws RequestParserException, IOException {
		int unpackedEntries = Zips.unpackTarGz(sourcePath, extractFolder);
		if (unpackedEntries <= 0) {
			throw new RequestParserException("The provided '" + SOURCE_PART_NAME + "' part is not a valid '.tar.gz' file.");
		}
	}

	public Optional<String> getVolumeName() throws IOException {
		return requestFacade().getParameter("volumeName");
	}
	
	public Optional<String> getWindowSize() throws IOException {
		return requestFacade().getParameter("windowSize");
	}
	
	public Optional<String> getWindowPosition() throws IOException {
		return requestFacade().getParameter("windowPosition");
	}
	
	public Optional<String> getIconSize() throws IOException {
		return requestFacade().getParameter("iconSize");
	}
	
	public Optional<String> getIcon() throws IOException {
		return requestFacade().getParameter("icon");
	}
	
	public Optional<String> getAppDropLink() throws IOException {
		return requestFacade().getParameter("appDropLink");
	}
	
	public Optional<Path> getVolumeIcon() throws IOException, RequestParserException, ServletException {
		return getCommandFileOption("volumeIcon");
	}
	
	public Optional<Path> getBackgroundImage() throws IOException, RequestParserException, ServletException {
		return getCommandFileOption("backgroundImage");
	}
	
	public Optional<Path> getEula() throws IOException, RequestParserException, ServletException {
		return getCommandFileOption("eula");
	}
	
	private Optional<Path> getCommandFileOption(String partName) throws IOException, ServletException, RequestParserException {
		final Optional<Path> ret;
		if (requestFacade().hasPart(partName)) {
			Optional<Path> partPath = requestFacade().getPartPath(partName, TEMP_FILE_PREFIX);
			if (partPath.isPresent()) {
				ret = partPath;
			} else {
				throw new RequestParserException("Can not retrieve the content of part '" + partName + "'");
			}
		} else {
			ret = Optional.empty();
		}
		return ret;
	}
	
	private Path createTempDirectory(Path folder, String prefix) throws IOException {
		Path tempFolder = Files.createTempDirectory(folder, prefix);
		tempPaths.add(tempFolder);
		return tempFolder;
	}

	abstract RequestFacade requestFacade();

	abstract Path tempFolder();

	public static Builder builder(Path tempFolder) {
		return new AutoValue_DMGPackagerServletRequestParser.Builder()
			.tempFolder(tempFolder);
	}

	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}
		public abstract Builder requestFacade(RequestFacade requestFacade);
		abstract Builder tempFolder(Path path);
		abstract DMGPackagerServletRequestParser autoBuild();
		public DMGPackagerServletRequestParser build() {
			DMGPackagerServletRequestParser parser = autoBuild();
			checkState(Files.isDirectory(parser.tempFolder()), "Temporary folder must be an existing directory");
			return parser;
		}
	}
	
	public static class RequestParserException extends RuntimeException {

		private static final long serialVersionUID = -6311813509650875970L;

		public RequestParserException(String message, Throwable cause) {
			super(message, cause);
		}

		public RequestParserException(String message) {
			super(message);
		}

		public RequestParserException(Throwable cause) {
			super(cause);
		}
	}
}
