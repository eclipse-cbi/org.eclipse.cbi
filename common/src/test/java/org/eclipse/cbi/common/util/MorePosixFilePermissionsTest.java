package org.eclipse.cbi.common.util;

import static org.junit.Assert.assertEquals;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.EnumSet;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class MorePosixFilePermissionsTest {

	@Test
	public void test777() {
		testFromAndToOctal("777", EnumSet.allOf(PosixFilePermission.class));
	}
	
	@Test
	public void test444() {
		testFromAndToOctal("444", PosixFilePermission.OWNER_READ, PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_READ);
	}
	
	@Test
	public void test644() {
		testFromAndToOctal("644", PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_READ);
	}
	
	@Test
	public void test645() {
		testFromAndToOctal("645", PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_EXECUTE);
	}
	
	@Test
	public void test640() {
		testFromAndToOctal("640", PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ);
	}
	
	@Test
	public void test000() {
		testFromAndToOctal("0", EnumSet.noneOf(PosixFilePermission.class));
	}
	
	@Test
	public void test004() {
		testFromAndToOctal("4", PosixFilePermission.OTHERS_READ);
	}
	
	@Test
	public void test064() {
		testFromAndToOctal("64", PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_READ);
	}
	
	private static String toOctalString(Collection<PosixFilePermission> filePermissions) {
		return Long.toOctalString(MorePosixFilePermissions.toFileMode(EnumSet.copyOf(filePermissions)));
	}
	
	private static void testFromAndToOctal(String octalString, PosixFilePermission... filePermissions) {
		testFromAndToOctal(octalString, ImmutableSet.copyOf(filePermissions));
	}
	
	private static void testFromAndToOctal(String octalString, Collection<PosixFilePermission> filePermissions) {
		assertEquals(octalString, toOctalString(filePermissions));
		assertEquals(filePermissions, MorePosixFilePermissions.fromFileMode(Integer.parseInt(octalString, 8)));
	}
}
