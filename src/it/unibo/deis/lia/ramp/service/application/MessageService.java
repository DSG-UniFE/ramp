/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.*;

import java.net.*;
import java.util.*;

/**
 *
 * @author useruser
 */
public class MessageService extends Thread{

    private boolean active = true;
    private BoundReceiveSocket serviceSocket;
    private Vector<String> receivedMessages;

    //public static int MESSAGE_PROTOCOL = E2EComm.UDP;
    public static int MESSAGE_PROTOCOL = E2EComm.TCP;
    public static int MESSAGGE_PORT=3300;

    private static MessageService messageService=null;
    private static MessageServiceJFrame msjf;
    public static synchronized MessageService getInstance(){
        if(messageService==null){
            messageService=new MessageService();
            messageService.start();
        }
        msjf.setVisible(true);
        return messageService;
    }
    private MessageService(){
        receivedMessages = new Vector<String>();
        msjf = new MessageServiceJFrame(this);
    }
    public void stopService(){
        active=false;
        try {
            serviceSocket.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        messageService=null;
    }
    public boolean isActive(){
        return active;
    }

    public Vector<String> getReceivedMessages() {
        return receivedMessages;
    }
    public void resetReceivedMessages(){
        receivedMessages = new Vector<String>();
    }
    
    @Override
    public void run(){
        try{
            System.out.println("MessageService START");
            serviceSocket = E2EComm.bindPreReceive(MessageService.MESSAGGE_PORT, MESSAGE_PROTOCOL);
            while(active){
                try{
                    // receive
                    GenericPacket gp=E2EComm.receive(serviceSocket, 5*1000);

                    System.out.println("MessageService new request");
                    new MessageHandler(gp).start();
                }
                catch(SocketTimeoutException ste){
                    //
                }
            }
            serviceSocket.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("MessageService FINISHED");
    }


    private class MessageHandler extends Thread{
        private GenericPacket gp;
        private MessageHandler(GenericPacket gp){
            this.gp=gp;
        }
        @Override
        public void run(){
            try{
                // check packet type
                if( gp instanceof UnicastPacket){
                    // check payload
                    UnicastPacket up = (UnicastPacket)gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if(payload instanceof Message){
                        //System.out.println("MessageService.MessageHandler");
                        Message messageObject = (Message)payload;
                        String message = messageObject.getMessage();
                        receivedMessages.addElement(message);
                        System.out.println("MessageService.MessageHandler message: "+message);
                    }
                    else{
                        // received payload is not Message: do nothing...
                        System.out.println("MessageService.MessageHandler wrong payload: "+payload);
                    }
                }
                else{
                     // received packet is not UnicastPacket: do nothing...
                    System.out.println("MessageService.MessageHandler wrong packet: "+gp.getClass().getName());
                }
                
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}
