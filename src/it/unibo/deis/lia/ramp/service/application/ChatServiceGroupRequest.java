package it.unibo.deis.lia.ramp.service.application;

import java.io.Serializable;

public class ChatServiceGroupRequest extends ChatServiceRequest implements Serializable{

	private static final long serialVersionUID = 6611464196080045953L;
	
	private int groupId;
    private int[] otherNodeId;
    private ChatServiceMessage[] otherDestInfo;
    
	public int getGroupId() {
        return groupId;
    }
	
	public int[] getOtherNodeId() {
        return otherNodeId;
    }

	public ChatServiceMessage[] getOtherDestInfo() {
		return otherDestInfo;
	}
	
	public void setOtherDestInfo(ChatServiceMessage[] otherDestInfo) {
		this.otherDestInfo = otherDestInfo;
	}
	
	public ChatServiceGroupRequest(int nodeId, int port, int protocol, int groupId, int[] otherNodeId, 
			ChatServiceUserProfile userProfile, int startChatPort)
	{
		super(nodeId, port, protocol, userProfile, startChatPort);
        this.groupId = groupId;
		this.otherNodeId = otherNodeId;
		this.otherDestInfo = new ChatServiceMessage[otherNodeId.length];
	}
	
	public String toString(){
		StringBuilder res = new StringBuilder("{ ");
		for(int i = 0; i < otherNodeId.length; i++)
			res.append(otherNodeId[i] +" ");
		res.append("}");
		
		return res.toString();
	}
}
