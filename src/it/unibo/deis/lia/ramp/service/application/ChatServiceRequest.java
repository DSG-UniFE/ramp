package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class ChatServiceRequest extends ChatServiceMessage implements Serializable {

	private static final long serialVersionUID = -1501153340481629547L;
	
	private int requestPort;
    private String requestType;
    
	public int getRequestPort() {
        return requestPort;
    }
	
	public String getRequestType() {
		return requestType;
	}
	
	public void setRequestType(String requestType) {
		this.requestType = requestType;
	}
	
	public ChatServiceRequest(int nodeId, int port, int protocol, ChatServiceUserProfile userProfile, int requestPort)
	{
		super(nodeId, port, protocol, userProfile);
		this.requestPort = requestPort;
		this.requestType = "";
	}
}
