package org.eclipse.cbi.maven.common.test.util;

import org.eclipse.jetty.util.log.Logger;

public class NullJettyLogger implements Logger {

	@Override
	public String getName() {
		return NullJettyLogger.class.getName();
	}

	@Override
	public void warn(String msg, Object... args) {
		// TODO Auto-generated method stub

	}

	@Override
	public void warn(Throwable thrown) {
		// TODO Auto-generated method stub

	}

	@Override
	public void warn(String msg, Throwable thrown) {
		// TODO Auto-generated method stub

	}

	@Override
	public void info(String msg, Object... args) {
		// TODO Auto-generated method stub

	}

	@Override
	public void info(Throwable thrown) {
		// TODO Auto-generated method stub

	}

	@Override
	public void info(String msg, Throwable thrown) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isDebugEnabled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setDebugEnabled(boolean enabled) {
		// TODO Auto-generated method stub

	}

	@Override
	public void debug(String msg, Object... args) {
		// TODO Auto-generated method stub

	}

	@Override
	public void debug(Throwable thrown) {
		// TODO Auto-generated method stub

	}

	@Override
	public void debug(String msg, Throwable thrown) {
		// TODO Auto-generated method stub

	}

	@Override
	public void ignore(Throwable ignored) {
		// TODO Auto-generated method stub

	}

	@Override
	public void debug(String msg, long value) {
		// TODO Auto-generated method stub

	}

	@Override
	public Logger getLogger(String name) {
		return this;
	}

}
