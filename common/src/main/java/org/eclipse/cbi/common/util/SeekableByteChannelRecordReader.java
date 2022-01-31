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
import java.nio.channels.SeekableByteChannel;

import org.eclipse.cbi.common.util.RecordDefinition.Field;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.Ints;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

class SeekableByteChannelRecordReader {
	private final SeekableByteChannel channel;
	
	public SeekableByteChannelRecordReader(SeekableByteChannel channel) {
		this.channel = channel;
	}

	public ByteBuffer read(RecordDefinition recordDefinition, long position) throws IOException {
		int recordLength = computeLength(recordDefinition, position);
		final ByteBuffer buffer = ByteBuffer.allocate(recordLength);
		
		channel.position(position);
		final int read = channel.read(buffer);
		if (read != buffer.capacity()) {
			throw new IOException("Did not read the correct number of byte (expected="+buffer.capacity()+", read="+read+")");
		}
		
		return (ByteBuffer) buffer.rewind();
	}

	private int uint16(Field field, RecordDefinition recordDefinition, long position) throws IOException {
		if (field.type() != Field.Type.UINT_16) {
			throw new IllegalArgumentException("Field is not uint16");
		}
		return readField(field, recordDefinition, position).getShort() & 0xffff;
	}
	
	public UnsignedInteger uint32(Field field, RecordDefinition recordDefinition, long position) throws IOException {
		if (field.type() != Field.Type.UINT_32) {
			throw new IllegalArgumentException("Field is not uint32");
		}
		return UnsignedInteger.fromIntBits(readField(field, recordDefinition, position).getInt());
	}
	
	private UnsignedLong uint64(Field field, RecordDefinition recordDefinition, long position) throws IOException {
		if (field.type() != Field.Type.UINT_64) {
			throw new IllegalArgumentException("Field is not uint64");
		}
		return UnsignedLong.fromLongBits(readField(field, recordDefinition, position).getLong());
	}
	
	private ByteBuffer readField(Field field, RecordDefinition recordDefinition, long position) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(field.type().size()).order(field.byteOrder());
		
		channel.position(position(field, recordDefinition, position));
		if (channel.read(buffer) != field.type().size()) {
			throw new IOException("Did not read the correct number of byte");
		}
		return (ByteBuffer) buffer.rewind();
	}

	private long position(Field field, RecordDefinition recordDefinition, long position) throws IOException {
		long value = position + offset(field, recordDefinition, position);
		if (value < 0) {
			throw new ArithmeticException("long overflow");
		}
		return value;
	}
	
	private long offset(RecordDefinition.Field field, RecordDefinition recordDefinition, long position) throws IOException {
		long offset = 0;
		for (RecordDefinition.Field f : recordDefinition.fields()) {
			if (field == f) {
				break;
			}
			offset += size(f, recordDefinition, position);
			if (offset < 0) {
				throw new ArithmeticException("long overflow");
			}
		}
		return offset;
	}
	
	private long size(RecordDefinition.Field f, RecordDefinition recordDefinition, long position) throws IOException {
		final long r;
		if (f.type() != Field.Type.VARIABLE) {
			r = f.type().size();
		} else {
			Field lenghtField = recordDefinition.fieldDefiningSizeOf(f);
			switch (lenghtField.type()) {
				case UINT_16:
					r = uint16(lenghtField, recordDefinition, position);
					break;
				case UINT_32:
					r = uint32(lenghtField, recordDefinition, position).longValue();
					break;
				case UINT_64:
					r = uint64(lenghtField, recordDefinition, position).longValue();
					if (r < 0) {
						throw new ArithmeticException("Can not handle uint64 size larger than Long.MAX_VALUE");
					}
					break;
				default:
					throw new IllegalArgumentException("Unsupported FieldLength");
			}
		}
		return r;
	}
	
	private int computeLength(RecordDefinition recordDefinition, long position) throws IOException {
		long r = 0;
		for (RecordDefinition.Field f : recordDefinition.fields()) {
			r += size(f, recordDefinition, position);
			if (r < 0) {
				throw new ArithmeticException("long overflow");
			}
		}
		return Ints.checkedCast(r);
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("channel", channel)
				.toString();
	}
}