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

import org.eclipse.cbi.util.ProcessExecutor;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class DMGPackager {
	
	DMGPackager() {}
	
	public Path packageImageFile(Path appFolder, Path targetImageFile) throws IOException {
		ImmutableList<String> command = createCommand(appFolder, targetImageFile);
	
		final StringBuffer output = new StringBuffer();
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

	private ImmutableList<String> createCommand(Path appFolder, Path targetImageFile) {
		ImmutableList.Builder<String> command = ImmutableList.builder();
		command.add("./create-dmg/create-dmg");
		
		if (volumeName().isPresent())
			command.add("--volname", volumeName().get());
		if (windowPosition().isPresent())
			command.add("--window-pos", windowPosition().get());
		if (iconSize().isPresent())
			command.add("--icon-size", iconSize().get());
		if (icon().isPresent())
			command.add("--icon", icon().get());
		if (appDropLink().isPresent())
			command.add("--app-drop-link", appDropLink().get());
		if (windowPosition().isPresent())
			command.add("--window-pos", windowPosition().get());
		if (volumeIcon().isPresent())
			command.add("--volicon", volumeIcon().get().toString());
		if (backgroundImage().isPresent())
			command.add("--background", backgroundImage().get().toString());
		if (eula().isPresent())
			command.add("--eula", eula().get().toString());
		
		command.add(targetImageFile.toString());
		command.add(appFolder.toString());
		
		return command.build();
	}

	abstract ProcessExecutor processExecutor();
	abstract long timeout();
	
	abstract Optional<String> volumeName();
	abstract Optional<String> windowPosition();
	abstract Optional<String> windowSize();
	abstract Optional<String> iconSize();
	abstract Optional<String> icon();
	abstract Optional<String> appDropLink();
	abstract Optional<Path> volumeIcon();
	abstract Optional<Path> backgroundImage();
	abstract Optional<Path> eula();
	
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
		
		public abstract Builder volumeName(Optional<String> volumeName);
		public abstract Builder windowPosition(Optional<String> windowPosition);
		public abstract Builder windowSize(Optional<String> windowSize);
		public abstract Builder iconSize(Optional<String> iconSize);
		public abstract Builder icon(Optional<String> icon);
		public abstract Builder appDropLink(Optional<String> appDropLink);
		public abstract Builder volumeIcon(Optional<Path> volumeIcon);
		public abstract Builder backgroundImage(Optional<Path> backgroundImage);
		public abstract Builder eula(Optional<Path> eula);
		
		public abstract DMGPackager build();
	}
}
