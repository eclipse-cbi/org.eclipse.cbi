/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat, Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mat Booth (Red Hat) - initial implementation
 *******************************************************************************/
package org.eclipse.cbi.maven.plugins.flatpakager.model;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@AutoValue
public abstract class Source {
	public static final int DEFAULT_STRIP_COMPONENTS = 1;

	// Suppressing FindBugs warnings on this class because Jackson's unconventional
	// use of equals() here. A) There are no non-static fields in this class,
	// implementing hashcode would be pointless. B) We are not comparing instances
	// of this class, equals() is being passed an instance of whatever type the
	// field using this filter is.
	@SuppressFBWarnings
	protected final static class StripComponentFilter {
		@Override
		public boolean equals(Object obj) {
			return Integer.valueOf(DEFAULT_STRIP_COMPONENTS).equals(obj);
		}
	}

	@JsonProperty("type")
	public abstract String type();

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("path")
	@Nullable
	public abstract String path();

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("url")
	@Nullable
	public abstract String url();

	@JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = StripComponentFilter.class)
	@JsonProperty("strip-components")
	public abstract int stripComponents();

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("dest-filename")
	@Nullable
	public abstract String destFilename();

	public static Builder builder() {
		return new AutoValue_Source.Builder().stripComponents(DEFAULT_STRIP_COMPONENTS);
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder type(String value);

		public abstract Builder path(String value);

		public abstract Builder url(String value);

		public abstract Builder stripComponents(int value);

		public abstract Builder destFilename(String value);

		public abstract Source build();
	}
}
