package org.eclipse.cbi.common.test.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class SampleFilesGenerators {

	private static final String LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
			+ "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
			+ "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris "
			+ "nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in "
			+ "reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla "
			+ "pariatur. Excepteur sint occaecat cupidatat non proident, sunt in "
			+ "culpa qui officia deserunt mollit anim id est laborum.";
	
	public static Path createLoremIpsumFile(Path path, int repeat) throws IOException {
		String loremIpsumRepeated = loremIpsumRepeated(repeat);
		return writeFile(path, loremIpsumRepeated);
	}

	public static Path writeFile(Path path, String contents) throws IOException {
		Path parent = path.normalize().getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}
		try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				PrintWriter writer = new PrintWriter(os, true)) {
			writer.print(contents);
		}
		return path;
	}
	
	public static String loremIpsumRepeated(int n) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < n; i++) {
			sb.append(LOREM_IPSUM).append('\n');
		}
		return sb.toString();
	}
	    
	public static byte[] readAllBytes(InputStream source) throws IOException {
		int bufferSize = 8192;
        byte[] buffer = new byte[bufferSize];
        int totalByteRead = 0;
        while(true) {
        	int byteRead;
            for (byteRead = source.read(buffer, totalByteRead, bufferSize - totalByteRead); 
            		byteRead > 0; 
            		byteRead = source.read(buffer, totalByteRead, bufferSize - totalByteRead)) {
                totalByteRead += byteRead;
            }

            int lastByte;
            if (byteRead >= 0) {
            	// we've read 'capacity - totalByteRead' byte, there may have more
            	lastByte = source.read();
            	if (lastByte >= 0) {
            		// there are more byte to read,
            		// increase buffer size
            		bufferSize = bufferSize << 1;
                    buffer = Arrays.copyOf(buffer, bufferSize);
                    buffer[totalByteRead] = (byte)lastByte;
                    totalByteRead++;
            	} else {
            		// lucky, the size of the input == capacity
            		break;
            	}
            } else {
            	// no more byte to read
            	break;
            }
        }
        
        return Arrays.copyOf(buffer, totalByteRead);
    }
}
