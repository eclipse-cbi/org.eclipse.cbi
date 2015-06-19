package org.eclipse.cbi.common.util;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.eclipse.cbi.common.util.Strings;
import org.junit.Test;

public class StringsTest {

	@Test
	public void testWithEmptyIterable() {
		assertEquals("", Strings.join("#", Arrays.<String>asList()));
	}
	
	@Test
	public void testWithSingletonIterable() {
		assertEquals("'test'", Strings.join("#", Arrays.asList("test")));
	}
	
	@Test
	public void testWithIterable() {
		assertEquals("'test1'#'test2'#'test3'", Strings.join("#", Arrays.asList("test1", "test2", "test3")));
	}
}
