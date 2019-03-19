package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

import it.unibo.deis.lia.ramp.util.NodeStats;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessage implements Serializable {
    private static final long serialVersionUID = -3323203535555375193L;

    public static final int UNUSED_FIELD = -1;

    private MessageType messageType;

    private int clientPort;

    /**
     * Data structure to hold node stats (address, nodeStats)
     */
    private Map<String, NodeStats> nodeStats;

    /**
     * JOIN_SERVICE message: messageType, clientPort
     * LEAVE_SERVICE message: messageType, nodeStats
     */
    public ControllerMessage(MessageType messageType, int clientPort,  Map<String, NodeStats> nodeStats) {
        this.messageType = messageType;
        this.clientPort = clientPort;
        this.nodeStats = nodeStats;
    }

    public ControllerMessage(MessageType messageType, int clientPort) {

        this(messageType, clientPort, null);
    }

    public ControllerMessage(MessageType messageType, Map<String, NodeStats> nodeStats) {

        this(messageType, UNUSED_FIELD, nodeStats);
    }

    public ControllerMessage(MessageType messageType) {

        this(messageType, UNUSED_FIELD, null);
    }

    public MessageType getMessageType() {
        return this.messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public int getClientPort() {
        return this.clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public Map<String, NodeStats> getNodeStats() {
        return this.nodeStats;
    }

    public void setNodeStats(Map<String, NodeStats> nodeStats) {
        this.nodeStats = nodeStats;
    }
}

