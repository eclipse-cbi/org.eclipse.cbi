/*******************************************************************************
 * Copyright (c) 2016 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mikael Barbero - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.common.record;

import java.nio.charset.Charset;

import org.eclipse.cbi.common.record.RecordDefinition.Field;

import com.google.common.collect.ForwardingObject;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

public interface Record {

	int uint16Value(RecordDefinition.Field field);

	UnsignedInteger uint32Value(RecordDefinition.Field field);

	UnsignedLong uint64Value(Field field);
	
	String stringValue(Field field, Charset charset);
	
	long size();

	abstract class Fowarding extends ForwardingObject implements Record {

		private final Record delegate;

		protected Fowarding(Record delegate) {
			this.delegate = delegate;
		}
		
		@Override
		protected Record delegate() {
			return delegate;
		}
		
		@Override
		public int uint16Value(Field field) {
			return delegate().uint16Value(field);
		}

		@Override
		public UnsignedInteger uint32Value(Field field) {
			return delegate().uint32Value(field);
		}

		@Override
		public UnsignedLong uint64Value(Field field) {
			return delegate().uint64Value(field);
		}
		
		@Override
		public String stringValue(Field field, Charset charset) {
			return delegate().stringValue(field, charset);
		}

		@Override
		public long size() {
			return delegate().size();
		}
	}
}