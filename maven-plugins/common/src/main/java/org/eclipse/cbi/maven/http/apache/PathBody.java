/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.http.apache;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.http.util.Args;

/**
 * Binary body part backed by a path.
 *
 * @see org.apache.http.entity.mime.MultipartEntityBuilder
 */
final class PathBody extends AbstractContentBody {

	private final Path path;

	PathBody(Path path) {
		super(ContentType.DEFAULT_BINARY);
		this.path = path;
	}
	
	public PathBody(Path path, String contentType) {
		super(ContentType.parse(contentType));
		this.path = path;
	}
	
	@Override
	public String getFilename() {
		return path.getFileName().toString();
	}

	@Override
	public void writeTo(OutputStream outstream) throws IOException {
		Args.notNull(outstream, "Output stream");
		Files.copy(path, outstream);
		outstream.flush();
	}

	@Override
	public String getTransferEncoding() {
		return MIME.ENC_BINARY;
	}

	@Override
	public long getContentLength() {
		try {
			return Files.size(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
