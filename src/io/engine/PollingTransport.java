/*
 * socket.io-java-client XhrTransport.java
 *
 * Copyright (c) 2012, Enno Boland
 * socket.io-java-client is a implementation of the socket.io protocol in Java.
 * 
 * See LICENSE file for more information
 */
package io.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;

/**
 * The Class XhrTransport.
 */
class PollingTransport extends IOTransport implements Runnable {

	/** The String to identify this Transport. */
	public static final String NAME = "polling";

	private Thread httpThread = null;

	private HttpURLConnection getConnection = null;

	private Iterator<String> queue = null;

	@Override
	protected void open() throws Exception {
		if(httpThread != null)
			throw new RuntimeException("Internal Error!");
		httpThread = new Thread(this, NAME);
		httpThread.start();
		setConnected(true);
	}

	private URL getUrl() throws MalformedURLException {
		String protocol = isSecure() ? "https://" : "http://";
			return new URL(protocol + getHost() + ":" + getPort() + getPath()
					+ getQuery(this));
	}

	@Override
	protected void send(Iterator<String> data) throws Exception {
		queue = data;
		interruptConnection();
	}

	@Override
	public void run() {
		while (isDisconnecting() == false) {
			Thread.yield();
			try {
				setConnected(true);
				if (queue != null) {
					URLConnection post = getUrl().openConnection();
					post.setDoOutput(true);
					post.setDoInput(true);
					OutputStreamWriter output = new OutputStreamWriter(
							post.getOutputStream());
					while (queue.hasNext()) {
						String packet = queue.next();
						output.append(packet.length() + ":" + packet);
						queue.remove();
					}
					output.close();
					queue = null;
					InputStream input = post.getInputStream();
					while(input.read() != -1) {}
					input.close();
				} else {
					getConnection = (HttpURLConnection) getUrl().openConnection();
					stream(new InputStreamReader(getConnection.getInputStream()));
				}
			} catch (IOException e) {
				failed("HTTP Thread failed", e);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
				}
			} finally {
				getConnection = null;
				setConnected(false);
			}
		}
		httpThread = null;
	}

	@Override
	protected void close() {
		interruptConnection();
	}

	private void interruptConnection() {
		HttpURLConnection getConnection = this.getConnection;
		if (getConnection != null)
			getConnection.disconnect();
	}

	@Override
	public String getName() {
		return NAME;
	}
}
