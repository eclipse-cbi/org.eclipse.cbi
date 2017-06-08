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
package org.eclipse.cbi.webservice.servlet;

import static com.google.common.base.Preconditions.checkState;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;

/**
 * A facade to {@link HttpServletRequest}.
 */
@AutoValue
public abstract class RequestFacade implements Closeable {

	private final static Logger logger = LoggerFactory.getLogger(RequestFacade.class);

	private final Set<Part> partToDelete;

	RequestFacade() { //prevents subclassing and instantiation outside package
		this.partToDelete = new LinkedHashSet<>();
	}

	/**
	 * Returns the request being decorated
	 * @return the request being decorated
	 */
	abstract HttpServletRequest request();

	/**
	 * Returns the temporary folder where part files are stored.
	 * @return the temporary folder where part files are stored.
	 */
	abstract Path tempFolder();

	/**
	 * Creates and returns a new builder for this class.
	 *
	 * @param tempFolder
	 *            the temporary folder to be used by this object. <strong>Must
	 *            be the same as the one given to {@link MultipartConfig}
	 *            </strong>.
	 * @return a new builder for this class.
	 */
	public static Builder builder(Path tempFolder) {
		return new AutoValue_RequestFacade.Builder().tempFolder(tempFolder);
	}

	/**
	 * A builder of {@link RequestFacade} objects.
	 */
	@AutoValue.Builder
	public static abstract class Builder {
		Builder() {}

		/**
		 * Sets the to-be decorated request.
		 *
		 * @param request
		 *            the request to be decorated.
		 * @return this builder for daisy-chaining.
		 */
		public abstract Builder request(HttpServletRequest request);

		/**
		 * Sets the temporary folder to be used by the to-be created
		 * {@link RequestFacade}.
		 *
		 * @param path
		 *            the temporary folder
		 * @return this builder for daisy-chaining.
		 */
		abstract Builder tempFolder(Path path);

		abstract RequestFacade autoBuild();

		public RequestFacade build() {
			RequestFacade requestFacade = autoBuild();
			checkState(Files.isDirectory(requestFacade.tempFolder()), "Temp folder must be an existing directory");
			checkState(requestFacade.tempFolder().isAbsolute(), "Temp folder must be an absolute path");
			return requestFacade;
		}
	}

	/**
	 * Returns true if the decorated request has a part with the given name,
	 * false otherwise
	 *
	 * @param partName
	 *            the name of the part to check for existence.
	 * @return true if the decorated request has a part with the given name,
	 *         false otherwise
	 * @throws IOException if an I/O error occurred during the retrieval of the requested Part
	 * @throws ServletException if this request is not of type multipart/form-data
	 */
	public boolean hasPart(String partName) throws IOException, ServletException {
		return request().getPart(partName) != null;
	}

	/**
	 * Returns true if the decorated request has a parameter with the given name,
	 * false otherwise
	 *
	 * @param parameterName
	 *            the name of the parameter to check for existence.
	 * @return true if the decorated request has a parameter with the given name,
	 *         false otherwise
	 * @throws IOException if an I/O error occurred during the retrieval of the requested Part
	 * @throws ServletException if this request is not of type multipart/form-data
	 */
	public boolean hasParameter(String parameterName) throws IOException, ServletException {
		return request().getParameter(parameterName) != null;
	}

	/**
	 * Returns the file of the part with the given part name, or
	 * {@link Optional#empty()} is no part with the given name exists.
	 *
	 * @param partName
	 *            the name of the part with the desired file
	 * @return the file of the part with the given part name, or
	 *         {@link Optional#empty()} is no part with the given name exists.
	 * @throws IOException if an I/O error occurred during the retrieval of the requested Part
	 * @throws ServletException if this request is not of type multipart/form-data
	 */
	public Optional<Path> getPartPath(String partName) throws IOException, ServletException {
		return getPartPath(partName, null);
	}

	/**
	 * Returns the file of the part with the given part name, or
	 * {@link Optional#empty()} is no part with the given name exists.
	 * <p>
	 * The file name will be prefixed with the given string.
	 *
	 * @param partName
	 *            the name of the part with the desired file
	 * @param prefix
	 *            the prefix to be prepended to the file name
	 * @return the file of the part with the given part name, or
	 *         {@link Optional#empty()} is no part with the given name exists.
	 * @throws IOException if an I/O error occurred during the retrieval of the requested Part
	 * @throws ServletException if this request is not of type multipart/form-data
	 */
	public Optional<Path> getPartPath(String partName, String prefix) throws IOException, ServletException {
		return getPartPath(partName, prefix, null);
	}

	/**
	 * Returns the file of the part with the given part name, or
	 * {@link Optional#empty()} is no part with the given name exists.
	 * <p>
	 * The file name will be prefixed with the given string and suffixed with
	 * the other one.
	 *
	 * @param partName
	 *            the name of the part with the desired file
	 * @param prefix
	 *            the prefix to be prepended to the file name
	 * @param suffix
	 *            the suffix to be appended to the file name
	 * @return the file of the part with the given part name, or
	 *         {@link Optional#empty()} is no part with the given name exists.
	 * @throws IOException if an I/O error occurred during the retrieval of the requested Part
	 * @throws ServletException if this request is not of type multipart/form-data
	 */
	public Optional<Path> getPartPath(String partName, String prefix, String suffix) throws IOException, ServletException {
		final Path ret;

		Part part = request().getPart(partName);
		if (part != null) {
			Path generatedPath = generatePath(Strings.nullToEmpty(prefix), "-" + Strings.nullToEmpty(part.getSubmittedFileName()) + Strings.nullToEmpty(suffix));
			// may rename in the temp folder as specified in MultipartConfig
			part.write(tempFolder().relativize(generatedPath).toString());

			if (!Files.exists(generatedPath)) {
				ret = null;
			} else {
				this.partToDelete.add(part);
				ret = generatedPath;
			}
		} else {
			ret = null;
		}
		return Optional.ofNullable(ret);
	}

	/**
	 * Returns the submitted file name of the part with the given name. If the
	 * decorated request has no request with the given name, it will return
	 * {@link Optional#empty()}
	 *
	 * @param partName
	 *            the name of the part from which the filename is desired.
	 * @return the submitted file name of the part with the given name. If the
	 *         decorated request has no request with the given name, it will
	 *         return {@link Optional#empty()}
	 * @throws IOException if an I/O error occurred during the retrieval of the requested Part
	 * @throws ServletException if this request is not of type multipart/form-data
	 */
	public Optional<String> getSubmittedFileName(String partName) throws IOException, ServletException {
		final String ret;
		Part part = request().getPart(partName);
		if (part != null) {
			String submittedFileName = part.getSubmittedFileName();
			if (!Strings.isNullOrEmpty(submittedFileName)) {
				ret = submittedFileName;
			} else {
				ret = null;
			}
		} else {
			ret = null;
		}

		return Optional.ofNullable(ret);
	}

	/**
	 * Returns the input stream associated with the part with the given name. If
	 * the decorated request has no part with the given name,
	 * {@link Optional#empty()} is returned.
	 *
	 * @param partName
	 *            the name of the part with the desired input stream.
	 * @return the input stream associated with the part with the given name. If
	 *         the decorated request has no part with the given name,
	 *         {@link Optional#empty()} is returned.
	 * @throws IOException if an I/O error occurred during the retrieval of the requested Part
	 * @throws ServletException if this request is not of type multipart/form-data
	 */
	public Optional<InputStream> getPartInputStream(String partName) throws IOException, ServletException {
		final InputStream ret;
		Part part = request().getPart(partName);
		if (part != null) {
			ret = part.getInputStream();
		} else {
			ret = null;
		}
		return Optional.ofNullable(ret);
	}

	/**
	 * Returns the value of the parameter with the given. If the the decorated
	 * request has no parameter with the given name, {@link Optional#empty()} is
	 * returned.
	 *
	 * @param parameterName
	 *            the name of the desired parameter
	 * @return the value of the parameter with the given. If the the decorated
	 *         request has no parameter with the given name,
	 *         {@link Optional#empty()} is returned.
	 */
	public Optional<String> getParameter(String parameterName) {
		return Optional.ofNullable(request().getParameter(parameterName));
	}

	@Override
	public void close() throws IOException {
		partToDelete.forEach(p -> {
			try {
				p.delete();
			} catch (Exception e) {
				logger.error("Error occured while deleting a temporary resource", e);
			}
		});
	}

	/** Random generator for file name generation */
    private static final SecureRandom random = new SecureRandom();

    /**
     * Generates a random string with the given prefix and suffix.
     */
    private static String randomString(String prefix, String suffix) {
        long n = random.nextLong();
        n = (n == Long.MIN_VALUE) ? 0 : Math.abs(n);
        return prefix + Long.toString(n) + suffix;
    }

	/**
	 * Generates a valid random path in {@link #tempFolder() the temporary
	 * folder}. It is valid in the sense that it does not exists when returned.
	 */
	private Path generatePath(String prefix, String suffix) {
		Path ret = null;
		do {
			final String generateFilename = randomString(prefix, suffix);
			final Path resolvedPath = tempFolder().resolve(generateFilename);
			if (!Files.exists(resolvedPath)) {
				ret = resolvedPath;
			}
		} while (ret == null);
		return ret;
	}
}
