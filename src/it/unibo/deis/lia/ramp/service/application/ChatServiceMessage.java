package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public abstract class ChatServiceMessage implements Serializable {

	private static final long serialVersionUID = -4237937682586853434L;

	private int nodeId;
	private int commPort = -1;
	private int protocol;
	private ChatServiceUserProfile userProfile;

	public ChatServiceMessage(int nodeId, int commPort, int protocol, ChatServiceUserProfile userProfile)
	{
		this.nodeId = nodeId;
		this.commPort = commPort;
		this.userProfile = userProfile;
	}
	
	public int getNodeId() {
        return nodeId;
    }
	
	public int getCommPort() {
        return commPort;
    }
	
	public int getProtocol() {
		return protocol;
	}
	
	public ChatServiceUserProfile getUserProfile() {
		return userProfile;
	}
}
