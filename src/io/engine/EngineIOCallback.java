package io.engine;

public interface EngineIOCallback {
	void onOpen();
	void onMessage(String message);
	void onClose();
	void onError(EngineIOException exceptiopn);
}
