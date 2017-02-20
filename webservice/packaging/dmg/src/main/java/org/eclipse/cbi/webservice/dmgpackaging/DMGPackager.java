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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.cbi.webservice.util.ProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class DMGPackager {
	
	private static final Logger logger = LoggerFactory.getLogger(DMGPackager.class);
	
	DMGPackager() {}
	
	public Path packageImageFile(Path appFolder, Path targetImageFile) throws IOException {
		return packageImageFile(appFolder, targetImageFile, Options.builder().build());
	}
	
	public Path packageImageFile(Path appFolder, Path targetImageFile, Options options) throws IOException {
		ImmutableList<String> command = createCommand(appFolder, targetImageFile, options);
	
		logger.info("The following 'create-dmg' command will be executed: '" + Joiner.on(' ').join(command) + "'");
		
		final StringBuilder output = new StringBuilder();
		int createImageFileExitValue = processExecutor().exec(command, output , timeout(), TimeUnit.SECONDS);
		if (createImageFileExitValue != 0) {
			throw new IOException(Joiner.on('\n').join(
					"The 'create-dmg' command exited with value '" + createImageFileExitValue + "'",
					"'create-dmg' output:",
					output));
		} else {
			return targetImageFile;
		}
	}

	private ImmutableList<String> createCommand(Path appFolder, Path targetImageFile, Options options) {
		ImmutableList.Builder<String> command = ImmutableList.builder();
		command.add("./create-dmg/create-dmg");
		
		Splitter splitter = Splitter.on(' ').trimResults().omitEmptyStrings();
		if (options.volumeName().isPresent())
			command.add("--volname", options.volumeName().get());
		if (options.windowPosition().isPresent())
			command.add("--window-pos").addAll(splitter.split(options.windowPosition().get()));
		if (options.windowSize().isPresent())
			command.add("--window-size").addAll(splitter.split(options.windowSize().get()));
		if (options.iconSize().isPresent())
			command.add("--icon-size", options.iconSize().get());
		if (options.icon().isPresent())
			command.add("--icon").addAll(splitter.split(options.icon().get()));
		if (options.appDropLink().isPresent())
			command.add("--app-drop-link").addAll(splitter.split(options.appDropLink().get()));
		if (options.volumeIcon().isPresent())
			command.add("--volicon", options.volumeIcon().get().toString());
		if (options.backgroundImage().isPresent())
			command.add("--background", options.backgroundImage().get().toString());
		if (options.eula().isPresent())
			command.add("--eula", options.eula().get().toString());
		
		command.add(targetImageFile.toString());
		command.add(appFolder.toString());
		
		return command.build();
	}

	abstract ProcessExecutor processExecutor();
	abstract long timeout();
	
	public static Builder builder(ProcessExecutor executor) {
		return new AutoValue_DMGPackager.Builder()
			.processExecutor(executor)
			.timeout(TimeUnit.MINUTES.toSeconds(3));
	}
	
	@AutoValue.Builder
	public abstract static class Builder {
		Builder() {}
		abstract Builder processExecutor(ProcessExecutor executor);
		public abstract Builder timeout(long timeout);

		public abstract DMGPackager build();
	}
	

	@AutoValue
	public static abstract class Options {
		
		Options() {}
		
		abstract Optional<String> volumeName();
		abstract Optional<String> windowPosition();
		abstract Optional<String> windowSize();
		abstract Optional<String> iconSize();
		abstract Optional<String> icon();
		abstract Optional<String> appDropLink();
		abstract Optional<Path> volumeIcon();
		abstract Optional<Path> backgroundImage();
		abstract Optional<Path> eula();
		
		public static Builder builder() {
			return new AutoValue_DMGPackager_Options.Builder();
		}
		
		@AutoValue.Builder
		public abstract static class Builder {
			Builder() {}
			
			public abstract Builder volumeName(Optional<String> volumeName);
			public abstract Builder windowPosition(Optional<String> windowPosition);
			public abstract Builder windowSize(Optional<String> windowSize);
			public abstract Builder iconSize(Optional<String> iconSize);
			public abstract Builder icon(Optional<String> icon);
			public abstract Builder appDropLink(Optional<String> appDropLink);
			public abstract Builder volumeIcon(Optional<Path> volumeIcon);
			public abstract Builder backgroundImage(Optional<Path> backgroundImage);
			public abstract Builder eula(Optional<Path> eula);
			
			public abstract Options build();
		}
	}
}
