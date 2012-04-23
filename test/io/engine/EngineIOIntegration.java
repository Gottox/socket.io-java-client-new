package io.engine;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EngineIOIntegration extends EngineIO {
	private static final String OPEN = "open";
	private static final String CLOSE = "close";
	private static final String ERROR = "error";
	private static final String DATA = "data";
	
	private final static String NODE = "node";
	private Process node;
	private BufferedReader stdout;
	private LinkedBlockingQueue<String> events = new LinkedBlockingQueue<String>();

	private String pollEvent() {
		try {
			return events.poll(1000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			return null;
		}
	}
	
	@Before
	public void setUp() throws Exception {
		port((int) (Math.random() * 32000) + 1200);
		node = Runtime.getRuntime().exec(
				new String[] { NODE, "./test/io/engine/engine.js",
						"" + getPort() });
		node.getErrorStream().close();
		stdout = new BufferedReader(
				new InputStreamReader(node.getInputStream()));
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					node.destroy();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		assertEquals("Server is ready", "OK", stdout.readLine());
	}

	@After
	public void tearDown() throws Exception {
		node.destroy();
	}

	@Test
	public void testOpen() throws IOException {
		open();
		assertEquals("onOpen() should be called", OPEN, pollEvent());
		assertEquals("server should get a new connection", OPEN, stdout.readLine());
	}

	@Override
	public void onOpen() {
		events.offer(OPEN);
	}

	@Override
	public void onMessage(String message) {
		events.offer(message);
	}

	@Override
	public void onClose() {
		events.offer(CLOSE);
	}

	@Override
	public void onError(EngineIOException exception) {
		events.offer(ERROR);
	}


}
