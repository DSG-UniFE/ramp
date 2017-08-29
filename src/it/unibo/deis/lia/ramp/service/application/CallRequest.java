package it.unibo.deis.lia.ramp.service.application;

public class CallRequest extends StreamRequest {

	private static final long serialVersionUID = -3270954796477571501L;
	private int myServicePort;
	
	public CallRequest(String streamName, int clientPort, String streamProtocol, String rampProtocol, int myServicePort) {
		super(streamName, clientPort, streamProtocol, rampProtocol);
		this.setMyServicePort(myServicePort);
	}

	public int getMyServicePort() {
		return myServicePort;
	}

	private void setMyServicePort(int myServicePort) {
		this.myServicePort = myServicePort;
	}
}
