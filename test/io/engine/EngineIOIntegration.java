package io.engine;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EngineIOIntegration extends EngineIO {
	private static final String OPEN = "OPEN";
	private static final String CLOSE = "CLOSE";
	private static final String ERROR = "ERROR";
	private static final String DATA = "DATA";

	private final static String NODE = "node";
	private Process node;
	private BufferedReader stdout;
	private LinkedBlockingQueue<String> events = new LinkedBlockingQueue<String>();
	private final Thread SHUTDOWN_HOOK = new Thread() {
		@Override
		public void run() {
			try {
				node.destroy();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

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
		ProcessBuilder pBuilder = new ProcessBuilder(NODE,
				"./test/io/engine/engine.js", "" + getPort(),
				"polling,websocket");
		node = pBuilder.start();
		stdout = new BufferedReader(
				new InputStreamReader(node.getInputStream()));

		new Thread() {
			public void run() {
				BufferedReader err = new BufferedReader(new InputStreamReader(node.getErrorStream()));
				try {
					String l;
					while ((l = err.readLine()) != null) {
						System.err.println(l);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
		}.start();
		Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);
		assertEquals("Server is ready", "OK", pollServer());
		Thread.sleep(1000);
	}

	private String pollServer() throws IOException {
		String line = stdout.readLine();
		System.err.println("Getting: " + line);
		return line;
	}

	@After
	public void tearDown() throws Exception {
		node.destroy();
	}

	@Test
	public void testOpen() throws IOException {
		open();
		assertEquals("onOpen() should be called", OPEN, pollEvent());
		assertEquals("server should get a new connection", OPEN,
				pollServer());
		close();
		assertEquals("onClose() should be called", CLOSE, pollEvent());
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
