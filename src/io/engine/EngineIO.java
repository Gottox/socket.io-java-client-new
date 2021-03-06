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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EngineIO implements EngineIOCallback {
	final private static char TYPE_OPEN = '0';
	final private static char TYPE_CLOSE = '1';
	final private static char TYPE_PING = '2';
	final private static char TYPE_PONG = '3';
	final private static char TYPE_MESSAGE = '4';
	final private static char TYPE_UPGRADE = '5';
	final private static String PROBE = "probe";
	final private static Logger LOGGER = Logger.getLogger("engine.io");
	final private static Pattern TRIM_SLASH = Pattern.compile("/$");
	final private static String ISSUE_URL = "https://github.com/Gottox/socket.io-java-client-new/issues";

	private String host = "localhost";
	private int port = 80;
	private String resource = "default";
	private ConcurrentHashMap<String, String> query = null;
	private boolean secure = false;
	private String basePath = "/engine.io";
	private boolean upgrade = true;
	private IOTransport[] transports = new IOTransport[] {
			new PollingTransport(), new WebsocketTransport() };
	private EngineIOCallback callback = this;
	private String uid;
	private String sid;
	private int pingTimeout = 10000;

	private IOTransport currentTransport = null;
	private IOTransport upgradingTransport = null;

	private final Timer timer = new Timer("background timer");

	private PingTimeoutTask pingTimeoutTask;
	private Exception lastException = null;

	private final class PingTimeoutTask extends TimerTask {
		@Override
		public void run() {
			currentTransport.shutdown();
			if (upgradingTransport != null)
				upgradingTransport.shutdown();
			onError(new EngineIOException("Timeout occured", lastException));
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

	public EngineIO resource(String resource) {
		this.resource = resource;
		return this;
	}

	public EngineIO pingTimeout(int pingTimeout) {
		this.pingTimeout = pingTimeout;
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
		this.basePath = basePath.replaceFirst("/$", "");
		return this;
	}

	public EngineIO upgrade(boolean upgrade) {
		this.upgrade = upgrade;
		return this;
	}

	public EngineIO transports(IOTransport... transports) {
		if (transports == null || transports.length == 0)
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

	public String getResource() {
		return resource;
	}

	public Map<String, String> getQuery() {
		return query;
	}

	public boolean isSecure() {
		return secure;
	}

	public String getBasePath() {
		return TRIM_SLASH.matcher(basePath).replaceFirst("");
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
		return currentTransport == null ? transports[0].getName()
				: currentTransport.getName();
	}

	public synchronized String getSid() {
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
			currentTransport.start(this);
			resetPingTimeout();
		} catch (Exception e) {
			callback.onError(new EngineIOException(
					"Error while opening connection", e));
		}
		return this;
	}

	protected IOTransport instanceTransport(List<String> names) {
		if (names == null)
			return this.transports[0];
		for (String name : names) {
			for (IOTransport transport : this.transports) {
				if (transport.getName().equals(name))
					return transport;
			}
		}
		return null;
	}

	String genQuery(IOTransport transport) {
		try {
			StringBuilder builder = new StringBuilder("?uid=").append(getUid())
					.append("&transport=").append(transport.getName());
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
			throw new RuntimeException("Unsupported Encoding. Internal error."
					+ "Please report this at " + ISSUE_URL, e);
		}
	}

	void transportPacket(IOTransport transport, String data) {
		LOGGER.info("< " + data);
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
				break;
			case TYPE_MESSAGE:
				onMessage(message.toString());
				break;
			// We're not supposed to handle them
			case TYPE_UPGRADE:
			default:
				LOGGER.warning("Received package type " + type
						+ ". We can't handle this.");
			}
			resetPingTimeout();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Garbaged packet. Ignoring...", e);
		}
	}

	synchronized void transportFailed(IOTransport transport, String message,
			Exception exception) {
		lastException = exception;
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
		for (IOTransport t : new IOTransport[] { currentTransport,
				upgradingTransport }) {
			try {
				if (t != null) {
					send(t, TYPE_CLOSE, "");
					t.shutdown();
				}
			} catch (Exception e) {
				transportFailed(t, "failed during close", e);
			}
		}
		callback.onClose();

	}

	private void receivedPong(IOTransport transport, CharSequence message) {
		if (transport == upgradingTransport && PROBE.equals(message)) {
			try {
				send(transport, TYPE_UPGRADE, "");
				currentTransport = transport;
				upgradingTransport = null;
				LOGGER.info("Upgrade successful");
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Upgrade failed", e);
			}
		}
	}

	private void receivedOpen(IOTransport transport, CharSequence message) {
		try {
			JSONObject open = new JSONObject(message.toString());
			setSid(open.getString("sid"));
			pingTimeout(open.getInt("pingTimeout"));
			JSONArray jsonUpgrades = open.optJSONArray("upgrades");
			if (isUpgrade() && jsonUpgrades != null
					&& jsonUpgrades.length() != 0) {
				ArrayList<String> upgrades = new ArrayList<String>(jsonUpgrades.length());
				for (int i = 0; i < jsonUpgrades.length(); i++)
					upgrades.add(jsonUpgrades.getString(i));
				tryUpgrade(upgrades);
			}
			callback.onOpen();
		} catch (JSONException e) {
			callback.onError(new EngineIOException("Garbage received", e));
		}
	}
	
	private void tryUpgrade(ArrayList<String> upgrades) {
		if(upgradingTransport != null)
			return; // TODO: Client should interrupt current upgrade process and start a new one instead. 
		upgradingTransport = instanceTransport(upgrades);
		if (upgradingTransport != null) {
			upgradingTransport.start(this);
			try {
				send(upgradingTransport, TYPE_PING, PROBE);
			} catch (Exception e) {
				upgradingTransport = null;
			}
		}
	}

	private synchronized void resetPingTimeout() {
		if (pingTimeoutTask != null)
			pingTimeoutTask.cancel();
		pingTimeoutTask = new PingTimeoutTask();
		timer.schedule(pingTimeoutTask, getPingTimeout());
	}

	private void receivedClose(IOTransport transport) {
		try {
			currentTransport.shutdown();
		} catch (Exception e) {
			// TODO: recheck if we can safely ignore this exception
			callback.onError(new EngineIOException(
					"Error while closing transport", e));
		}
	}

	@Override
	public void onOpen() {
		LOGGER.info("onOpen called.");
	}

	@Override
	public void onMessage(String message) {
		LOGGER.info("onMessage called with message '" + message + "'");
	}

	@Override
	public void onClose() {
		LOGGER.info("onClose called");
	}

	@Override
	public void onError(EngineIOException exception) {
		LOGGER.log(Level.WARNING, "onError called with Exception", exception);
	}
}
