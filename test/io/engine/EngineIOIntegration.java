package io.engine;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EngineIOIntegration extends EngineIOBaseTest {
	private final static String NODE = "node";
	private Process node;
	private BufferedReader stdout;
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

	protected String pollServer() throws IOException {
		String line = stdout.readLine();
		LOGGER.info("Server: " + line);
		return line;
	}

	@After
	public void tearDown() throws Exception {
		node.destroy();
		node.waitFor();
		super.tearDown();
	}

}
