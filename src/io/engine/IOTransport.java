package io.engine;

public interface IOTransport {
	void init(EngineIO engine) throws Exception;
	void open() throws Exception;
	void send(String data) throws Exception;
	void send(String[] data) throws Exception;
	void close() throws Exception;
	String getTransportName();
	boolean canSendBulk();
}
