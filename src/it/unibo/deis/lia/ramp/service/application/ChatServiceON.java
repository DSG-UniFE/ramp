package it.unibo.deis.lia.ramp.service.application;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.Resolver;
import it.unibo.deis.lia.ramp.core.internode.ResolverPath;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

/**
*
* @author Stefano Lanzone
*/
public class ChatServiceON extends Thread {

	    private static BoundReceiveSocket serviceSocket;
	    public static int CHAT_PROTOCOL = E2EComm.TCP;
	    final public static int CHAT_PORT = 6300;

	    private boolean active = true;
	    private static ChatServiceON chat=null;

	    private ChatServiceUserProfile userProfile;
	    private Hashtable<Integer, ChatCommunicationSupport> openedChat;
//	    Vector<ServiceResponse> contacts = null;

	    //Bx Messages
	    private LinkedHashMap<Integer, ChatServiceRequest> bxMessages;

	    public static boolean isActive(){
	        return ChatServiceON.chat != null;
	    }

	    public Hashtable<Integer, ChatCommunicationSupport> getOpenedChat(){
			return openedChat;
		}

	    public ChatCommunicationSupport getChatSupport(int user){
			return openedChat.get(user);
		}

	    public void closeChatSupport(int user){
	    	ChatCommunicationSupport support = openedChat.get(user);
	    	support.stopService(false);
	    	openedChat.remove(user);
		}

	    public ChatServiceRequest getLastBxMessage(){
			if(bxMessages != null && bxMessages.size() > 0)
			{
				ArrayList<ChatServiceRequest> arrayList = new ArrayList<ChatServiceRequest>(this.bxMessages.values());
			    return arrayList.get(arrayList.size()-1);
			}
			else
				return null;
		}

	    public void setChatUserProfile()
	    {
	    	if (RampEntryPoint.getAndroidContext() != null){
         		try {
                     Class<?> activityChat = Class.forName("it.unife.dsg.ramp_android.service.application.ChatServiceActivity");

                     Method mI=activityChat.getMethod("setChatUserProfile");
                     Method aMI = activityChat.getMethod("getInstance");
                     if(mI!=null){

                      	mI.invoke(aMI.invoke(null, new Object[]{}), (Object[])null);
                      }

                 } catch (IllegalAccessException ex) {
                     Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
                 } catch (IllegalArgumentException ex) {
                     Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
                 } catch (InvocationTargetException ex) {
                     Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
                 } catch (NoSuchMethodException ex) {
                     Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
                 } catch (SecurityException ex) {
                     Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
                 } catch (ClassNotFoundException ex) {
                     Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
                 }
         	}
	    }

	    public ChatServiceUserProfile getChatUserProfile()
	    {
	    	return userProfile;
	    }

	    public void stopService(){
	        active=false;
	        try {
	            serviceSocket.close();
	        } catch (Exception ex) {
	            ex.printStackTrace();
	        }
	        chat=null;
	    }

	    public static synchronized ChatServiceON getInstance(){
	        try{

	            if(chat==null){
	            	chat= new ChatServiceON();
	            	chat.start();

	            }
	        }
	        catch(Exception e){
	            e.printStackTrace();
	        }
	        return chat;
	    }

	    private ChatServiceON() throws Exception{
	        serviceSocket = E2EComm.bindPreReceive(CHAT_PORT, CHAT_PROTOCOL);
	        //CHAT_PORT=serviceSocket.getLocalPort();

	        openedChat = new Hashtable<Integer, ChatCommunicationSupport>();
	        userProfile = new ChatServiceUserProfile("", "", "", null, 3, 5000, GenericPacket.UNUSED_FIELD, 0);
	        bxMessages = new LinkedHashMap<Integer, ChatServiceRequest>();

	        ServiceManager.getInstance(false).registerService("" +
	        		"ChatService",
	        		CHAT_PORT,
	        		CHAT_PROTOCOL
	    		);
	    }

	    public Vector<ServiceResponse> findContactChatService(int ttl, int timeout, int serviceAmount) throws Exception{
	        long pre = System.currentTimeMillis();
	        Vector<ServiceResponse> services = ServiceDiscovery.findServices(
	                ttl,
	                "ChatService",
	                timeout,
	                serviceAmount,
	                null
	        );
	        long post = System.currentTimeMillis();
	        float elapsed = (post-pre)/(float)1000;
	        System.out.println("ChatService findContactChatService elapsed="+elapsed+"    services="+services);

	        return services;
	    }

	    public Hashtable<String, ServiceResponse> getContactInfo(List<ServiceResponse> contactList, int timeout) throws Exception
	    {
	    	Hashtable<String, ServiceResponse> csupTable = new Hashtable<String, ServiceResponse>();
	    	int GET_CONTACT_INFO_PROTOCOL = E2EComm.UDP;
	    	BoundReceiveSocket contactInfoSocket = E2EComm.bindPreReceive(GET_CONTACT_INFO_PROTOCOL);
	    	int nodeId = Dispatcher.getLocalRampId();
        	ChatServiceRequest cs_request = new ChatServiceRequest(nodeId, 0, GET_CONTACT_INFO_PROTOCOL, null, contactInfoSocket.getLocalPort());
        	cs_request.setRequestType("INFO");
        	byte[] serializeRequest = E2EComm.serialize(cs_request);
        	boolean receiveAllResponse = true;
	    	for (int i = 0; i < contactList.size() && receiveAllResponse; i++)
        	{
	    		ServiceResponse contactSelect = contactList.get(i);

        		E2EComm.sendUnicast(
     	    		contactSelect.getServerDest(),
     	    		contactSelect.getServerPort(),
     	    		contactSelect.getProtocol(),
     	    		serializeRequest
             		);

        		try{
            		UnicastPacket up = (UnicastPacket) E2EComm.receive(contactInfoSocket,  timeout); // timeout

            		//Alternativa, ricevo già lo userId sotto forma di stringa
            		Message message = (Message)(E2EComm.deserialize(up.getBytePayload()));
            		String user = message.getMessage();
            		csupTable.put(user, contactSelect);
            		System.out.println("ChatService getContactInfo: ChatServiceUserProfile from "+contactSelect.getServerNodeId());
            		}
            		catch(java.net.SocketTimeoutException ste){
                        System.out.println("ChatService getContactInfo SocketTimeoutException: ste = "+ste);
                        receiveAllResponse = false;
//                        csupTable = null;
                        if(contactInfoSocket != null)
                        	contactInfoSocket.close();
                    }
        	}

	    	return csupTable;
	    }

	    public boolean sendBroadcastMessage(ChatServiceUserProfile userProfile)
	    {
	    	boolean res = true;

	    	int ttl = userProfile.getTTL();
	    	int expiry = userProfile.getExpiry();
	    	int destPort = CHAT_PORT;
	    	int protocol = CHAT_PROTOCOL;

	    	int nodeId = Dispatcher.getLocalRampId();
	    	ChatServiceRequest cs_request = new ChatServiceRequest(nodeId, destPort, protocol, userProfile, destPort);

	    	try
	    	{
	    		GeneralUtils.appendLog("ChatService sendBroadcastMessage");
	    		if(expiry != GenericPacket.UNUSED_FIELD)
	    			E2EComm.sendBroadcast(ttl, destPort, protocol, expiry, E2EComm.serialize(cs_request));
	    		else
	    			E2EComm.sendBroadcast(ttl, destPort, protocol, E2EComm.serialize(cs_request));
	    	}
	    	catch (Exception e) {
	    		res = false;
	    		 e.printStackTrace();
			}

	    	return res;
	    }

	    public int startCommunication(List<ServiceResponse> selectedContacts, ChatServiceUserProfile userProfile) throws Exception
	    {
	    	int id = -1;
	    	if(selectedContacts.size() == 1)
	    	{
	    		id = startUnicastCommunication(selectedContacts.get(0), userProfile);
	    	}
	    	else
	    	{
	    		//Group
	    		id = startGroupCommunication(selectedContacts, userProfile);
	    	}
	    	return id;
	    }

	    private int startGroupCommunication(List<ServiceResponse> selectedContacts, ChatServiceUserProfile userProfile) throws Exception{

	    	int groupId = RampEntryPoint.nextRandomInt();

        	if(!openedChat.containsKey(groupId)){

        		int STARTCHAT_PROTOCOL = E2EComm.UDP;
            	ChatCommunicationSupport communication;
            	int timeout = userProfile.getTimeout();

            	BoundReceiveSocket startChatSocket = E2EComm.bindPreReceive(STARTCHAT_PROTOCOL);

            	int nodeId = Dispatcher.getLocalRampId();
            	//OtherNodeID
            	int[] otherNodeId = new int[selectedContacts.size()];
            	Hashtable<Integer, String[]> dest = new Hashtable<Integer, String[]>();

            	for (int i = 0; i < selectedContacts.size(); i++)
            	{
            		otherNodeId[i] = selectedContacts.get(i).getServerNodeId();
                	dest.put(otherNodeId[i], selectedContacts.get(i).getServerDest());
            	}

            	communication = new ChatCommunicationSupport(dest, userProfile, null); //Dest info set after receive response

            	ChatServiceGroupRequest cg_request = new ChatServiceGroupRequest(nodeId, communication.getLocalPort(), STARTCHAT_PROTOCOL, groupId, otherNodeId, userProfile, startChatSocket.getLocalPort());
            	byte[] serializeRequest = E2EComm.serialize(cg_request);
            	System.out.println("ChatService startGroupCommunication: ChatServiceGroupRequest from "+nodeId +" to " +cg_request.toString());

	    		Hashtable<Integer, ChatServiceMessage> destInfo = new Hashtable<Integer, ChatServiceMessage>();
            	boolean receiveAllResponse = true;
	    		for (int i = 0; i < selectedContacts.size() && receiveAllResponse; i++)
            	{
            		ServiceResponse contactSelect = selectedContacts.get(i);

            		E2EComm.sendUnicast(
         	    		contactSelect.getServerDest(),
         	    		contactSelect.getServerPort(),
         	    		contactSelect.getProtocol(),
         	    		serializeRequest
                 		);

            		try{
            		UnicastPacket up = (UnicastPacket) E2EComm.receive(startChatSocket,  timeout); // timeout
            		ChatServiceResponse cs_response = (ChatServiceResponse)(E2EComm.deserialize(up.getBytePayload()));
            		destInfo.put(contactSelect.getServerNodeId(), cs_response);
            		System.out.println("ChatService startGroupCommunication: ChatServiceResponse from "+contactSelect.getServerNodeId());
            		}
            		catch(java.net.SocketTimeoutException ste){
                        System.out.println("ChatService startGroupCommunication SocketTimeoutException: ste = "+ste);
                        receiveAllResponse = false;
                        if(startChatSocket != null)
                        	startChatSocket.close();
                    }
                }

    			if(receiveAllResponse)
    			{
    				communication.setDestInfo(destInfo);
        			openedChat.put(groupId, communication);
        			if(startChatSocket != null)
        				startChatSocket.close();

        			ChatServiceMessage[] otherDestInfo = new ChatServiceMessage[otherNodeId.length];
        			for (int i = 0; i < selectedContacts.size(); i++)
                	{
        				ServiceResponse contactSelect = selectedContacts.get(i);
        				otherDestInfo[i] = communication.getDestInfo().get(contactSelect.getServerNodeId());
                	}
        			cg_request.setOtherDestInfo(otherDestInfo);
        			cg_request.setRequestType("OK");
        			serializeRequest = E2EComm.serialize(cg_request);

        			for (int i = 0; i < selectedContacts.size(); i++)
                	{
        				ServiceResponse contactSelect = selectedContacts.get(i);

        				E2EComm.sendUnicast(
             	    		contactSelect.getServerDest(),
             	    		contactSelect.getServerPort(),
             	    		contactSelect.getProtocol(),
             	    		serializeRequest
                     		);
//        				Thread.sleep(50);
                	}
    			}
    			else
    				groupId = -1;
        	}

        	return groupId;
	    }

		private int startUnicastCommunication(ServiceResponse contactSelect, ChatServiceUserProfile userProfile) throws Exception{

	    	int destId = contactSelect.getServerNodeId();

//	    	if(!openedChat.containsKey(destId)){

	    		 int STARTCHAT_PROTOCOL = E2EComm.UDP;
	         	 ChatCommunicationSupport communication;
	         	 int timeout = userProfile.getTimeout();

	         	 //In questo caso la chat che si vuole avviare non è stata
	         	 //ancora creata con "startCommunication"...
	         	 BoundReceiveSocket startChatSocket = E2EComm.bindPreReceive(STARTCHAT_PROTOCOL);

	         	 Hashtable<Integer, String[]> dest = new Hashtable<Integer, String[]>();
	         	 dest.put(contactSelect.getServerNodeId(), contactSelect.getServerDest());
	         	 communication = new ChatCommunicationSupport(dest, userProfile, null); //Dest info set after receive response

	         	 int nodeId = Dispatcher.getLocalRampId();
	         	 ChatServiceRequest cs_request = new ChatServiceRequest(nodeId, communication.getLocalPort(), STARTCHAT_PROTOCOL, userProfile, startChatSocket.getLocalPort());
	         	 System.out.println("ChatService startUnicastCommunication: ChatServiceRequest from "+nodeId +" to " +destId);

	    		 E2EComm.sendUnicast(
	    			 			contactSelect.getServerDest(),
	    			 			contactSelect.getServerPort(),
	    			 			contactSelect.getProtocol(),
	    			 			E2EComm.serialize(cs_request)
	    		  );

	    		  System.out.println("ChatService startUnicastCommunication: contactSelect.getServerDest()="+Arrays.toString(contactSelect.getServerDest())+" contactSelect.getServerPort="+contactSelect.getServerPort()+" contactSelect.getProtocol="+contactSelect.getProtocol());

	    		  boolean receiveResponse = true;
	    		  try{
	    			UnicastPacket up = (UnicastPacket) E2EComm.receive(startChatSocket,  timeout); // timeout
	    		  	ChatServiceResponse cs_response = (ChatServiceResponse)(E2EComm.deserialize(up.getBytePayload()));

	    		  	System.out.println("ChatService startUnicastCommunication: ChatServiceResponse from "+contactSelect.getServerNodeId());

	    		  	Hashtable<Integer, ChatServiceMessage> destInfo = new Hashtable<Integer, ChatServiceMessage>();
         		  	destInfo.put(contactSelect.getServerNodeId(), cs_response);
         		  	communication.setDestInfo(destInfo);

         		  	//NEW!
         		  	if(openedChat.containsKey(destId))
         		  	{
         		  		Vector<String> messages = openedChat.get(destId).getReceivedMessages();
         		  		this.closeChatSupport(destId);
         		  		communication.setReceivedMessages(messages);
         		  	}

                  	openedChat.put(destId, communication);
	    		  }
	    		  catch(java.net.SocketTimeoutException ste){
                      System.out.println("ChatService startUnicastCommunication SocketTimeoutException: ste = "+ste);
                      receiveResponse = false;
                      destId = -1;
                      if(startChatSocket != null)
                      	startChatSocket.close();
                  }

                  if(receiveResponse && startChatSocket != null)
                	  startChatSocket.close();
//	    	}

	    	return destId;
		}

	    @Override
	    public void run(){
	        try{
	            System.out.println("ChatService START");
	            System.out.println("ChatService START "+serviceSocket.getLocalPort()+" "+CHAT_PROTOCOL);
	            while(active){
	                try{
	                	// receive
	                    GenericPacket gp=E2EComm.receive(serviceSocket);
	                    new ChatServiceHandler(gp,this).start();//ricevo sourcePort, crea CommSupp e invio a source la localPort
	                }
	                catch(SocketTimeoutException ste){
	                    ste.printStackTrace();
	                }
	            }
	            serviceSocket.close();
	            System.out.println("ChatService FINISHED");
	        }
	        catch(Exception e){
	            e.printStackTrace();
	        }
	    }

		public void notifyChatRequest(String type, int sourceNodeId) {
			if (RampEntryPoint.getAndroidContext() != null){
				try {
			         Class<?> activityChat = Class.forName("it.unife.dsg.ramp_android.service.application.ChatServiceActivity");

//                                 Method mI=activityChat.getMethod("startChat",Integer.TYPE);
//                                 Method aMI = activityChat.getMethod("getInstance");
//
//                                 if(mI!=null){
//
//                                 	mI.invoke(aMI.invoke(null, new Object[]{}), sourceNodeId);
//                                 }

			         Method mI=activityChat.getMethod("createNotification", String.class, Integer.TYPE);
			         Method aMI = activityChat.getMethod("getInstance");
			         if(mI!=null){

			          	mI.invoke(aMI.invoke(null, new Object[]{}), type, sourceNodeId);
			          }

			     } catch (IllegalAccessException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (IllegalArgumentException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (InvocationTargetException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (NoSuchMethodException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (SecurityException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     } catch (ClassNotFoundException ex) {
			         Logger.getLogger(ChatCommunicationSupport.class.getName()).log(Level.SEVERE, null, ex);
			     }
			}
		}

	    //Thread for Chat Connection Requests, Group Request, Bx Message
	    private class ChatServiceHandler extends Thread{

	    	 private GenericPacket gp;
	         private ChatServiceON ch;
	         private ChatServiceHandler(GenericPacket gp,ChatServiceON ch){
	             this.gp=gp;
	             this.ch=ch;
	         }

	         @Override
	         public void run(){
	             try{
	                 // check packet type
	                 if( gp instanceof UnicastPacket){
	                	 UnicastPacket up=(UnicastPacket)gp;
	                	 Object payload = E2EComm.deserialize(up.getBytePayload());
	                	// check header
	                     UnicastHeader uh=up.getHeader();
	                     String[] source=uh.getSource();
	                     int sourceNodeId=uh.getSourceNodeId();
	                     String[] newDest = E2EComm.ipReverse(source);

	                     ChatCommunicationSupport communication;
	                     int nodeId = Dispatcher.getLocalRampId();

	                     ch.setChatUserProfile();
            			 ChatServiceUserProfile userProfile = ch.getChatUserProfile();

	                	 if(payload.getClass() == ChatServiceRequest.class) {
	                		 System.out.println("ChatService ChatServiceRequest");

	                		 ChatServiceRequest cs_request = (ChatServiceRequest)payload;
	                		 String requestType = cs_request.getRequestType();

	                		 if(requestType.equals(""))
	                         {
	                			 Hashtable<Integer, ChatServiceMessage> destInfo = new Hashtable<Integer, ChatServiceMessage>();
	                			 destInfo.put(sourceNodeId, cs_request);
	                			 Vector<String> messages = null;
	                			 if(openedChat.containsKey(sourceNodeId))
	                			 {
	                				 //Caso in cui il client ha perso la conversazione e me la richiede
//	                				 communication = ch.getChatSupport(sourceNodeId);
//	                				 communication.setDestInfo(destInfo);

	                				 communication = ch.getChatSupport(sourceNodeId);
	                				 messages = communication.getReceivedMessages();
	                				 ch.closeChatSupport(sourceNodeId);
	                			 }
//	                			 else
//	                			 {
	                				 //Costruisco la risposta
	                				 Hashtable<Integer, String[]> dest = new Hashtable<Integer, String[]>();
	                				 dest.put(sourceNodeId, newDest);
	                				 communication = new ChatCommunicationSupport(dest, cs_request.getCommPort(), userProfile, destInfo);

	                				 if(messages != null)
	                					 communication.setReceivedMessages(messages);
	                			//	 }


	                			 ChatServiceResponse cs_response = new ChatServiceResponse(nodeId, communication.getLocalPort(), cs_request.getProtocol(),
	            					 userProfile);


	                			 E2EComm.sendUnicast(
                					    newDest,
                                        cs_request.getRequestPort(),
                                        cs_request.getProtocol(),
                                        E2EComm.serialize(cs_response)
	                					 );

	                			 //Thread.sleep(1000);

	                			 if(!communication.isActive())
	                				 communication.start();
	                			 if(!openedChat.containsKey(sourceNodeId))
	                			 {
	                				 openedChat.put(sourceNodeId, communication);
	                				 notifyChatRequest("request", sourceNodeId);
	                			 }
	                         }
	                		 else if(requestType.equals("INFO"))
	                		 {
	                			 //Mando tutto lo userProfile
//	                			 E2EComm.sendUnicast(
//	                					    newDest,
//	                                        cs_request.getRequestPort(),
//	                                        cs_request.getProtocol(),
//	                                        E2EComm.serialize(userProfile)
//		                					 );

	                			 //Alternativa: mando lo stretto necessario, ovvero il mio #id: <nome, cognome>
	                			 String info = "";
	                			 info += nodeId;
	                     		 if(!userProfile.getFirstName().equals("") || !userProfile.getLastName().equals(""))
	                     			info += ": "+userProfile.getFirstName() + " " +userProfile.getLastName();

	                     		 Message messageInfo = new Message(info);
	                			 E2EComm.sendUnicast(
	                					 newDest,
	                					 cs_request.getRequestPort(),
	                					 cs_request.getProtocol(),
	                					 E2EComm.serialize(messageInfo)
             					 );
	                		 }
                         }
	                	 else if(payload.getClass() == ChatServiceGroupRequest.class)
	                	 {
	                		 System.out.println("ChatService ChatServiceGroupRequest");

	                		 ChatServiceGroupRequest cg_request = (ChatServiceGroupRequest)payload;
	                		 int groupId = cg_request.getGroupId();
	                		 int[] otherNodeId = cg_request.getOtherNodeId();
	                		 String requestType = cg_request.getRequestType();

	                		 if(requestType.equals(""))
	                         {
	                			//Costruisco la risposta
	                			Hashtable<Integer, String[]> dest = new Hashtable<Integer, String[]>();
	                			dest.put(sourceNodeId, newDest);

	                			boolean allFound = true;
	                			for (int i = 0; i < otherNodeId.length && allFound; i++)
	                			{
	                				if(otherNodeId[i] != nodeId)
	                				{
//	                					boolean currentFound = false;

//	                					for (int j = 0; j < contacts.size() && !currentFound; j++)
//	                					{
//	                						int currentServerNodeId = contacts.get(j).getServerNodeId();
//	                						if(otherNodeId[i] == currentServerNodeId)
//	                						{
//	                							currentFound = true;
//	                							dest.put(currentServerNodeId, contacts.get(j).getServerDest());
//	                						}
//	                					}

	                					Resolver resolver = Resolver.getInstance(false);
	                					Vector<ResolverPath> destinationAddr = resolver.resolveNow(otherNodeId[i]);
	                					ResolverPath bestPath = null;
	                					if (destinationAddr != null) {
	                						for (int j = 0; j < destinationAddr.size(); j++) {
	                							ResolverPath aPath = destinationAddr.elementAt(j);
	                								if (bestPath == null) {
	                									bestPath = aPath;
	                								} else if (aPath.getPath().length < bestPath.getPath().length) {
	                									// XXX hardcoded bestPath based on hop count: should be more flexible...
	                									bestPath = aPath;
	                								}
//	                							}
	                						}
	                						dest.put(otherNodeId[i], bestPath.getPath());
	                					}
	                					else
	                						allFound = false;
	                				}
	                			}

	                			if(allFound)
	                			{
	                				Hashtable<Integer, ChatServiceMessage> destInfo = new Hashtable<Integer, ChatServiceMessage>();
	                				destInfo.put(sourceNodeId, cg_request); //Aggiunte in seguito le altre destInfo...
	                				communication = new ChatCommunicationSupport(dest, cg_request.getCommPort(), userProfile, destInfo);

	                				ChatServiceResponse cs_response = new ChatServiceResponse(nodeId, communication.getLocalPort(), cg_request.getProtocol(),
	                						 userProfile);

	                				E2EComm.sendUnicast(
	                					    newDest,
	                					    cg_request.getRequestPort(),
	                					    cg_request.getProtocol(),
	                                        E2EComm.serialize(cs_response)
	                						 );

	                				//Thread.sleep(1000);
	                				openedChat.put(groupId, communication);
//	                				if(!communication.isActive())
//	                					communication.start();
	                			}

	                			 //Risp non inviata, eccezione lato client
	                         }
	                		 else if(requestType.equals("OK"))
	                		 {
	                			 if(openedChat.containsKey(groupId))
		                		 {
	                				 communication = ch.getChatSupport(groupId);
	                				 ChatServiceMessage[] otherDestInfo = cg_request.getOtherDestInfo();
	                				 for (int k = 0; k < otherDestInfo.length; k++) {
	                					 if(otherDestInfo[k].getNodeId() != nodeId)
	                						 communication.getDestInfo().put(otherDestInfo[k].getNodeId(), otherDestInfo[k]);
									 }

	                				 if(!communication.isActive())
	                					 communication.start();
	                				 notifyChatRequest("request", groupId);
		                		 }
	                		 }
	                		 else
	                		 {
	                			 //richiesta di chiusura chat
	                			 if(openedChat.containsKey(groupId))
		                		 {
	                				 communication = ch.getChatSupport(groupId);
	                				 communication.stopService(false);
	                				 openedChat.remove(groupId);
		                		 }
	                		 }
	                	}

	                 }
	                 else if(gp instanceof BroadcastPacket)
	                 {
	                	 final BroadcastPacket bp = (BroadcastPacket) gp;
	                	 Object payload = E2EComm.deserialize(bp.getBytePayload());

	                	 if(payload.getClass() == ChatServiceRequest.class)
	                	 {
	                		 ChatServiceRequest cs_request = (ChatServiceRequest)payload;
	                		 int idRequest = bp.getId();
	                		 if(!bxMessages.containsKey(idRequest))
	                		 {
	                			 //Non ho già ricevuto il messaggio
	                			 bxMessages.put(idRequest, cs_request);
	                			 notifyChatRequest("", -1);
	                		 }
	                	 }
	                 }
	                 else{
	                     // received packet is not UnicastPacket: do nothing...
	                    System.out.println("ChatServiceHandler wrong packet: "+gp.getClass().getName());
	                }

	             }
	             catch(Exception e){
	                 e.printStackTrace();
	             }
	         }
	    }
}
