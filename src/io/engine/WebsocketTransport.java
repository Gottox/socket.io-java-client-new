/*
 * socket.io-java-client WebsocketTransport.java
 *
 * Copyright (c) 2012, Enno Boland
 * socket.io-java-client is a implementation of the socket.io protocol in Java.
 * 
 * See LICENSE file for more information
 */
package io.engine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import org.java_websocket.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * The Class WebsocketTransport.
 */
class WebsocketTransport extends IOTransport {

	private class Websocket extends WebSocketClient {
		public Websocket(URI serverURI) {
			super(serverURI);
		}

		@Override
		public void onOpen(ServerHandshake handshakedata) {
			setConnected(true);
		}

		@Override
		public void onMessage(String message) {
			packet(message);
		}

		@Override
		public void onClose(int code, String reason, boolean remote) {
			setConnected(false);
			if (isDisconnecting() == false) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
				init();
			}
		}

		@Override
		public void onError(Exception ex) {
			failed("Websocket called onError", ex);
			setConnected(false);
			websocket.close();
			if (isDisconnecting() == false) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
				init();
			}
		}
	}

	Websocket websocket;

	/** The String to identify this Transport */
	public static final String NAME = "websocket";

	URI uri;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void open() throws URISyntaxException {
		String protocol = isSecure() ? "wss://" : "ws://";
		uri = new URI(protocol + getHost() + ":" + getPort() + getPath()
				+ getQuery(this));
		init();
	}

	private void init() {
		try {
			websocket = new Websocket(uri);
			websocket.connect();
		} catch (Exception e) {
			failed("Error while init websocket", e);
		}
	}

	@Override
	public void send(Iterator<String> data) throws Exception {
		while (data.hasNext()) {
			websocket.send(data.next());
		}
	}

	@Override
	public void close() throws Exception {
		websocket.close();
	}
}
