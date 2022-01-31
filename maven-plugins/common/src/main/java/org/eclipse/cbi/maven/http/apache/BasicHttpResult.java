/*******************************************************************************
 * Copyright (c) 2015 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.http.apache;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.eclipse.cbi.maven.http.HttpResult;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;

final class BasicHttpResult implements HttpResult {

	private final int status;
	private final String reason;
	private final HttpEntity entity;

	BasicHttpResult(int status, String reason, HttpEntity entity) {
		this.status = status;
		this.reason = Preconditions.checkNotNull(reason);
		this.entity = Preconditions.checkNotNull(entity);
	}
	
	@Override
	public int statusCode() {
		return status;
	}

	@Override
	public String reason() {
		return reason;
	}
	
	@Override
	public long contentLength() {
		return entity.getContentLength();
	}

	@Override
	public long copyContent(Path target, CopyOption... options) throws IOException {
		try (InputStream is = new BufferedInputStream(entity.getContent())) {
			return Files.copy(is, target, options);
		}
	}
	
	@Override
	public long copyContent(OutputStream target) throws IOException {
		try (InputStream is = new BufferedInputStream(entity.getContent())) {
			return ByteStreams.copy(is, target);
		}
	}

	@Override
	public Charset contentCharset() {
		ContentType contentType = ContentType.get(entity);
		final Charset cs;
		if (contentType != null && contentType.getCharset() != null) {
			cs = contentType.getCharset();
		} else {
			cs = StandardCharsets.UTF_8;
		}
		return cs;
	}
}