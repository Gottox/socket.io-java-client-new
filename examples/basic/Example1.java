package basic;

import io.engine.EngineIO;

public class Example1 extends EngineIO {
	public Example1() {
		this.host("localhost").port(3000).open();
	}
	
	public static void main(String[] args) {
		new Example1();
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void onMessage(String message) {
		System.out.println("message: "+message);
		this.send("answer");
	}
}
