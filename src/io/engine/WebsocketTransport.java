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

import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;

/**
 * The Class WebsocketTransport.
 */
class WebsocketTransport extends IOTransport implements WebSocketEventHandler {

	WebSocketConnection websocket;

	/** The String to identify this Transport */
	public static final String NAME = "websocket";

	URI uri;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void open() throws WebSocketException, URISyntaxException {
		String protocol = isSecure() ? "wss://" : "ws://";
		uri = new URI(protocol + getHost() + ":" + getPort() + getPath()
				+ getQuery());
		init();
	}

	private void init() {
		try {
			websocket = new WebSocketConnection(uri);
			websocket.setEventHandler(this);
			websocket.connect();
		} catch (WebSocketException e) {
			failed("Error while init websocket", e);
		}
	}

	@Override
	public void onOpen() {
		setConnected(true);
	}

	@Override
	public void send(Iterator<String> data) throws Exception {
		while (data.hasNext()) {
			websocket.send(data.next());
		}
	}

	@Override
	public void onMessage(WebSocketMessage message) {
		packet(message.getText());
	}

	@Override
	public void close() throws Exception {
		websocket.close();
	}

	@Override
	public void onClose() {
		setConnected(false);
		if (isDisconnecting() == false)
			init();
	}
}
