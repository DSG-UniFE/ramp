/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.*;

/**
 *
 * @author Carlo Giannelli
 */
public class MessageClient{

    private static MessageClient messageClient=null;
    private static MessageClientJFrame mcj;
    private MessageClient(){
        mcj = new MessageClientJFrame(this);
    }
    public static synchronized MessageClient getInstance(){
        if(messageClient==null){
            messageClient=new MessageClient();
        }
        mcj.setVisible(true);
        return messageClient;
    }
    public void stopClient(){
        messageClient=null;
    }

    public void sendMessage(int destNodeId, String message, int packetDeliveryTimeout){
        Message messageObject = new Message(message);
        try{
            E2EComm.sendUnicast(
                    null,
                    destNodeId,
                    MessageService.MESSAGGE_PORT,
                    MessageService.MESSAGE_PROTOCOL,
                    false, // ack
                    GenericPacket.UNUSED_FIELD, // timeoutAck
                    GenericPacket.UNUSED_FIELD, // bufferSize
                    packetDeliveryTimeout,
                    GenericPacket.UNUSED_FIELD, // connectTimeout
                    E2EComm.serialize(messageObject)
            );
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}
