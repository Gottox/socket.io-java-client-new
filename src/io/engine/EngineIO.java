/*
 * engine.io EngineIO.java
 *
 * Copyright (c) 2012, Enno Boland
 * Engine.io client
 * 
 * See LICENSE file for more information
 */
package io.engine;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EngineIO implements EngineIOCallback {
	final private char TYPE_OPEN = '0';
	final private char TYPE_CLOSE = '1';
	final private char TYPE_PING = '2';
	final private char TYPE_PONG = '3';
	final private char TYPE_MESSAGE = '4';
	final private char TYPE_UPGRADE = '5';
	final private String PROBE = "probe";
	final private Logger logger = Logger.getLogger("engine.io");

	private String host = "localhost";
	private int port = 80;
	private String resource = "";
	private ConcurrentHashMap<String, String> query = null;
	private boolean secure = false;
	private String basePath = "/engine.io";
	private boolean upgrade = true;
	private IOTransport[] transports = new IOTransport[] { new PollingTransport(), new WebsocketTransport() };
	private EngineIOCallback callback = this;
	private String uid;
	private String sid;
	private int pingTimeout;

	private IOTransport currentTransport = null;
	private IOTransport upgradingTransport = null;
	
	private final Timer timer = new Timer("background timer");
	
	private PingTimeoutTask pingTimeoutTask;
	
	private final class PingTimeoutTask extends TimerTask {
		@Override
		public void run() {
			currentTransport.reqClose();
			onError(new EngineIOException("Timeout occured"));
		}
	};
	
	

	// Configuration BEGIN
	
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

	public EngineIO transports(IOTransport[] transports) {
		if(transports == null || transports.length == 0)
			throw new RuntimeException("Transports cannot be empty.");
		this.transports = transports;
		return this;
	}

	public EngineIO callback(EngineIOCallback callback) {
		this.callback = callback;
		return this;
	}
	
	// Configuration END

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

	public IOTransport[] getTransports() {
		return transports;
	}

	public EngineIOCallback getCallback() {
		return callback;
	}

	public String getCurrentTransport() {
		return currentTransport == null ? transports[0].getName() : currentTransport
				.getName();
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
		currentTransport = instanceTransport(null);
		if (currentTransport == null)
			throw new RuntimeException(
					"No Transports to connect available. If you're implementing your own transport "
							+ "make sure you overwrite instanceTransport(String) in EngineIO"
							+ "to instanciate your custom EngineIOTransport.");
		try {
			currentTransport.reqOpen(this);
		} catch (Exception e) {
			callback.onError(new EngineIOException(
					"Error while opening connection", e));
		}
		return this;
	}

	protected IOTransport instanceTransport(String[] names) {
		if(names == null)
			return this.transports[0];
		for (String name : names) {
			for(IOTransport transport : this.transports) {
				if(transport.getName().equals(name))
					return transport;
			}
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
				send(transport, TYPE_PONG, message.toString());
				break;
			case TYPE_PONG:
				receivedPong(transport, message);
			case TYPE_MESSAGE:
				onMessage(message.toString());
				break;
			// We're not supposed to handle them
			case TYPE_UPGRADE:
			default:
				logger.warning("Received package type " + type + " we can't handle this.");
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Garbaged packet. Ignoring...", e);
		}
	}

	synchronized void transportFailed(IOTransport transport, String message,
			Exception exception) {
		logger.log(Level.WARNING, message, exception);
	}

	synchronized void setSid(String sid) {
		this.sid = sid;
	}

	public void send(String data) {
		send(TYPE_MESSAGE, data);
	}

	private void send(char type, String data) {
		send(currentTransport, type, data);
	}

	private synchronized void send(IOTransport transport, char type, String data) {
		try {
			transport.bufferedSend(type + data);
		} catch (Exception e) {
			transportFailed(transport, "failed during send", e);
		}
	}

	public void close() {
		try {
			send(TYPE_CLOSE, "");
			currentTransport.reqClose();
		} catch (Exception e) {
			transportFailed(currentTransport, "failed during close", e);
		}
	}

	private void receivedPong(IOTransport transport, CharSequence message) {
		if (transport == upgradingTransport && PROBE.equals(message)) {
			try {
				send(transport, TYPE_UPGRADE, "");
				currentTransport = transport;
				upgradingTransport = null;
				logger.info("Upgrade successful");
			} catch (Exception e) {
				logger.log(Level.WARNING, "Upgrade failed", e);
			}
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
					try {
						send(upgradingTransport, TYPE_PING, PROBE);
					} catch (Exception e) {
						upgradingTransport = null;
					}
				}
			}
			resetPingTimeout();
		} catch (JSONException e) {
			callback.onError(new EngineIOException("Garbage received", e));
		}
	}

	private void resetPingTimeout() {
		if(pingTimeoutTask != null)
			pingTimeoutTask.cancel();
		pingTimeoutTask = new PingTimeoutTask();
		timer.schedule(pingTimeoutTask, getPingTimeout());
	}

	private void receivedClose(IOTransport transport) {
		try {
			currentTransport.reqClose();
		} catch (Exception e) {
			logger.log(Level.WARNING, "Error while closing transport", e);
		}
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
