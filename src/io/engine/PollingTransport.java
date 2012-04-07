/*
 * socket.io-java-client XhrTransport.java
 *
 * Copyright (c) 2012, Enno Boland
 * socket.io-java-client is a implementation of the socket.io protocol in Java.
 * 
 * See LICENSE file for more information
 */
package io.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * The Class XhrTransport.
 */
class PollingTransport implements IOTransport, Runnable {

	/** The String to identify this Transport. */
	public static final String NAME = "polling";

	private static final Logger LOGGER = Logger.getLogger("engine.io polling");

	/** The connection. */
	private EngineIO engine;

	private Thread pollthread = null;

	private HttpURLConnection urlConnection;

	public PollingTransport() {
	}

	@Override
	public void init(EngineIO engine) throws Exception {
		this.engine = engine;
	}

	public void open() throws Exception {
		urlConnection = (HttpURLConnection) genURL().openConnection();
		pollthread = new Thread(this, "PollingTransport");
		pollthread.start();
	}

	@Override
	public synchronized void run() {
		try {
			InputStreamReader input = new InputStreamReader(
					urlConnection.getInputStream());
			engine.transportOpen(this);
			engine.transportPayload(this, input);
			engine.transportClose(this);
		} catch (IOException e) {
			engine.transportFailed(this, "Error while initialising connection",
					e);
		}
	}

	private URL genURL() throws MalformedURLException {
		String protocol = engine.isSecure() ? "https://" : "http://";
		return new URL(protocol + engine.getHost() + ":" + engine.getPort()
				+ engine.getBasePath() + engine.getPath() + engine.genQuery());
	}

	@Override
	public void send(String data) throws Exception {
		throw new RuntimeException(
				"Cannot send simple messages. Internal error."
						+ "Please report this at https://github.com/Gottox/socket.io-java-client/issues");
	}

	@Override
	public void send(String[] data) throws Exception {
		urlConnection.disconnect();
		synchronized (this) {
			HttpURLConnection post = (HttpURLConnection) genURL()
					.openConnection();
			post.setDoOutput(true);
			OutputStreamWriter output = new OutputStreamWriter(
					post.getOutputStream());
			for (String packet : data) {
				output.append(packet.length() + ":" + packet);
			}
			output.close();

			BufferedReader input = new BufferedReader(new InputStreamReader(
					post.getInputStream()));
			String line;
			while ((line = input.readLine()) != null)
				LOGGER.info("response: " + line);
			input.close();
		}
	}

	@Override
	public void close() throws Exception {
		urlConnection.disconnect();
		engine.transportClose(this);
	}

	@Override
	public String getTransportName() {
		return NAME;
	}

	@Override
	public boolean canSendBulk() {
		return true;
	}
}
