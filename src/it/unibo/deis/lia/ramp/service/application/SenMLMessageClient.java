package it.unibo.deis.lia.ramp.service.application;
import it.unibo.deis.lia.ramp.core.e2e.*;

/**
 * @author Matteo Mendula
 */
public class SenMLMessageClient {

    private static SenMLMessageClient SenMLMessageClient = null;
    private static SenMLMessageClientJFrame mcj;

    private SenMLMessageClient() {
        mcj = new SenMLMessageClientJFrame(this);
    }

    public static synchronized SenMLMessageClient getInstance() {
        if (SenMLMessageClient == null) {
            SenMLMessageClient = new SenMLMessageClient();
        }
        mcj.setVisible(true);
        return SenMLMessageClient;
    }

    public void stopClient() {
        SenMLMessageClient = null;
    }

    public void sendMessage(int destNodeId, String message, int packetDeliveryTimeout) {
        Message messageObject = new Message(message);
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
