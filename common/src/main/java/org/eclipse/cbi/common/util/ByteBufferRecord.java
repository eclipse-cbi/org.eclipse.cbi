/*******************************************************************************
 * Copyright (c) 2016 Eclipse Foundation and others
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
package org.eclipse.cbi.common.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

import org.eclipse.cbi.common.util.RecordDefinition.Field;

import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

class ByteBufferRecord implements Record {

	private final ByteBuffer buffer;
	private final RecordDefinition recordDefinition;

	public ByteBufferRecord(RecordDefinition recordDefinition, ByteBuffer buffer) throws IOException {
		this.recordDefinition = Objects.requireNonNull(recordDefinition);
		this.buffer = Objects.requireNonNull(buffer);
	}

	@Override
	public int uint16Value(RecordDefinition.Field field) {
		int offset = offset(Objects.requireNonNull(field));
		return buffer.order(field.byteOrder()).getShort(offset) & 0xffff;
	}

	@Override
	public UnsignedInteger uint32Value(RecordDefinition.Field field) {
		int offset = offset(Objects.requireNonNull(field));
		return UnsignedInteger.fromIntBits(buffer.order(field.byteOrder()).getInt(offset));
	}

	@Override
	public UnsignedLong uint64Value(Field field) {
		int offset = offset(Objects.requireNonNull(field));
		return UnsignedLong.fromLongBits(buffer.order(field.byteOrder()).getLong(offset));
	}

	@Override
	public String stringValue(Field field, Charset charset) {
		int offset = offset(Objects.requireNonNull(field));
		int size = Ints.checkedCast(size(Objects.requireNonNull(field)));
		byte[] dst = new byte[size];
		buffer.position(offset);
		buffer.order(field.byteOrder()).get(dst);
		return new String(dst, charset);
	}

	@Override
	public long size() {
		final long size;
		if (recordDefinition.fields().isEmpty()) {
			size = 0;
		} else {
			Field lastField = recordDefinition.fields().get(recordDefinition.fields().size() - 1);
			size = offset(lastField) + size(lastField);
			if (size < 0) {
				throw new ArithmeticException("long overflow");
			}
		}
		return size;
	}

	private int offset(Field field) {
		Objects.requireNonNull(field);
		long offset = 0;
		for (RecordDefinition.Field f : recordDefinition.fields()) {
			if (field == f) {
				break;
			}
			offset = offset + size(f);
			if (offset < 0) {
				throw new ArithmeticException("long overflow");
			}
		}
		return Ints.checkedCast(offset);
	}

	private long size(Field f) {
		Objects.requireNonNull(f);
		final long r;
		if (f.type() != Field.Type.VARIABLE) {
			r = f.type().size();
		} else {
			final Field lenghtField = recordDefinition.fieldDefiningSizeOf(f);
			switch (lenghtField.type()) {
			case UINT_16:
				r = uint16Value(lenghtField);
				break;
			case UINT_32:
				r = uint32Value(lenghtField).longValue();
				break;
			case UINT_64:
				r = uint64Value(lenghtField).longValue();
				if (r < 0) {
					throw new ArithmeticException("Can not handle uint64 size larger than Long.MAX_VALUE");
				}
			default:
				throw new IllegalArgumentException("Unsupported field type for length");
			}
		}
		return r;
	}
}