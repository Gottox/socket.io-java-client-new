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

import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;

/**
 * The Class WebsocketTransport.
 */
class WebsocketTransport implements IOTransport, WebSocketEventHandler {

	WebSocketConnection websocket;

	/** The String to identify this Transport */
	public static final String NAME = "websocket";

	/** The EngineIO of this transport. */
	private EngineIO engine;

	@Override
	public void init(EngineIO engine) throws WebSocketException,
			URISyntaxException {
		this.engine = engine;
	}
	
	public void open() throws Exception {
		websocket = new WebSocketConnection(genURI());
		websocket.setEventHandler(this);
		websocket.connect();
	}

	private URI genURI() throws URISyntaxException {
		String protocol = engine.isSecure() ? "wss://" : "ws://";
		return new URI(protocol + engine.getHost() + ":" + engine.getPort()
				+ engine.getBasePath() + engine.getPath() + engine.genQuery());
	}

	@Override
	public String getTransportName() {
		return NAME;
	}

	@Override
	public void onClose() {
		engine.transportClose(this);
	}

	@Override
	public void onMessage(WebSocketMessage message) {
		engine.transportPacket(this, message.getText());
	}

	@Override
	public void onOpen() {
		engine.transportOpen(this);
	}

	@Override
	public void send(String data) throws WebSocketException {
		websocket.send(data);
	}

	@Override
	public void send(String[] data) {
		throw new RuntimeException(
				"Cannot send bulk messages. Internal error."
						+ "Please report this at https://github.com/Gottox/socket.io-java-client/issues");
	}

	@Override
	public boolean canSendBulk() {
		return false;
	}

	@Override
	public void close() throws WebSocketException {
		websocket.close();
	}
}
