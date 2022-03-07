/*******************************************************************************
 * Copyright (c) 2016, 2022 Eclipse Foundation and others
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

import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.cbi.common.util.RecordDefinition.Field.Type;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@AutoValue
abstract class RecordDefinition {

	public abstract String name();

	public abstract long signature();

	public abstract Optional<Field> signatureField();

	public abstract List<Field> fields();

	protected abstract Map<Field, Field> sizeDefinitionFields();

	public abstract Class<? extends Record> recordClass();

	public Field fieldDefiningSizeOf(Field field) {
		Preconditions.checkArgument(field.type() == Type.VARIABLE);
		Preconditions.checkArgument(fields().contains(field));
		final Field sizeDefField = sizeDefinitionFields().get(field);
		if (sizeDefField == null) {
			throw new NullPointerException();
		}
		return sizeDefField;
	}

	public int size() {
		int size = 0;
		for (Field f : fields()) {
			if (f.type() != Type.VARIABLE) {
				size += f.type().size();
			} else {
				throw new UnsupportedOperationException("Record definition size is not fixed");
			}
		}

		return size;
	}

	public static Builder builder() {
		return new AutoValue_RecordDefinition.Builder().sizeDefinitionFields(Map.<Field, Field>of()).signature(-1)
				.signatureField(Optional.empty());
	}

	@AutoValue.Builder
	public static abstract class Builder {

		public abstract Builder name(String name);

		public abstract Builder fields(List<Field> fields);

		public abstract Builder signature(long signature);

		public abstract Builder signatureField(Optional<Field> signatureField);

		public abstract Builder sizeDefinitionFields(Map<Field, Field> sizeDefinitionFields);

		public abstract Builder recordClass(Class<? extends Record> clazz);

		abstract RecordDefinition autobuild();

		public RecordDefinition build() {
			RecordDefinition ret = autobuild();
			for (Field f : ret.fields()) {
				if (f.type() == Type.VARIABLE) {
					Preconditions.checkArgument(ret.sizeDefinitionFields().get(f) != null);
				}
			}
			for (Entry<Field, Field> f : ret.sizeDefinitionFields().entrySet()) {
				Preconditions.checkArgument(ret.fields().contains(f.getKey()));
				Preconditions.checkArgument(ret.fields().contains(f.getValue()));
				Preconditions.checkArgument(f.getKey().type() == Type.VARIABLE);
				Preconditions.checkArgument(f.getValue().type() != Type.VARIABLE);
				Preconditions.checkArgument(ret.fields().indexOf(f.getValue()) < ret.fields().indexOf(f.getKey()));
				if (!ret.signatureField().isPresent()) {
					Preconditions.checkArgument(ret.fields().contains(ret.signatureField().get()));
				}
			}
			return ret;
		}

	}

	public static Field createLEField(Type size, String name) {
		return new BasicFieldImpl(size, name, ByteOrder.LITTLE_ENDIAN);
	}

	public interface Field {

		/**
		 * Maximum value stored in uint16
		 */
		int UINT16_MAX_VALUE = 0xFFFF;

		enum Type {
			UINT_16(2), UINT_32(4), UINT_64(8), VARIABLE(-1);

			private final int length;

			Type(int length) {
				this.length = length;
			}

			public int size() {
				return length;
			}
		}

		String name();

		ByteOrder byteOrder();

		Type type();
	}

	private static class BasicFieldImpl implements Field {
		private final Type size;
		private final String specname;
		private final ByteOrder endianness;

		BasicFieldImpl(Type size, String specname, ByteOrder endianness) {
			this.endianness = endianness;
			this.size = Objects.requireNonNull(size);
			this.specname = Objects.requireNonNull(specname);
		}

		@Override
		public Type type() {
			return size;
		}

		@Override
		public String name() {
			return specname;
		}

		@Override
		public ByteOrder byteOrder() {
			return endianness;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("name", specname).add("size", type()).toString();
		}
	}
}