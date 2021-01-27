package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class ChatServiceResponse extends ChatServiceMessage implements Serializable {

	private static final long serialVersionUID = 5672331216624766065L;
	
	public ChatServiceResponse(int nodeId, int port, int protocol, ChatServiceUserProfile userProfile)
	{
		super(nodeId, port, protocol, userProfile);
	}	
}
