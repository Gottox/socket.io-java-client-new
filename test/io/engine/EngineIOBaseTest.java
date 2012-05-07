package io.engine;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Test;

public abstract class EngineIOBaseTest extends EngineIO {
	public static final String OPEN = "OPEN";
	public static final String CLOSE = "CLOSE";
	public static final String ERROR = "ERROR";
	public static final String PING = "PING";
	public static final String UPGRADE = "UPGRADE";
	public static final String DATA = "DATA";
	public static final Logger LOGGER = Logger.getLogger("EngineIOBaseTest");
	
	private LinkedBlockingQueue<String> events = new LinkedBlockingQueue<String>();
	
	@Override
	public void onOpen() {
		gotEvent(OPEN);
	}

	@Override
	public void onMessage(String message) {
		gotEvent(message);
	}

	@Override
	public void onClose() {
		gotEvent(CLOSE);
	}

	@Override
	public void onError(EngineIOException exception) {
		gotEvent(ERROR);
	}

	private void gotEvent(String event) {
		events.offer(event);
		LOGGER.info("Client: " + event);
	}
	
	final protected String pollEvent() {
		try {
			return events.poll(1000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			return null;
		}
	}
	
	@Test
	public void testOpenClose() throws Exception {
		this.open();
		assertEquals("Server should got open", OPEN, pollServer());
		assertEquals("Should call onOpen()", OPEN, pollEvent());
		this.close();
		assertEquals("Should call onClose()", CLOSE, pollEvent());
		assertEquals("Server should got open", CLOSE, pollServer());
	}
	
	protected abstract String pollServer() throws Exception;
	
	@After
	public void tearDown() throws Exception {
		assertEquals("Assert no left events", null, this.events.poll());
		assertEquals("transport should not send anything else", null, pollServer());
	}
}
