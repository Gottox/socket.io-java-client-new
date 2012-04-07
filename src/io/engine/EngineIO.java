/*
 * engine.io EngineIO.java
 *
 * Copyright (c) 2012, Enno Boland
 * Engine.io client
 * 
 * See LICENSE file for more information
 */
package io.engine;

import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EngineIO implements EngineIOCallback {
	final private int STATE_INIT = 0;
	final private int STATE_CONNECTING = 1;
	final private int STATE_CONNECTED = 2;
	final private int STATE_UPGRADING = 3;
	final private int STATE_INTERUPTED = 4;
	final private int STATE_INVALID = 5;
	final private char TYPE_OPEN = '0';
	final private char TYPE_CLOSE = '1';
	final private char TYPE_PING = '2';
	final private char TYPE_PONG = '3';
	final private char TYPE_MESSAGE = '4';
	final private char TYPE_UPGRADE = '5';
	final private String PROBE = "probe";
	final private Logger logger = Logger.getLogger("engine.io");
	private int state = STATE_INIT;

	private String host = "localhost";
	private int port = 80;
	private String resource = "default";
	private ConcurrentHashMap<String, String> query = null;
	private boolean secure = false;
	private String basePath = "/engine.io";
	private boolean upgrade = true;
	private String[] transports = new String[] { "polling", "websocket" };
	private EngineIOCallback callback = this;
	private String uid;
	private String sid;
	private int pingTimeout;
	private LinkedList<String> output = new LinkedList<String>();

	private IOTransport currentTransport = null;
	private IOTransport upgradingTransport = null;

	public EngineIO host(String host) {
		this.host = host;
		return this;
	}

	public EngineIO port(int port) {
		this.port = port;
		return this;
	}

	public EngineIO path(String path) {
		this.resource = path;
		return this;
	}

	public EngineIO query(Map<String, String> query) {
		this.query = new ConcurrentHashMap<String, String>(query);
		return this;
	}

	public EngineIO secure(boolean secure) {
		this.secure = secure;
		return this;
	}

	public EngineIO basePath(String basePath) {
		this.basePath = basePath;
		return this;
	}

	public EngineIO upgrade(boolean upgrade) {
		this.upgrade = upgrade;
		return this;
	}

	public EngineIO transports(String[] transports) {
		this.transports = transports;
		return this;
	}

	public EngineIO callback(EngineIOCallback callback) {
		this.callback = callback;
		return this;
	}

	public String getUid() {
		return uid;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getPath() {
		return resource;
	}

	public Map<String, String> getQuery() {
		return query;
	}

	public boolean isSecure() {
		return secure;
	}

	public String getBasePath() {
		return basePath;
	}

	public boolean isUpgrade() {
		return upgrade;
	}

	public String[] getTransports() {
		return transports;
	}

	public EngineIOCallback getCallback() {
		return callback;
	}

	public String getCurrentTransport() {
		return currentTransport == null ? transports[0] : currentTransport
				.getTransportName();
	}

	public String getSid() {
		return sid;
	}

	public int getPingTimeout() {
		return pingTimeout;
	}

	public EngineIO open() {
		this.uid = ("" + Math.random()).substring(5)
				+ ("" + Math.random()).substring(5);
		if (currentTransport != null)
			throw new RuntimeException(
					"Dublicated open call. Please call open only once!");
		currentTransport = instanceTransport(transports);
		if (currentTransport == null)
			throw new RuntimeException(
					"No Transports to connect available. If you're implementing your own transport "
							+ "make sure you overwrite instanceTransport(String) "
							+ "to instanciate your custom EngineIOTransport.");
		try {
			currentTransport.init(this);
			currentTransport.open();
			setState(STATE_CONNECTING);
		} catch (Exception e) {
			callback.onError(new EngineIOException(
					"Error while opening connection", e));
		}
		return this;
	}

	protected IOTransport instanceTransport(String[] transports) {
		for (String transport : transports) {
			if (WebsocketTransport.NAME.equals(transport))
				return new WebsocketTransport();
			else if (PollingTransport.NAME.equals(transport))
				return new PollingTransport();
		}
		return null;
	}

	String genQuery() {
		try {
			StringBuilder builder = new StringBuilder("?uid=").append(getUid())
					.append("&transport=").append(getCurrentTransport());
			String sid = getSid();
			if (sid != null) {
				builder.append("&sid=").append(URLEncoder.encode(sid, "UTF-8"));
			}
			if (query != null) {
				for (Entry<String, String> entry : query.entrySet()) {
					builder.append('&')
							.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
							.append('=')
							.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
				}
			}
			return builder.toString();
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(
					"Unsupported Encoding. Internal error."
							+ "Please report this at https://github.com/Gottox/socket.io-java-client/issues",
					e);
		}
	}

	void transportOpen(IOTransport transport) {
		if (getState() == STATE_INTERUPTED) {
			if (flush())
				setState(STATE_CONNECTED);
		}
	}

	private synchronized boolean flush() {
		if (currentTransport.canSendBulk()) {
			String[] data = output.toArray(new String[output.size()]);
			try {
				currentTransport.send(data);
				output.clear();
			} catch (Exception e) {
				transportFailed(currentTransport, "Error while flushing", e);
				return false;
			}
		} else {
			String packet;
			while ((packet = output.peek()) != null) {
				try {
					currentTransport.send(packet);
				} catch (Exception e) {
					output.addFirst(packet);
					return false;
				}
			}
		}
		return true;
	}

	void transportPayload(IOTransport transport, Reader reader) {
		try {
			int c;
			while ((c = reader.read()) > 0) {
				StringBuilder sizeBuilder = new StringBuilder(4);
				do {
					sizeBuilder.append((char) c);
				} while ((c = reader.read()) > 0 && ((char) c) != ':');

				int left = Integer.parseInt(sizeBuilder.toString());
				char[] buffer = new char[left];
				while (left > 0)
					left -= reader.read(buffer, buffer.length - left, left);
				transportPacket(transport, new String(buffer));
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Garbaged Payload. Ignoring...", e);
		}
	}

	void transportPacket(IOTransport transport, String data) {
		logger.info("< " + data);
		try {
			char type = data.charAt(0);
			CharSequence message = data.subSequence(1, data.length());
			switch (type) {
			case TYPE_OPEN:
				receivedOpen(transport, message);
				break;
			case TYPE_CLOSE:
				receivedClose(transport);
				break;
			case TYPE_PING:
				send(TYPE_PONG + message.toString());
				break;
			case TYPE_PONG:
				receivedPong(transport, message);
			case TYPE_MESSAGE:
				onMessage(message.toString());
				break;
			// We're not supposed to handle them
			case TYPE_UPGRADE:
			default:

			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Garbaged packet. Ignoring...", e);
		}
	}

	synchronized void transportFailed(IOTransport transport, String message,
			Exception exception) {
		setState(STATE_INTERUPTED);
		logger.log(Level.WARNING, message, exception);
	}

	synchronized void transportClose(IOTransport transport) {
		setState(STATE_INTERUPTED);
		try {
			currentTransport.open();
		} catch (Exception e) {
			transportFailed(currentTransport, "Failed while reopening", e);
		}
	}

	synchronized void setSid(String sid) {
		this.sid = sid;
	}

	public synchronized void send(String data) {
		try {
			currentTransport.send(TYPE_MESSAGE + data);
		} catch (Exception e) {
			output.add(data);
		}
	}

	public void close() {
		try {
			currentTransport.send("" + TYPE_CLOSE);
			setState(STATE_INVALID);
		} catch (Exception e) {
			// TODO
		}
	}

	private void receivedPong(IOTransport transport, CharSequence message) {
		if (getState() == STATE_UPGRADING && transport == upgradingTransport
				&& PROBE.equals(message)) {
			try {
				transport.send("" + TYPE_UPGRADE);
				currentTransport = transport;
				logger.info("Upgrade successful");
			} catch (Exception e) {
				logger.log(Level.WARNING, "Upgrade failed", e);
			}
			setState(STATE_CONNECTED);
		}
	}

	private void receivedOpen(IOTransport transport, CharSequence message) {
		try {
			JSONObject open = new JSONObject(message.toString());
			setSid(open.getString("sid"));
			setPingTimeout(open.getInt("pingTimeout"));
			JSONArray jsonUpgrades = open.optJSONArray("upgrades");
			if (jsonUpgrades != null && jsonUpgrades.length() != 0) {
				String[] upgrades = new String[jsonUpgrades.length()];
				for (int i = 0; i < jsonUpgrades.length(); i++)
					upgrades[i] = jsonUpgrades.getString(i);
				upgradingTransport = instanceTransport(upgrades);
				if (upgradingTransport != null) {
					setState(STATE_UPGRADING);
					try {
						upgradingTransport.send(TYPE_PING + "probe");
					} catch (Exception e) {
						setState(STATE_CONNECTED);
						upgradingTransport = null;
					}
				}
			}
			else {
				setState(STATE_CONNECTED);
			}

		} catch (JSONException e) {
			callback.onError(new EngineIOException("Garbage received", e));
		}
	}

	private void receivedClose(IOTransport transport) {
		try {
			currentTransport.close();
		} catch (Exception e) {
			logger.log(Level.WARNING, "Error while closing transport", e);
		}
	}

	private synchronized int getState() {
		return state;
	}

	private synchronized void setState(int state) {
		logger.info("switching from state " + this.state + " to " + state);
		this.state = state;
	}

	private void setPingTimeout(int pingTimeout) {
		this.pingTimeout = pingTimeout;
	}

	@Override
	public void onOpen() {
		logger.info("onOpen called.");
	}

	@Override
	public void onMessage(String message) {
		logger.info("onMessage called with message '" + message + "'");
	}

	@Override
	public void onClose() {
		logger.info("onClose called");
	}

	@Override
	public void onError(EngineIOException exception) {
		logger.log(Level.WARNING, "onError called with Exception", exception);
	}
}
