package io.engine;

import static org.junit.Assert.*;

import java.util.LinkedList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EngineIOUnit extends EngineIO {
	private static final String OPEN = "open";
	private static final String CLOSE = "close";
	private static final String ERROR = "error";
	private static final String DATA = "data";
	TestTransport transport = new TestTransport("TEST");
	LinkedList<String> events = new LinkedList<String>();
	
	private String pollEvent() {
		return events.poll();
	}

	private String pollServer() {
		return transport.output.poll();
	}

	@Before
	public void setUp() throws Exception {
		this.transports(transport);
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 5000}");
		events.clear();
	}

	@After
	public void tearDown() throws Exception {
		assertEquals("Assert no left events", null, pollEvent());
		assertEquals("transport should not send anything else", null, pollServer());
	}

	@Test
	public void testOpenGarbaged() {
		transport.setConfiguration("garbage");
		this.open();
		assertEquals("garbage open packet should cause 'onError'", ERROR, pollEvent());
	}

	@Test
	public void testOpenEmpty() {
		transport.setConfiguration("{}");
		this.open();
		assertEquals("garbage open packet should cause 'onError'", ERROR, pollEvent());
	}

	@Test
	public void testOpenMissingPingTimeout() {
		transport.setConfiguration("{\"sid\":\"ASID\"}");
		this.open();
		assertEquals("garbage open packet should cause 'onError'", ERROR, pollEvent());
	}

	@Test
	public synchronized void testPingTimeout() throws InterruptedException {
		pingTimeout(10);	// Setting pingTimeout to 10 ms before connecting (default is 10 sec)
		transport.setConfiguration(null); // tell TestTransport to skip sending configuration
		this.open();
		wait();
		assertEquals("timeout should cause onError()", ERROR, pollEvent());
	}
	
	@Test
	public synchronized void testOpenPingTimeout() throws InterruptedException {
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 10}");
		this.open();
		assertEquals("Should call onOpen()", OPEN, pollEvent());
		wait();
		assertEquals("garbage open packet should cause onError()", ERROR, pollEvent());
	}
	
	@Test
	public void testClose() {
		this.open();
		assertEquals("Should call onOpen()", OPEN, pollEvent());
		this.close();
		assertEquals("Should call onClose()", CLOSE, pollEvent());
	}
	
	@Test
	public void testSend() {
		this.open();
		assertEquals("Should call onOpen()", OPEN, pollEvent());
		transport.allowSend(true);
		send(DATA);
		assertEquals("Transport should send", "4"+DATA, pollServer());
		this.close();
		assertEquals("Transport should send close packet", "1", pollServer());
		assertEquals("Should call onClose()", CLOSE, pollEvent());
	}

	@Test
	public void testUpgrade() {
		TestTransport upgradeTransport = new TestTransport("upgrade");
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 100000, \"upgrades\": [ \"upgrade\" ]}");
		transports(transport, upgradeTransport);
		this.open();
		upgradeTransport.allowSend(true);
		transport.allowSend(true);
		assertEquals("Should call onOpen()", OPEN, pollEvent());
		assertEquals("Upgrading transport should receive a ping probe", "2probe", upgradeTransport.output.poll());
		send(DATA);
		assertEquals("Transport should send", "4"+DATA, pollServer());
		upgradeTransport.inject("3probe");
		assertEquals("Upgrading transport should send upgrade packet", "5", upgradeTransport.output.poll());
		send(DATA);
		assertEquals("Upgrading transport should handle messages now", "4"+DATA, upgradeTransport.output.poll());
		assertEquals("transport should not send anything else", null, upgradeTransport.output.poll());
	}

	@Override
	public synchronized void onOpen() {
		events.add(OPEN);
		notify();
	}

	@Override
	public synchronized void onMessage(String message) {
		events.add(message);
		notify();
	}

	@Override
	public synchronized void onClose() {
		events.add(CLOSE);
		notify();
	}
	
	@Override
	public synchronized void onError(EngineIOException exception) {
		new Exception(exception).printStackTrace();
		events.add(ERROR);
		notify();
	}
}
