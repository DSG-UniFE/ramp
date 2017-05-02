package it.unibo.deis.lia.ramp.service.application;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.SavedPacket;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

public class ChatCommunicationSupport  extends Thread {

    private Hashtable<Integer, String[]> dest;
    public static int COMMUNICATION_PROTOCOL = E2EComm.TCP;
    
    private Hashtable<Integer, ChatServiceMessage> destInfo; //da qui ricavo destNodeId, destPort, etc..
    private ChatServiceUserProfile senderInfo;
    
    private boolean active=false;
    private Vector<String> receivedMessages;
    private BoundReceiveSocket serviceSocket;
    
    private boolean groupCommunication = false;
    private boolean supportVisible = false;
    private int chatComId = -1;
    
    public ChatCommunicationSupport(Hashtable<Integer, String[]> dest, ChatServiceUserProfile senderInfo, Hashtable<Integer, ChatServiceMessage> destInfo)
    {
    	 initialize(dest, senderInfo, destInfo);
         
         try {
             serviceSocket = E2EComm.bindPreReceive(COMMUNICATION_PROTOCOL);
//             communication_port = getLocalPort();
             System.out.println("CommunicationSupport creato sulla porta:" + serviceSocket.getLocalPort());
         } catch (Exception ex) {
             ex.printStackTrace();
         }
    }

    public ChatCommunicationSupport(Hashtable<Integer, String[]> dest, int port, ChatServiceUserProfile senderInfo, Hashtable<Integer, ChatServiceMessage> destInfo)
    {
    	 initialize(dest, senderInfo, destInfo);
         
         try {
             serviceSocket = E2EComm.bindPreReceive(port, COMMUNICATION_PROTOCOL);
//             communication_port = getLocalPort();
             System.out.println("CommunicationSupport creato sulla porta:" + serviceSocket.getLocalPort());
         } catch (Exception ex) {
             ex.printStackTrace();
         }
    }
    
    private void initialize(Hashtable<Integer, String[]> dest, ChatServiceUserProfile senderInfo, Hashtable<Integer, ChatServiceMessage> destInfo) {
		this.dest = dest;
    	 this.destInfo = destInfo;
         this.senderInfo = senderInfo;
         
         receivedMessages = new Vector<String>();
         if(dest.size() > 1)
        	 groupCommunication = true;
         else
        	 groupCommunication = false;
	}
    
    public boolean isSupportVisible() {
	    return supportVisible;
	  } 
    public void setSupportVisible(boolean visible) {
	    this.supportVisible = visible;
	  } 
    
    public void setChatComIdVisible(int chatComId) {
    	this.chatComId = chatComId;
	  } 
    
    public boolean isActive() {
        return active;
    }
    
    public boolean isGroupCommunication() {
    	return groupCommunication;
    }

    public int getLocalPort() {
        return serviceSocket.getLocalPort();
    }
    
    public Hashtable<Integer, ChatServiceMessage> getDestInfo() {
    	return destInfo;
    }
    
    public void setDestInfo(Hashtable<Integer, ChatServiceMessage> destInfo) {
    	this.destInfo = destInfo;
    }

    public Vector<String> getReceivedMessages() {
    	
    	return receivedMessages;
    }
    
    public void setReceivedMessages(Vector<String> messages) {
    	
    	this.receivedMessages = messages;
    }
    
    public void sendMessage(String message) {
    	
    	Message messageObject = new Message(message);
    	//int destNodeId = destInfo.getNodeId();
    	int destPort = getLocalPort();
//    	boolean sent = true;
    	
    	try {
    		Enumeration<Integer> nodeId = dest.keys();
    		while(nodeId.hasMoreElements()) {
    		      int destNodeId = (Integer) nodeId.nextElement();
    		      String[] currentDest = dest.get(destNodeId);
    		   
    		      System.out.println("CommunicationSupport: " +message + " INVIATO AL CONTATTO: " + destNodeId + " ALLA PORTA:" + destPort);
    		      GeneralUtils.appendLog("CommunicationSupport message: " +message + " send to contact: " + destNodeId + " port:" + destPort);
    		      int expiry = senderInfo.getExpiry();
    		      
    		      E2EComm.sendUnicast(currentDest, destNodeId, destPort,
    		    			COMMUNICATION_PROTOCOL,
    		    			false, // ack
    		    			GenericPacket.UNUSED_FIELD, // timeoutAck
    		    			GenericPacket.UNUSED_FIELD, // bufferSize
    		    			3000,
    		    			expiry, //expiry
    		    			GenericPacket.UNUSED_FIELD,
    		    			E2EComm.serialize(messageObject));
    		      
//    		      if (sent)
//    		    	  System.out.println("CommunicationSupport: " +message + " INVIATO AL CONTATTO: " + destNodeId + " ALLA PORTA:" + destPort);
//    		      else
//    		      {
//    				  System.out.println("CommunicationSupport: msg non spedito al contatto: "+ destNodeId);
//    			  }
    		}
        }
    	catch (Exception e) {
    		e.printStackTrace();
//    		sent = false;
    	}
    	
//    	if (sent) 
    	String sender = String.valueOf(Dispatcher.getLocalRampId());
    	if(!senderInfo.getFirstName().equals("") && !senderInfo.getLastName().equals(""))
    		sender = senderInfo.getFirstName() + " " +senderInfo.getLastName();
		receivedMessages.addElement(sender + ": " + message);
    	
//    	return sent;
    }
    
    @Override
    public void run() {
        try {

            System.out.println("CommunicationSupport START");
            active=true;
            while (active) {
                try {
                    GenericPacket gp = E2EComm.receive(serviceSocket);
                    System.out.println("CommunicationSupport new request");
                    new MessageHandler(gp, this).start();
                } catch (SocketTimeoutException ste) {
                    ste.printStackTrace();
                }
            }
            serviceSocket.close();
            System.out.println("CommunicationSupport FINISHED");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
//    public void deactivate() {
//		System.out.println("CommunicationSupport DISABLED");
//		this.active = false;
//		interrupt();
//	}
    
    public void stopService(boolean notifyOtherNodes) {
    	
    	System.out.println("CommunicationSupport CLOSE");
        active = false;
        
//        if (notifyOtherNodes) {
//        	//Avvisare gli altri nodi del gruppo della sua uscita???
//        }
    }
    
    private class MessageHandler extends Thread {

        private GenericPacket gp;
        private ChatCommunicationSupport support;
        private ChatServiceON ch=null;
        
        private MessageHandler(GenericPacket gp, ChatCommunicationSupport support) {
            this.gp = gp;
            this.support = support;
        }

        @Override
        public void run() {
            try {
            	if(ch==null )
        			ch = ChatServiceON.getInstance();
        			
                // check packet type
                if (gp instanceof UnicastPacket) {
                	
                	UnicastPacket up = (UnicastPacket) gp;
                    UnicastHeader uh = up.getHeader();
                    int sourceNodeId = uh.getSourceNodeId();

                    // check payload
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if (payload instanceof Message) {
                    	Message messageObject = (Message) payload;
                        String message = messageObject.getMessage();
                        System.out.println("CommunicationSupport: received message: " + message);
                    
                        ChatServiceUserProfile userProfile =  support.getDestInfo().get(sourceNodeId).getUserProfile();
                        String headerMess = String.valueOf(sourceNodeId);
                        if(!userProfile.getFirstName().equals("") &&
                        		!userProfile.getLastName().equals("") )
                        	headerMess = userProfile.getFirstName() + " "+ userProfile.getLastName();
                        
                        receivedMessages.addElement(headerMess + ": " + message);
                        System.out.println("CommunicationSupport.MessageHandler message: " + message);
                        GeneralUtils.appendLog("CommunicationSupport.MessageHandler received message:" + message +", from: "+sourceNodeId);
                        
                        if(!support.isSupportVisible())
                        	ch.notifyChatRequest("message", chatComId);
                    }
                }

                else {
                	// received packet is not UnicastPacket: do nothing...
                	System.out.println("CommunicationSupport.MessageHandler wrong packet: " + gp.getClass().getName());
                }

           	} catch (Exception e) {
           		e.printStackTrace();
           	}
        }
        
        
    }
}
