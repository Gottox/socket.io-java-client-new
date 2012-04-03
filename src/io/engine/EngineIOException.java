package io.engine;

public class EngineIOException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1678173406434249282L;

	public EngineIOException(String message, Throwable throwable) {
		super(message, throwable);
	}

	public EngineIOException(String message) {
		super(message);
	}
}
