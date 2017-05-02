/*
 
* CommunicationSupport.java
 *
 * Created on 27 aprile 2010, 12.02
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.service.management.*;

import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.core.internode.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * 
 */
//public class CommunicationSupport extends Thread{
public class ChatCommunicationSupportOld extends Thread {

    private String[] dest;
    private int destNodeId;
    private ChatCommunicationSupportInterface csjf =null;
    public static int COMMUNICATION_PROTOCOL = E2EComm.TCP;
    private int destPort;//comunicata anche dopo la creazione->due costruttori
    private boolean active=false;
    private Vector<String> receivedMessages;
    private BoundReceiveSocket serviceSocket;
    private ChatServiceOld ch;
    private Hashtable<Integer, Integer> invitedContacts;
    //private Hashtable<String, String[]> invitedAddr;
    private boolean groupCommunication = false;
    //private Resolver resolver;

    public ChatCommunicationSupportOld(String[] dest, int destNodeId, int destPort, ChatServiceOld ch) {

        this.dest = dest;
        this.destNodeId = destNodeId;

        this.destPort = destPort;
        this.ch = ch;

        receivedMessages = new Vector<String>();
        //resolver = Resolver.getInstance();
        
        if (RampEntryPoint.getAndroidContext() == null&&csjf==null) {
            csjf = new ChatCommunicationSupportJFrame(this, ch.getContacts());

        } else {
        	try {
                Class<?> activityChat = Class.forName("it.unibo.deis.lia.ramp.android.service.application.ChatServiceActivity");

               
                
                
                Method mI=activityChat.getMethod("startChat", String.class);
                Method aMI = activityChat.getMethod("getInstance");

                if(mI!=null){
                	
                	mI.invoke(aMI.invoke(null, new Object[]{}), destNodeId);
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
            csjf = null;
        }
        try {
            serviceSocket = E2EComm.bindPreReceive(COMMUNICATION_PROTOCOL);
            System.out.println("CommunicationSupport creato sulla porta:" + serviceSocket.getLocalPort());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }

    public ChatCommunicationSupportOld(String[] dest, int destNodeId, ChatServiceOld ch) {

        this.dest = dest;
        this.destNodeId = destNodeId;
        this.ch = ch;
        receivedMessages = new Vector<String>();
       
        //resolver = Resolver.getInstance();
        if (RampEntryPoint.getAndroidContext() == null&&csjf==null) {
            csjf = new ChatCommunicationSupportJFrame(this, ch.getContacts());

        } else {
             try {
            	 Class<?> activityChat = Class.forName("it.unibo.deis.lia.ramp.android.service.application.ChatServiceActivity");

                 
                 
                 
                 Method mI=activityChat.getMethod("startChat", String.class);
                 Method aMI = activityChat.getMethod("getInstance");

                 if(mI!=null){
                 	
                 	mI.invoke(aMI.invoke(null, new Object[]{}), destNodeId);
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
            csjf = null;
        }

        try {
            serviceSocket = E2EComm.bindPreReceive(COMMUNICATION_PROTOCOL);
            System.out.println("CommunicationSupport creato sulla porta:" + serviceSocket.getLocalPort());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }

    public void setIterface(ChatCommunicationSupportInterface csi) {
        this.csjf = csi;
    }

    public void setDestPort(int port) {
        destPort = port;
    }

    public boolean isActive() {
        return active;
    }

    public int getLocalPort() {
        return serviceSocket.getLocalPort();
    }

    public void stopService(boolean last) {
        System.out.println("CommunicationSupport close");
        active = false;
        //invio un messaggio di notifica a tutti i contatti con cui si stava comunicando, x segnalare la chiusura
        //l'identificatore booleano serve per sapere se sono ancora attivi alcuni contatti, i quali quindi verranno avvisati 
        //della chiusura del CommunicationSupport corrente. Se invece il ComSupp corrente non ha piu nessun contatto con cui
        //comunicare, si termina solamente il CommSupp corrente
        if (!last) {
            String msg = "CLOSE:";
            try {
                boolean sent = ch.safeSendUnicast(dest, destNodeId, destPort, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
                /*
                Message messageObject = new Message(msg);
                try{
                E2EComm.sendUnicast(
                dest,
                destNodeId,
                destPort,
                CommunicationSupport.COMMUNICATION_PROTOCOL,
                false, // ack
                GenericPacket.UNUSED_FIELD, // timeoutAck
                GenericPacket.UNUSED_FIELD, // bufferSize
                500,
                messageObject
                );*/
                if (sent) {
                    System.out.println(msg + " INVIATO AL CONTATTO: " + destNodeId + "ALLA PORTA:" + destPort);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (groupCommunication) {
                try {
                    sleep(2000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                Enumeration<Integer> keys = invitedContacts.keys();
                for (; keys.hasMoreElements();) {
                    int id = keys.nextElement();
                    //String[] add=invitedAddr.get(id);
                    int port = invitedContacts.get(id);
                    try {
                        boolean sent = ch.safeSendUnicast(null, id, port, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
                        //Vector<ResolverPath> destinationAddr=resolver.resolveBlocking(id,1000);
                        //String[] destAddr=destinationAddr.elementAt(0).getPath();
                     /*
                        try{
                        E2EComm.sendUnicast(
                        add,
                        id,
                        port,
                        CommunicationSupport.COMMUNICATION_PROTOCOL,
                        false, // ack
                        GenericPacket.UNUSED_FIELD, // timeoutAck
                        GenericPacket.UNUSED_FIELD, // bufferSize
                        500,
                        messageObject
                        );*/
                        if (sent) {
                            System.out.println(msg + " INVIATO AL CONTATTO: " + id + "ALLA PORTA:" + port);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public Vector<String> getReceivedMessages() {
    	
    	return receivedMessages;
    }

    public void SendMessage(String message) {
        String msg = "COMM.MESSAGE:" + message;
        //Message messageObject = new Message(msg);

        try {
            /*
            sent=E2EComm.sendUnicast(
            dest,
            destNodeId,
            destPort,
            CommunicationSupport.COMMUNICATION_PROTOCOL,
            true, // ack
            1000, // timeoutAck
            GenericPacket.UNUSED_FIELD, // bufferSize
            500,
            messageObject
            );*/
            boolean sent = ch.safeSendUnicast(dest, destNodeId, destPort, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
            if (sent) {
                receivedMessages.addElement(Dispatcher.getLocalRampId() + ": " + message);
                System.out.println(message + " INVIATO AL CONTATTO: " + destNodeId + " ALLA PORTA:" + destPort);
            } else {
                System.out.println("msg non spedito");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (groupCommunication) {
            Enumeration<Integer> keys = invitedContacts.keys();
            try {
                sleep(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            for (; keys.hasMoreElements();) {
                msg = "COMM.MESSAGE:" + message;
                //messageObject = new Message(msg);
                int id = keys.nextElement();
                //String[] add=invitedAddr.get(id);
                int port = invitedContacts.get(id);
                //Vector<ResolverPath> destinationAddr=resolver.resolveBlocking(id,1000);
                //String[] destAddr=destinationAddr.elementAt(0).getPath();
                try {
                    /*
                    E2EComm.sendUnicast(
                    add,
                    id,
                    port,
                    CommunicationSupport.COMMUNICATION_PROTOCOL,
                    false, // ack
                    GenericPacket.UNUSED_FIELD, // timeoutAck
                    GenericPacket.UNUSED_FIELD, // bufferSize
                    500,
                    messageObject
                    );*/
                    boolean sent = ch.safeSendUnicast(null, id, port, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
                    if (sent) {
                        System.out.println(message + " INVIATO " + "AL CONTATTO " + id + " ALLA PORTA:" + invitedContacts.get(id));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void InviteContact(int contact) throws Exception {

        if (invitedContacts == null) {
            invitedContacts = new Hashtable<Integer, Integer>();
            //invitedAddr=new Hashtable<String,String[]>();
        } else if (invitedContacts.containsKey(contact) || destNodeId==contact) {
            System.out.println("il contatto selezionato e gia stato aggiunto alla comunicazione di gruppo");
        } else {

            groupCommunication = true;

            //1- invio un msg al chatService di contact per avviare CommunicationSupport con il nodo corrente:
            //si dovro' avviare solo CommSupp sul pari xche' in locale e' gia' avviato. Si ricevera' la porta del pari e la si aggiungera'
            //tra le porte dei contatti invitati

            String[] destAddr = null;
            ServiceResponse response = null;
            int port;
            System.out.println("stampo la lista di indirizzi per raggiungere " + contact + ": ");
            //Vector<ResolverPath> destinationAddr=resolver.resolveBlocking(destId,1500);
            Vector<ResolverPath> destinationAddr = Resolver.getInstance(false).resolveBlocking(contact, 1000);
            if (destinationAddr == null) {
                System.out.println("il contatto richiesto e offline");
            }

            destAddr = destinationAddr.elementAt(0).getPath();
            for (int count = 0; count < destAddr.length; count++) {
                System.out.println(" ; " + destAddr[count]);
            }

            try {
                response = ServiceDiscovery.findService(destAddr, "ChatService");
            } catch (Exception ex) {
                System.out.println("il contatto richiesto e offline");
            }
            if (response != null) {
                System.out.println("il contatto richiesto e online");
            }

            port = response.getServerPort();//ottengo la porta del ChatSvc del destinatario

            //Message messageObject;
            //Vector<ResolverPath> destinationAddr;
            BoundReceiveSocket serviceSocket2 = E2EComm.bindPreReceive(ChatServiceOld.CHAT_PROTOCOL);
            String msg = "COMM.REQ:" + serviceSocket2.getLocalPort() + ":" + getLocalPort();//porta a cui inviare la risposta:porta delCommSupp del mittente
            //messageObject = new Message(msg); 
            try {
                /*
                E2EComm.sendUnicast(
                destAddr,
                contact,
                port,
                ChatService.CHAT_PROTOCOL,
                false, // ack
                GenericPacket.UNUSED_FIELD, // timeoutAck
                GenericPacket.UNUSED_FIELD, // bufferSize
                1000,
                messageObject
                );*/
                ch.safeSendUnicast(destAddr, contact, port, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                //ricevo la porta del CommSupp del contatto che si vuole invitare, su una porta differente da quella usata per le richieste di conn.

                System.out.println("Ricevo alla porta:" + serviceSocket2.getLocalPort());
                //invitedAddr.put(contact,destAddr);
                GenericPacket gp = E2EComm.receive(serviceSocket2);
                new MessageHandler2(gp).start();//aggiunge la porta ricevuta in invitedContacts
                sleep(700);

            } catch (SocketTimeoutException ste) {
                ste.printStackTrace();
            }
            serviceSocket2.close();
            //2- invio a contact la lista di NodeId e le rispettive porte gia' inserite nella comunicazione di gruppo

            msg = "INVITEMESSAGE:" + destNodeId + ":" + destPort;//nodeId e porta del primo contatto con cui si e' instaurata la comunicazione
            //messageObject = new Message(msg); 
            port = invitedContacts.get(contact);
            try {
                /*
                E2EComm.sendUnicast(
                destAddr,
                contact,
                port,
                ChatService.CHAT_PROTOCOL,
                false, // ack
                GenericPacket.UNUSED_FIELD, // timeoutAck
                GenericPacket.UNUSED_FIELD, // bufferSize
                1000,
                messageObject
                );*/
                ch.safeSendUnicast(destAddr, contact, port, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (invitedContacts.size() > 1) //comunico in un unico messaggio, tutti gli altri contatti gia invitati con le relative informazioni
            {
                Enumeration<Integer> keys = invitedContacts.keys();
                msg = "INVITEMESSAGE:";
                for (; keys.hasMoreElements();) {
                    int id = keys.nextElement();
                    if (!(id==contact)) {
                        msg = msg + id + ":" + invitedContacts.get(id) + ":";
                    }

                }
                //messageObject = new Message(msg);
                try {
                    /*
                    E2EComm.sendUnicast(
                    destAddr,
                    contact,
                    port,
                    ChatService.CHAT_PROTOCOL,
                    false, // ack
                    GenericPacket.UNUSED_FIELD, // timeoutAck
                    GenericPacket.UNUSED_FIELD, // bufferSize
                    1000,
                    messageObject
                    );*/
                    ch.safeSendUnicast(destAddr, contact, port, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //3-comunico a tutti i contatti gia' invitati, l'inserimento di un nuovo contatto'

            msg = "INVITEMESSAGE:" + contact + ":" + invitedContacts.get(contact);
            //messageObject = new Message(msg);
            try { //effettuo la comunicazione al primo contatto aggiunto, con cui e' stata creata la sessione di comunicazione
                /*
                E2EComm.sendUnicast(
                dest,
                destNodeId,
                destPort,
                ChatService.CHAT_PROTOCOL,
                false, // ack
                GenericPacket.UNUSED_FIELD, // timeoutAck
                GenericPacket.UNUSED_FIELD, // bufferSize
                1500,
                messageObject
                );*/
                ch.safeSendUnicast(dest, destNodeId, destPort, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (invitedContacts.size() > 1)//se sono gia stati invitati altri contatti,notifico ad ognuno l'aggiunta del nuovo contatto
            {
                Enumeration<Integer> keys = invitedContacts.keys();
                for (; keys.hasMoreElements();) {//per ogni contatto gia invitato, notifico l'aggiunta di un nuovo contatto al gruppo
                    int id = keys.nextElement();
                    if (!(id==contact)) {//non invio le informazioni allo stesso contatto appena aggiunto

                        //destinationAddr=resolver.resolveBlocking(id,1500);
                        //destAddr=destinationAddr.elementAt(0).getPath();
                        //destAddr=invitedAddr.get(id);
                        port = invitedContacts.get(id);

                        msg = "INVITEMESSAGE:" + contact + ":" + invitedContacts.get(contact);
                        //messageObject = new Message(msg);
                        try {
                            /*
                            E2EComm.sendUnicast(
                            destAddr,
                            id, 
                            port,
                            ChatService.CHAT_PROTOCOL,
                            false, // ack
                            GenericPacket.UNUSED_FIELD, // timeoutAck
                            GenericPacket.UNUSED_FIELD, // bufferSize
                            1500,
                            messageObject
                            );*/
                            ch.safeSendUnicast(null, id, port, ChatCommunicationSupportOld.COMMUNICATION_PROTOCOL, msg);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
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
                    new MessageHandler1(gp).start();
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

    private class MessageHandler1 extends Thread {

        private GenericPacket gp;

        private MessageHandler1(GenericPacket gp) {
            this.gp = gp;
        }

        @Override
        public void run() {
            try {
                // check packet type
                if (gp instanceof UnicastPacket) {

                    UnicastPacket up = (UnicastPacket) gp;
                    UnicastHeader uh = up.getHeader();
                    int sourceNodeId = uh.getSourceNodeId();
                    //String[] source=uh.getSource();
                    // check payload
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if (payload instanceof Message) {
                        //System.out.println("MessageService.MessageHandler");
                        Message messageObject = (Message) payload;
                        String message = messageObject.getMessage();
                        System.out.println("CommunicationSupport: received message: " + message);
                        StringTokenizer st = new StringTokenizer(message, ":");
                        String intro = st.nextToken();
                        if (intro.equals("COMM.MESSAGE")) {
                            receivedMessages.addElement(sourceNodeId + ": " + st.nextToken());
                            System.out.println("CommunicationSupport.MessageHandler message: " + message);
                        } else if (intro.equals("INVITEMESSAGE"))//tramite questo messaggio, viene comunicato uno o piu nodeId
                        {                                   //con le relative porte, che partecipano alla comunicazione di gruppo 
                            if (invitedContacts == null) {
                                invitedContacts = new Hashtable<Integer, Integer>();
                                //invitedAddr=new Hashtable<String,String[]>();
                            }
                            groupCommunication = true;
                            String contactId = st.nextToken();
                            int contactPort = Integer.parseInt(st.nextToken());

                            invitedContacts.put(contactId.hashCode(), contactPort);
                            /*
                            Vector<ResolverPath> destinationAddr=resolver.resolveBlocking(contactId,500);
                            String[] addr=destinationAddr.elementAt(0).getPath();
                            invitedAddr.put(contactId,addr);
                             */
                            System.out.println("Communication Support: il contatto " + contactId + ":" + contactPort + " e stato aggiunto alla comunicazione di gruppo");
                            if (message.endsWith(":"))//se sono comunicati piu contatti contemporaneamente
                            {
                                while (st.hasMoreTokens()) {

                                    contactId = st.nextToken();
                                    contactPort = Integer.parseInt(st.nextToken());

                                    invitedContacts.put(contactId.hashCode(), contactPort);
                                    /*
                                    destinationAddr=resolver.resolveBlocking(contactId,500);
                                    addr=destinationAddr.elementAt(0).getPath();
                                    invitedAddr.put(contactId,addr);
                                     */
                                    System.out.println("Communication Support: il contatto " + contactId + ":" + contactPort + " e stato aggiunto alla comunicazione di gruppo");
                                }
                            }
                        } else if (intro.equals("CLOSE")) {
                            System.out.println("CommunicationSupport: " + sourceNodeId + " ha terminato la sessione di comunicazione");
                            if (groupCommunication) {
                                if (invitedContacts.containsKey(sourceNodeId)) {
                                    invitedContacts.remove(sourceNodeId);
                                    //invitedAddr.remove(sourceNodeId);
                                } else if (destNodeId==sourceNodeId) {//setto come contatto principale, il primo dei contatti invitati
                                    destNodeId = invitedContacts.keys().nextElement();
                                    destPort = invitedContacts.get(destNodeId);
                                    //dest=invitedAddr.get(destNodeId);
                                    dest = ch.getContactAddress(destNodeId);
                                    invitedContacts.remove(destNodeId);
                                    //invitedAddr.remove(destNodeId);
                                }
                                if (invitedContacts.size() == 0) {
                                    groupCommunication = false;
                                    System.out.println("Comunicazione di gruppo terminata");
                                }

                            } else if (destNodeId==sourceNodeId) {
                                stopService(true);
                                csjf.stopInteface();
                            }

                        }
                    } else {
                        // received payload is not Message: do nothing...
                        System.out.println("CommunicationSupport.MessageHandler wrong payload: " + payload);
                    }
                } else {
                    // received packet is not UnicastPacket: do nothing...
                    System.out.println("CommunicationSupport.MessageHandler wrong packet: " + gp.getClass().getName());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class MessageHandler2 extends Thread {

        private GenericPacket gp;

        private MessageHandler2(GenericPacket gp) {
            this.gp = gp;
        }

        @Override
        public void run() {
            try {
                // check packet type
                if (gp instanceof UnicastPacket) {

                    UnicastPacket up = (UnicastPacket) gp;
                    UnicastHeader uh = up.getHeader();
                    int sourceNodeId = uh.getSourceNodeId();
                    // check payload
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if (payload instanceof Message) {
                        //System.out.println("MessageService.MessageHandler");
                        Message messageObject = (Message) payload;
                        int port = Integer.parseInt(messageObject.getMessage());
                        invitedContacts.put(sourceNodeId, port);
                        System.out.println("INVITE CONTACTS: " + sourceNodeId + " legato alla porta " + port + " invitato");
                        //receivedMessages.addElement(destNodeId+": "+message);
                        //System.out.println("CommunicationSupport.MessageHandler message: "+message);
                    } else {
                        // received payload is not Message: do nothing...
                        System.out.println("CommunicationSupport.MessageHandler wrong payload: " + payload);
                    }
                } else {
                    // received packet is not UnicastPacket: do nothing...
                    System.out.println("CommunicationSupport.MessageHandler wrong packet: " + gp.getClass().getName());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
