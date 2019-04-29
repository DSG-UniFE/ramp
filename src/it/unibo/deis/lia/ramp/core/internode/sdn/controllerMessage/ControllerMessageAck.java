package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

import it.unibo.deis.lia.ramp.util.NodeStats;

import java.util.Map;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessageAck extends ControllerMessage {

    private String srcIP;

    private int routeId;

    /**
     * OS_ROUTING_ACK message: messageType, srcIP, routeID
     * OS_ROUTING_ABORT message: messageType, routeID
     * DATA_PLANE_DATA_TYPE_ACK message: messageType
     * DATA_PLANE_DATA_TYPE_ABORT message: messageType
     * DATA_PLANE_RULE_ACK message: messageType
     * DATA_PLANE_RULE_ABORT message: messageType
     */
    public ControllerMessageAck(MessageType messageType, int clientPort, Map<String, NodeStats> nodeStats, String srcIP, int routeId) {
        super(messageType, clientPort, nodeStats);

        this.srcIP = srcIP;
        this.routeId = routeId;
    }

    public ControllerMessageAck(MessageType messageType, String srcIP, int routeId) {
        super(messageType);
        this.srcIP = srcIP;
        this.routeId = routeId;
    }

    public ControllerMessageAck(MessageType messageType) {
        super(messageType);
    }

    public String getSrcIP() { return srcIP; }

    public void setSrcIP(String srcIP) { this.srcIP = srcIP; }

    public int getRouteId() { return routeId; }

    public void setRouteId(int routeId) { this.routeId = routeId; }
}
