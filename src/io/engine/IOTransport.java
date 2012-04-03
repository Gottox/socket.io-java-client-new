package io.engine;

public interface IOTransport {
	void open(EngineIO engine) throws Exception;
	void send(String data) throws Exception;
	void send(String[] data) throws Exception;
	void close() throws Exception;
	String getName();
	boolean canSendBulk();
}
