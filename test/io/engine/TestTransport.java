package io.engine;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class TestTransport extends IOTransport {
	public String getConfiguration() {
		return configuration;
	}

	public TestTransport setConfiguration(String configuration) {
		this.configuration = configuration;
		return this;
	}

	public int getPingInterval() {
		return pingInterval;
	}

	public TestTransport setPingInterval(int pingInterval) {
		this.pingInterval = pingInterval;
		return this;
	}

	private String configuration;
	private int pingInterval = 0;

	Timer timer = new Timer("TestKeepAlive");
	private int pingCounter = 0;
	private int pongCounter = 0;
	private KeepAlive keepAliveTask;

	LinkedList<String> output = new LinkedList<String>();
	private String name;

	public TestTransport(String name) {
		this.name = name;
	}

	class KeepAlive extends TimerTask {
		@Override
		public void run() {
			inject("2");
			pingCounter++;
		}
	}

	@Override
	String getName() {
		return name;
	}

	public void inject(String data) {
		packet(data);
	}

	@Override
	protected void open() throws Exception {
		if (pingInterval > 0) {
			keepAliveTask = new KeepAlive();
			this.timer.schedule(keepAliveTask, pingInterval, pingInterval);
		}
		if (configuration != null) {
			inject("0" + configuration);
			output.add(EngineIOBaseTest.OPEN);
		}
	}

	public void allowSend(boolean allow) {
		setConnected(allow);
	}
	
	@Override
	protected void send(Iterator<String> datas) throws Exception {
		String data;
		while (datas.hasNext()) {
			data = datas.next();
			switch (data.charAt(0)) {
			case '1':
				// CLOSE: Nothing
				break;
			case '2':
				output.add(EngineIOBaseTest.PING);
				break;
			case '3':
				pongCounter++;
				break;
			case '4':
				output.add(data.substring(1));
				inject(data);
				break;
			case '5':
				output.add(EngineIOBaseTest.UPGRADE);
				break;
			}
			datas.remove();
		}
	}

	@Override
	protected void close() throws Exception {
		if (keepAliveTask != null) {
			keepAliveTask.cancel();
			keepAliveTask = null;
		}
		output.add(EngineIOBaseTest.CLOSE);
	}

	public boolean acknowledgedAllPing() {
		return pingCounter == pongCounter;
	}

	@Override
	public boolean isSecure() {
		return super.isSecure();
	}

	@Override
	public String getHost() {
		return super.getHost();
	}

	@Override
	public int getPort() {
		return super.getPort();
	}

	@Override
	public String getPath() {
		return super.getPath();
	}

	@Override
	public String getQuery(IOTransport transport) {
		return super.getQuery(transport);
	}

}
