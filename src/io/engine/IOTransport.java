package io.engine;

import java.io.Reader;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class IOTransport {
	private EngineIO engine;
	private boolean connected;
	private boolean disconnecting = false;
	private ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<String>();

	abstract String getName();

	protected abstract void open() throws Exception;

	protected abstract void send(Iterator<String> data) throws Exception;

	protected abstract void close() throws Exception;

	protected void failed(String message, Exception exception) {
		engine.transportFailed(this, message, exception);
	}

	protected void packet(String data) {
		engine.transportPacket(this, data);
	}

	final protected void stream(Reader reader) {
		try {
			int c;
			while ((c = reader.read()) > 0) {
				StringBuilder sizeBuilder = new StringBuilder(4);
				do {
					sizeBuilder.append((char) c);
				} while ((c = reader.read()) > 0 && ((char) c) != ':');

				int left = Integer.parseInt(sizeBuilder.toString());
				char[] buffer = new char[left];
				while (left > 0)
					left -= reader.read(buffer, buffer.length - left, left);
				packet(new String(buffer));
			}
		} catch (Exception e) {
			failed("Garbaged Payload.", e);
		}
	}

	final protected boolean isConnected() {
		return connected;
	}

	final protected void setConnected(boolean connected) {
		this.connected = connected;
		flush();
	}

	final public synchronized void flush() {
		if (connected && buffer.isEmpty() == false) {
			try {
				send(buffer.iterator());
			} catch (Exception e) {
				failed("Flushing buffer failed", e);
			}
		}
	}

	final void bufferedSend(String data) throws Exception {
		buffer.add(data);
		flush();
	}

	final void reqOpen(EngineIO engine) {
		disconnecting = false;
		this.engine = engine;
		try {
			open();
		} catch (Exception e) {
			failed("open has failed", e);
		}
	}

	final void reqClose() {
		disconnecting = true;
		try {
			close();
		} catch (Exception e) {
			failed("close has failed", e);
		}
	}

	protected boolean isDisconnecting() {
		return disconnecting;
	}
	
	protected boolean isSecure() {
		return engine.isSecure();
	}
	
	protected String getHost() {
		return engine.getHost();
	}
	
	protected int getPort() {
		return engine.getPort();
	}
	
	protected String getPath() {
		return engine.getBasePath() + engine.getPath();
	}
	
	protected String getQuery() {
		return engine.genQuery();
	}
}
