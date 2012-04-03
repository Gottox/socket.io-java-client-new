/*
 * engine.io EngineIO.java
 *
 * Copyright (c) 2012, Enno Boland
 * Engine.io client
 * 
 * See LICENSE file for more information
 */
package io.engine;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EngineIO implements EngineIOCallback {
	final private int STATE_INIT = 0;
	final private int STATE_CONNECTING = 1;
	final private int STATE_CONNECTED = 2;
	final private int STATE_INTERUPTED = 3;
	final private int STATE_INVALID = 4;
	private int state = STATE_INIT;
	final private char TYPE_OPEN = '0';
	final private char TYPE_CLOSE = '1';
	final private char TYPE_PING = '2';
	final private char TYPE_PONG = '3';
	final private char TYPE_MESSAGE = '4';
	final private char TYPE_UPGRADE = '5';

	private String host = "localhost";
	private int port = 80;
	private String path = "";
	private ConcurrentHashMap<String, String> query = null;
	private boolean secure = false;
	private String basePath = "/engine.io";
	private boolean upgrade = true;
	private String[] transports = new String[] { "polling", "websocket" };
	private EngineIOCallback callback = this;
	private String uid;
	private String sid;
	private int pingTimeout;
	private ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<String>();

	private IOTransport currentTransport = null;

	public EngineIO() {
		this.uid = ("" + Math.random()).substring(5)
				+ ("" + Math.random()).substring(5);
	}

	public EngineIO host(String host) {
		this.host = host;
		return this;
	}

	public EngineIO port(int port) {
		this.port = port;
		return this;
	}

	public EngineIO path(String path) {
		this.path = path;
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
		return path;
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
				.getName();
	}

	synchronized public EngineIO open() {
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
			currentTransport.open(this);
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

	void transportReceived(IOTransport transport, String data) {
		char type = data.charAt(1);
		String message = data.substring(1);
		switch (type) {
		case TYPE_OPEN:
			openTransport(message);
			break;
		case TYPE_CLOSE:
			closeTransport();
			break;
		case TYPE_PING:
			send(TYPE_PONG+message);
			break;
		case TYPE_MESSAGE:
			onMessage(message);
			break;
		case TYPE_PONG:
		case TYPE_UPGRADE:
			// We're not supposed to handle them
		default:

		}
	}

	

	/*
	 * void transportReceived(Reader reader) { try { char c; while ((c = (char)
	 * reader.read()) != -1) { StringBuilder sizeBuilder = new StringBuilder(4);
	 * do { sizeBuilder.append(c); } while ((c = (char) reader.read()) != -1 &&
	 * c != ':');
	 * 
	 * int left = Integer.parseInt(sizeBuilder.toString()); char[] buffer = new
	 * char[left]; while (left > 0) left -= reader.read(buffer, buffer.length -
	 * left, left); callback.onMessage(new String(buffer)); } } catch
	 * (IOException e) { callback.onError(new
	 * EngineIOException("Garbage received", e)); } }
	 */

	public synchronized void send(String data) {
		if (getState() == STATE_CONNECTED) {
			try {
				currentTransport.send(data);
			} catch (Exception e) {
				output.add(data);
			}
		} else
			output.add(data);
	}

	synchronized void transportError(IOTransport transport, String message, Exception exception) {
		setState(STATE_INTERUPTED);
		onError(new EngineIOException(message, exception));
	}

	synchronized void transportDisconnected(IOTransport transport) {
		setState(STATE_INTERUPTED);
	}

	private void openTransport(String message) {
		try {
			JSONObject open = new JSONObject(message);
			setSid(open.getString("sid"));
			setPingTimeout(open.getInt("pingTimeout"));
			JSONArray jsonUpgrades = open.optJSONArray("upgrades");
			if (jsonUpgrades != null) {
				String[] upgrades = new String[jsonUpgrades.length()];
				for (int i = 0; i < jsonUpgrades.length(); i++)
					upgrades[i] = jsonUpgrades.getString(i);
				IOTransport transport = instanceTransport(upgrades);
				if(transport != null)
					currentTransport = transport;
			}
	
		} catch (JSONException e) {
			callback.onError(new EngineIOException("Garbage received", e));
		}
	}

	private void closeTransport() {
		// TODO Auto-generated method stub
	
	}

	@Override
	public void onOpen() {
		setState(STATE_CONNECTED);
	}

	@Override
	public void onMessage(String message) {
	}

	@Override
	public void onClose() {
	}

	@Override
	public void onError(EngineIOException exception) {
	}

	String genQuery() {
		try {
			StringBuilder builder = new StringBuilder("?uid=").append(getUid())
					.append("&transport=").append(getCurrentTransport());
			String sid = getSid();
			if (sid != null) {
				builder.append("&sid=").append(URLEncoder.encode(sid, "UTF-8"));
			}
			for (Entry<String, String> entry : query.entrySet()) {
				builder.append('&')
						.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
						.append('=')
						.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
			}
			return builder.toString();
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(
					"Unsupported Encoding. Internal error."
							+ "Please report this at https://github.com/Gottox/socket.io-java-client/issues",
					e);
		}
	}

	public synchronized String getSid() {
		return sid;
	}

	synchronized void setSid(String sid) {
		this.sid = sid;
	}

	private synchronized int getState() {
		return state;
	}

	private synchronized void setState(int state) {
		this.state = state;
	}

	public int getPingTimeout() {
		return pingTimeout;
	}

	private void setPingTimeout(int pingTimeout) {
		this.pingTimeout = pingTimeout;
	}
}
