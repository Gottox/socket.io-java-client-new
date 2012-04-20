package io.engine;

import static org.junit.Assert.*;

import java.util.LinkedList;

import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EngineIOTest extends EngineIO {
	private static final String OPEN = "open";
	private static final String CLOSE = "close";
	private static final String ERROR = "error";
	private static final String DATA = "data";
	TestTransport transport = new TestTransport("TEST");
	LinkedList<String> events = new LinkedList<String>();
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		this.transports(transport);
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 1000}");
		events.clear();
	}

	@After
	public void tearDown() throws Exception {
		assertEquals("Assert no left events", null, events.poll());
		assertEquals("transport should not send anything else", null, transport.output.poll());
	}

	@Test
	public void testOpenGarbaged() {
		transport.setConfiguration("garbage");
		this.open();
		assertEquals("garbage open packet should cause 'onError'", ERROR, events.poll());
	}
	
	@Test
	public void testOpenEmpty() {
		transport.setConfiguration("{}");
		this.open();
		assertEquals("garbage open packet should cause 'onError'", ERROR, events.poll());
	}

	@Test
	public void testOpenMissingPingTimeout() {
		transport.setConfiguration("{\"sid\":\"ASID\"}");
		this.open();
		assertEquals("garbage open packet should cause 'onError'", ERROR, events.poll());
	}

	@Test
	public synchronized void testPingTimeout() throws InterruptedException {
		pingTimeout(10);	// Setting pingTimeout to 10 ms before connecting (default is 10 sec)
		transport.setConfiguration(null); // tell TestTransport to skip sending configuration
		this.open();
		wait();
		assertEquals("timeout should cause onError()", ERROR, events.poll());
	}
	
	@Test
	public synchronized void testOpenPingTimeout() throws InterruptedException {
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 10}");
		this.open();
		assertEquals("Should call onOpen()", OPEN, events.poll());
		wait();
		assertEquals("garbage open packet should cause onError()", ERROR, events.poll());
	}
	
	@Test
	public void testClose() {
		this.open();
		assertEquals("Should call onOpen()", OPEN, events.poll());
		this.close();
		assertEquals("Should call onClose()", CLOSE, events.poll());
	}
	
	@Test
	public void testSend() {
		this.open();
		assertEquals("Should call onOpen()", OPEN, events.poll());
		transport.allowSend(true);
		send(DATA);
		assertEquals("Transport should send", "4"+DATA, transport.output.poll());
		this.close();
		assertEquals("Transport should send close packet", "1", transport.output.poll());
		assertEquals("Should call onClose()", CLOSE, events.poll());
	}
	
	@Test
	public void testUpgrade() {
		TestTransport upgradeTransport = new TestTransport("upgrade");
		transport.setConfiguration("{\"sid\":\"ASID\", pingTimeout: 100000, \"upgrades\": [ \"upgrade\" ]}");
		transports(transport, upgradeTransport);
		this.open();
		upgradeTransport.allowSend(true);
		transport.allowSend(true);
		assertEquals("Should call onOpen()", OPEN, events.poll());
		assertEquals("Upgrading transport should receive a ping probe", "2probe", upgradeTransport.output.poll());
		send(DATA);
		assertEquals("Transport should send", "4"+DATA, transport.output.poll());
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
