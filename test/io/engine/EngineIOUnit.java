package io.engine;

import static org.junit.Assert.*;

import java.util.LinkedList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EngineIOUnit extends EngineIOBaseTest {
	private static final String OPEN = "OPEN";
	private static final String CLOSE = "CLOSE";
	private static final String ERROR = "ERROR";
	private static final String DATA = "DATA";
	TestTransport transport = new TestTransport("TEST");

	protected String pollServer() {
		return transport.output.poll();
	}
	
	@Override
	public void send(String data) {
		transport.allowSend(true);
		super.send(data);
	}
	

	@Before
	public void setUp() throws Exception {
		this.transports(transport);
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 5000}");
	}

	@Test
	public void testOpenGarbaged() {
		transport.setConfiguration("garbage");
		this.open();
		assertEquals("Server should have a new connection", OPEN, pollServer());
		assertEquals("garbage open packet should cause 'onError'", ERROR, pollEvent());
	}

	@Test
	public void testOpenEmpty() {
		transport.setConfiguration("{}");
		this.open();
		assertEquals("Server should receive open", OPEN, pollServer());
		assertEquals("garbage open packet should cause 'onError'", ERROR, pollEvent());
	}

	@Test
	public void testOpenMissingPingTimeout() {
		transport.setConfiguration("{\"sid\":\"ASID\"}");
		this.open();
		assertEquals("Server should receive open", OPEN, pollServer());
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
	public void testSend() {
		open();
		assertEquals("Should call onOpen()", OPEN, pollEvent());
		assertEquals("Server should have a new connection", OPEN, pollServer());
		send(DATA);
		assertEquals("Transport should send", DATA, pollServer());
		assertEquals("Should call onMessage()", DATA, pollEvent());
		this.close();
		assertEquals("Transport should send close packet", CLOSE, pollServer());
		assertEquals("Should call onClose()", CLOSE, pollEvent());
	}

	@Test
	public void testUpgrade() {
		TestTransport upgradeTransport = new TestTransport("upgrade");
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 100000, \"upgrades\": [ \"upgrade\" ]}");
		transports(transport, upgradeTransport);
		this.open();
		upgradeTransport.allowSend(true);
		assertEquals("Should call onOpen()", OPEN, pollEvent());
		assertEquals("Server should have a new connection", OPEN, pollServer());
		assertEquals("Upgrading transport should receive a ping probe", PING, upgradeTransport.output.poll());
		send(DATA);
		assertEquals("Transport should send", DATA, pollServer());
		assertEquals("Should call onMessage()", DATA, pollEvent());
		upgradeTransport.inject("3probe");
		assertEquals("Upgrading transport should send upgrade packet", UPGRADE, upgradeTransport.output.poll());
		send(DATA);
		assertEquals("Upgrading transport should handle messages now", DATA, upgradeTransport.output.poll());
		assertEquals("transport should not send anything else", null, pollServer());
		assertEquals("Should call onMessage()", DATA, pollEvent());
	}

}
