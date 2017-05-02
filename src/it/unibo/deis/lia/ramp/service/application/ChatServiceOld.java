
//
/*
 * ChatServiceOld.java
 *
 * Created on 15 aprile 2010, 17.18
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.*;
import it.unibo.deis.lia.ramp.service.management.*;
import it.unibo.deis.lia.ramp.core.e2e.*;
//import it.unibo.deis.lia.ramp.service.application.*;

import java.io.*;
//import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

//import android.R.string;


/**
 *
 *
 */
public class ChatServiceOld extends Thread{

    private static BoundReceiveSocket serviceSocket;
    public static int CHAT_PROTOCOL = E2EComm.TCP;
    public static int CHAT_PORT;
    private boolean active = true;
    private static final String contactfile="./temp/lista_contatti.txt";

    //private ServiceManager serviceManager;

    //creo secureSendUnicast che fa invio sicuro: con ack che se false, fa Resolver e riinvia(quindi gli altri metodi useranno questo)

    private static ChatServiceOld chat=null;
    private ChatInterface rsc;
    //private Resolver resolver=null;
    private String[] contacts=null;
    private Hashtable<Integer, String[]> contactsAddr;
    private Hashtable<Integer, Long> timeAddr;//istante in cui ho calcolato l'indirizzo'
    private Hashtable<Integer, ChatCommunicationSupportOld> openedChat;
    private String myStatus="";


	public static synchronized ChatServiceOld getInstance(){
        try{
            if(chat==null){
                chat=new ChatServiceOld();
                //Dispatcher.setLocalNodeId("portatile");
                chat.start();
            }

        }
        catch(Exception e){
            e.printStackTrace();
        }
        return chat;
    }
    private ChatServiceOld() throws Exception{

        //this.resolver=Resolver.getInstance();
        //receivedMessages = new Vector<String>();
        //this.serviceManager=ServiceManager.getInstance();
        serviceSocket = E2EComm.bindPreReceive(CHAT_PROTOCOL);//relativa all'ascolto di richieste di connessione'
        CHAT_PORT=serviceSocket.getLocalPort();
        contactsAddr=new Hashtable<Integer,String[]>();
        timeAddr=new Hashtable<Integer,Long>();
        openedChat=new Hashtable<Integer, ChatCommunicationSupportOld>();
        System.out.println("ChatService legato alla porta: "+CHAT_PORT);
        ServiceManager.getInstance(false).registerService("ChatService", CHAT_PORT,CHAT_PROTOCOL);
        if(RampEntryPoint.getAndroidContext()==null){
            myStatus="ONLINE";
        	rsc=new ChatServiceJFrame(this);
new SendStatusHendler(myStatus).start();
        }
        else{
//           Class c = Class.forName("it.unibo.deis.lia.ramp.service.application.ChatServiceActivity");
//           Method mI = c.getMethod("getInstance");
//           rsc=(ChatInterface)mI.invoke(null, new Object[]{});
            //sharedDirectory = "/sdcard/ramp";
            myStatus="ONLINE";
        	rsc=null;//da settare da android tramite il metodo setInterface
new SendStatusHendler(myStatus).start();
        }

    }

    public void setInterface(ChatInterface rsc){
        this.rsc=rsc;
    }

    public static boolean isActive(){
        return ChatServiceOld.chat != null;
    }
    public String getMyStatus() {
		return myStatus;
	}
	public void setMyStatus(String myStatus) {
		this.myStatus = myStatus;
		sendStatus(null);
	}

    public String[] getContacts(){
        int length=contacts.length;
        String[] result=new String[length];
        for(int count=0;count<length;count++)
            result[count]=contacts[count];
        return result;

    }

    public String[] getContactAddress(int id){
        return contactsAddr.get(id);
    }

    public void loadContacts(String[] contacts){
        this.contacts=contacts;
    }


    public void loadContacts(){
        //Viene caricata una lista con tutti i contatti dell'utente
        File f = new File(contactfile);
	       if (!f.exists() || f.isDirectory())
			System.out.println("il file lista_contatti.txt e irraggiungibile");
               else{
		BufferedReader br = null;
                String line;
                int count=0;
		try {
			br = new BufferedReader( new InputStreamReader( new FileInputStream(f) ) );
			while( (line=br.readLine()) != null ) {
                        count++;
			}
                        contacts=new String[count];
                        count=0;
                        br = new BufferedReader( new InputStreamReader( new FileInputStream(f) ) );
			while( (line=br.readLine()) != null ) {
                        contacts[count]=line;
                        count++;
			}
                }catch(Exception e){e.printStackTrace();}
                }
    }

    public void stopService(){
        System.out.println("ChatService close");
        rsc.visible(false);
        active=false;
        sendStatus("OFFLINE");
        ServiceManager.getInstance(false).removeService("ChatService");
        chat = null;
    }

    public boolean safeSendUnicast(String[] destAddr, int destNodeId, int destPort, int protocol, String msg) throws Exception{


        boolean res=false;
        if(destAddr==null)
        {
            if(!contactsAddr.containsKey(destNodeId) || (contactsAddr.containsKey(destNodeId) && System.currentTimeMillis()-timeAddr.get(destNodeId)>20000))//se l'indirizzo non e' stato memorizzato oppure e' scaduto'
              {
                  System.out.println("ChatService.safeSendUnicast: address not stored or expired");
                  Vector<ResolverPath> destinationAddr=Resolver.getInstance(false).resolveBlocking(destNodeId,1000);
                  if(destinationAddr==null)
                  {
                      System.out.println("the requested contact is offline");
                      return false;
                  }
                  destAddr=destinationAddr.elementAt(0).getPath();
                  contactsAddr.put(destNodeId,destAddr);
                  timeAddr.put(destNodeId,System.currentTimeMillis());
              }
              else
              {
                  long t=System.currentTimeMillis()-timeAddr.get(destNodeId);
                  System.out.println("ChatService.safeSendUnicast: the stored address is valid. Seconds passed from last update: "+t);
                  destAddr=contactsAddr.get(destNodeId);
              }
        }
        if(destPort==-1) //solo nel caso di sendStatus----scopre laporta
        {
            ServiceResponse response=null;
            response=ServiceDiscovery.findService(destAddr,"ChatService");
            destPort=response.getServerPort();
        }

        try{
            Message messageObject = new Message(msg);
            boolean sent;
                    sent=E2EComm.sendUnicast(
                            destAddr,
                            destNodeId,
                            destPort,
                            protocol,
                            true, // ack
                            2000, // timeoutAck
                            GenericPacket.UNUSED_FIELD, // bufferSize
                            500, // TODO why???
                            GenericPacket.UNUSED_FIELD,
                            E2EComm.serialize(messageObject)
                    );
                    if(sent)
                        return true;
                    else
                    {
                        Vector<ResolverPath> destinationAddr=Resolver.getInstance(false).resolveBlocking(destNodeId,1000);
                        if(destinationAddr==null)
                        {
                            System.out.println("the requested contact is offline");
                            return false;
                        }
                        destAddr=destinationAddr.elementAt(0).getPath();
                        contactsAddr.put(destNodeId,destAddr);
                        timeAddr.put(destNodeId,System.currentTimeMillis());

                        sent=E2EComm.sendUnicast(
                            destAddr,
                            destNodeId,
                            destPort,
                            protocol,
                            true, // ack
                            1000, // timeoutAck
                            GenericPacket.UNUSED_FIELD, // bufferSize
                            500, // TODO why???
                            GenericPacket.UNUSED_FIELD,
                            E2EComm.serialize(messageObject)
                         );
                        if(sent)
                            return true;
                        else{
                            System.out.println("Unable to send message: "+msg);
                            return false;
                        }
                    }
                }
                catch(Exception e){
                    e.printStackTrace();
                }
        return res;
    }

    public void startCommunication(int destId) throws Exception
    {

          String [] destAddr=null;
          ServiceResponse response=null;
          int destPort;

          System.out.println("stampo la lista di indirizzi per raggiungere "+destId+": ");
          if(!contactsAddr.containsKey(destId) || (contactsAddr.containsKey(destId) && System.currentTimeMillis()-timeAddr.get(destId)>10000))//se l'indirizzo non e' stato memorizzato oppure e' scaduto'
          {
              System.out.println("indirizzo non memorizzato o scaduto");
              Vector<ResolverPath> destinationAddr=Resolver.getInstance(false).resolveBlocking(destId,1000);
              if(destinationAddr==null)
                  System.out.println("il contatto richiesto e offline");
              else
              {
              destAddr=destinationAddr.elementAt(0).getPath();
              contactsAddr.put(destId,destAddr);
              timeAddr.put(destId,System.currentTimeMillis());
              }
          }
          else
          {
              long t=System.currentTimeMillis()-timeAddr.get(destId);
              System.out.println("indirizzo memorizzato valido "+t);
              destAddr=contactsAddr.get(destId);
          }

           for(int count2=0;count2<destAddr.length;count2++)
           {
            System.out.println(" ; "+destAddr[count2]);
           }

          try {
               response=ServiceDiscovery.findService(destAddr,"ChatService");
          } catch (Exception ex) {
                System.out.println("il contatto richiesto e offline");
           }
          if(response!=null)
               System.out.println("il contatto richiesto e online");


//1-creo commsupp locale che fa subito bindPreReceive e quindi ottengo la porta a cui ha fatto bind
//2-invio richiesta di connessione al pari passandogli la porta a cui inviare il messaggio di risposta e la porta a cui inviera i messaggi di comunicazione
//3-IL PARI: riceve richiesta di connessione, crea CommSupp con gli opportuni parametri settando come porta del destinatario la porta ricevuta, successivamente il CommSupport fa bindPreReceive
//creando la socket da cui ricevere' i messaggi di comunicazione e quindi viene inviato dal ChatSvc un messaggio di risposta al ChatSvc mittente con la porta usata da bind del proprio communication support
//4-DI NUOVO MITTENTE: riceve risposta dal pari,estrae la porta del CommSupp destinatario e la comunica al proprio CommSupp
//5-avvio CommSupp locale

                //1
                destPort=response.getServerPort();//ottengo la porta del ChatSvc del destinatario
                System.out.println("ChatSvc peer port:"+destPort);
                //boolean isAlreadyOpened=false;
                ChatCommunicationSupportOld communication;
                if(!openedChat.containsKey(destId)){
                	System.out.println("ChatService: entro qui dovve non dovrei entrare");
                	communication=new ChatCommunicationSupportOld(destAddr,destId,this);
               startChat(destId, communication);}
                else{

                	communication = getChatSupport(destId);
                	if (RampEntryPoint.getAndroidContext() != null){
                		try {
                            Class<?> activityChat = Class.forName("it.unife.dsg.ramp.android.service.application.ChatServiceActivity");




                            Method mI=activityChat.getMethod("startChat", String.class);
                            Method aMI = activityChat.getMethod("getInstance");

                            if(mI!=null){

                            	mI.invoke(aMI.invoke(null, new Object[]{}), destId);
                            }

                        } catch (IllegalAccessException ex) {
                            Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IllegalArgumentException ex) {
                            Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (InvocationTargetException ex) {
                            Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (NoSuchMethodException ex) {
                            Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (SecurityException ex) {
                            Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ClassNotFoundException ex) {
                            Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                        }
                	}
                	//isAlreadyOpened=true;
                }
                //ricevo la porta del destinatario su una porta differente da quella usata per le richieste di conn.

                BoundReceiveSocket serviceSocket2=E2EComm.bindPreReceive(CHAT_PROTOCOL);
                String msg="COMM.REQ:"+serviceSocket2.getLocalPort()+":"+communication.getLocalPort();//porta a cui inviare la risposta:porta delCommSupp del mittente
                //Message messageObject = new Message(msg);
                //2
                    safeSendUnicast(
                            destAddr,
                            destId,
                            destPort,
                            ChatServiceOld.CHAT_PROTOCOL,
                            msg
                    );

                  try{
                    //ricevo la porta del CommunicationSupport destinatario su una porta differente da quella usata per le richieste di conn.

                    System.out.println("Ricevo alla porta:"+serviceSocket2.getLocalPort());
                    GenericPacket gp=E2EComm.receive(serviceSocket2);
                    //System.out.println("ChatService new communication request");
                    new MessageHandler2(gp,communication).start();//4-5


                }
                catch(SocketTimeoutException ste){
                    ste.printStackTrace();
                }
                serviceSocket2.close();
                System.out.println("serviceSocket2 closed!");

    }

    private  class SendStatusHendler extends Thread{
        private String status=null;

        private SendStatusHendler(String status) {
			this.status=status;
		}
       public void run(){
    	if(status==null)
    		status=myStatus;
    	//ServiceResponse response=null;
       if(contacts!=null)
       {
        for(int count=0;count<contacts.length;count++)
        {
         System.out.println("Sending Status to: "+contacts[count]);
         String msg="STATUS:"+status;
                boolean sent=false;
                try {
                    sent = safeSendUnicast(null,contacts[count].hashCode(), -1, ChatServiceOld.CHAT_PROTOCOL, msg);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                if(sent)
                    System.out.println("Stato: "+status+" inviato a "+contacts[count]);
                        try {
                            sleep(2000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
            // }
          //}

        }
     }
       }
    }

        public void run(){
        try{
            System.out.println("ChatService START");

            while(active){
                try{

                    GenericPacket gp=E2EComm.receive(serviceSocket);
                    new HandlerConnectionStatus(gp,this).start();//ricevo sourcePort, crea CommSupp e invio a source la localPort
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

//usata in fase di ricezione di richieste di connessione o per ricevere un messaggio indicante lo stato del contatto mittente
    private class HandlerConnectionStatus extends Thread{
        private GenericPacket gp;
        private ChatServiceOld ch;
        private HandlerConnectionStatus(GenericPacket gp,ChatServiceOld ch){
            this.gp=gp;
            this.ch=ch;
        }
        @Override
        public void run(){
            try{
                // check packet type
                if( gp instanceof UnicastPacket){

                    UnicastPacket up=(UnicastPacket)gp;
                    // check header
                    UnicastHeader uh=up.getHeader();
                    String[] source=uh.getSource();
                    int sourceNodeId=uh.getSourceNodeId();

                    // check payload
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if(payload instanceof Message){
                        //System.out.println("MessageService.MessageHandler");
                        Message messageObject = (Message)payload;
                        String message = messageObject.getMessage();
                        String intro;
                        System.out.println("ChatService: "+message+" ricevuto da "+sourceNodeId);
                        StringTokenizer st = new StringTokenizer(message,":");
                        intro=st.nextToken();
                        if(intro.equals("STATUS"))
                        {
                            String status=st.nextToken();
                            System.out.println("Ricevuto cambio di stato in ramp");
                            ch.rsc.setStatus(sourceNodeId,status);
                        }
                        else if(intro.equals("YOURSTATUS")){
                        	String msg="STATUS:"+myStatus;

                            try {
                                safeSendUnicast(null,sourceNodeId, -1, ChatServiceOld.CHAT_PROTOCOL, msg);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                        else if(intro.equals("COMM.REQ"))
                        {
                            int destCommPort;
                            int answerPort;
                            System.out.println("ChatService new communication request");
                            answerPort=Integer.parseInt(st.nextToken());//porta a cui inviare il messaggio di risposta
                            destCommPort=Integer.parseInt(st.nextToken());//porta a cui e' legato il CommSupp del pari

                            System.out.println("ChatService.MessageHandler1 destCommPort: "+destCommPort+" toAnswerPort: "+answerPort);
                            ChatCommunicationSupportOld communication;
                            if(!openedChat.containsKey(sourceNodeId)){
                           communication=new ChatCommunicationSupportOld(E2EComm.ipReverse(source),sourceNodeId,destCommPort,ch);
                           startChat(sourceNodeId, communication);}
                            else{
                            	communication=getChatSupport(sourceNodeId);
                            	if (RampEntryPoint.getAndroidContext() != null){
                            		try {
                                        Class<?> activityChat = Class.forName("it.unife.dsg.ramp.android.service.application.ChatServiceActivity");




                                        Method mI=activityChat.getMethod("startChat", String.class);
                                        Method aMI = activityChat.getMethod("getInstance");

                                        if(mI!=null){

                                        	mI.invoke(aMI.invoke(null, new Object[]{}), sourceNodeId);
                                        }

                                    } catch (IllegalAccessException ex) {
                                        Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                                    } catch (IllegalArgumentException ex) {
                                        Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                                    } catch (InvocationTargetException ex) {
                                        Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                                    } catch (NoSuchMethodException ex) {
                                        Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                                    } catch (SecurityException ex) {
                                        Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                                    } catch (ClassNotFoundException ex) {
                                        Logger.getLogger(ChatCommunicationSupportOld.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                            	}
                            	}
                            String msg=""+communication.getLocalPort(); //ottengo il num di porta su cui commSupp locale ha legato la propria socket
                            //invio un messaggio di risposta al mittente della richiesta di comunicazione, inviandogli
                            //la porta a cui si e' legato il CommunicationSupport locale
                            try{
                                safeSendUnicast(E2EComm.ipReverse(source),sourceNodeId, answerPort, ChatServiceOld.CHAT_PROTOCOL, msg);
                                /*
                                messageObject = new Message(msg);
                                E2EComm.sendUnicast(
                                        E2EComm.ipReverse(source),
                                        sourceNodeId,
                                        answerPort,
                                        ChatService.CHAT_PROTOCOL,
                                        false, // ack
                                        GenericPacket.UNUSED_FIELD, // timeoutAck
                                        GenericPacket.UNUSED_FIELD, // bufferSize
                                        1500,
                                        messageObject
                                ); */
                                Thread.sleep(1000);
                                if(!communication.isActive())
                                communication.start();

                            }
                            catch(Exception e){
                                e.printStackTrace();
                            }

                       }
                    }
                    else{
                        // received payload is not Message: do nothing...
                        System.out.println("ChatService.MessageHandler1 wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("ChatService.MessageHandler1 wrong packet: "+gp.getClass().getName());
                }

            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //usata da StartCommunication per ricevere la porta a cui e' legato il CommSupp del pari e quindi avviare CommSupp locale
    private class MessageHandler2 extends Thread{
        private GenericPacket gp;
        private ChatCommunicationSupportOld cs;

        private MessageHandler2(GenericPacket gp,ChatCommunicationSupportOld cs){
            this.gp=gp;
            this.cs=cs;
        }
        @Override
        public void run(){
            try{
                // check packet type
                if( gp instanceof UnicastPacket){

                    UnicastPacket up=(UnicastPacket)gp;
                    /* // check header
                    UnicastHeader uh=up.getHeader();
                    String[] source=E2EComm.ipReverse(uh.getSource());
                    String sourceNodeId=uh.getSourceNodeId();
                    */
                    // check payload
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if(payload instanceof Message){
                        //System.out.println("MessageService.MessageHandler");
                        Message messageObject = (Message)payload;
                        String message = messageObject.getMessage();
                        int destPort=Integer.parseInt(message);
                        if(!cs.isActive()){
                        cs.setDestPort(destPort);

                        cs.start();}
                        System.out.println("ChatService.MessageHandler2 destPort: "+destPort);
                    }
                    else{
                        // received payload is not Message: do nothing...
                        System.out.println("ChatService.MessageHandler2 wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("ChatService.MessageHandler2 wrong packet: "+gp.getClass().getName());
                }

            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }

	public void sendStatus(String status) {
		new SendStatusHendler(status).start();

	}
	public void startChat(int user, ChatCommunicationSupportOld support){
		if(!openedChat.containsKey(user))
		openedChat.put(user, support);
	}

	public ChatCommunicationSupportOld getChatSupport(int user){
		return openedChat.get(user);
	}
	public void stopChat(String user){
		openedChat.remove(user);
	}




}
