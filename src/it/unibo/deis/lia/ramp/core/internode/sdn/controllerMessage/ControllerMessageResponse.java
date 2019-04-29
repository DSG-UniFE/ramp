package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.util.NodeStats;

import java.util.List;
import java.util.Map;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessageResponse extends ControllerMessage {
    private int flowId;

    private List<PathDescriptor> newPaths;

    private String srcIP;

    private String destIP;

    private String viaIP;

    private int routeId;

    /**
     * PATH_RESPONSE message: messageType, newPaths
     * MULTICAST_CONTROL message: messageType, flowId, newPaths
     * OS_ROUTING_RESPONSE: messageType, routeId
     * OS_ROUTING_ADD_ROUTE: messageType, srcIP, destIP, viaIP, routeId
     * OS_ROUTING_DELETE_ROUTE: messageType, routeId
     * TOPOLOGY_GRAPH_RESPONSE: messageType
     */
    public ControllerMessageResponse(MessageType messageType, int clientPort, Map<String, NodeStats> nodeStats, int flowId, List<PathDescriptor> newPaths, String srcIP, String destIP, String viaIP, int routeId) {
        super(messageType, clientPort, nodeStats);

        this.flowId = flowId;
        this.newPaths = newPaths;
        this.srcIP = srcIP;
        this.destIP = destIP;
        this.viaIP = viaIP;
        this.routeId = routeId;
    }

    public ControllerMessageResponse(MessageType messageType, int flowId, List<PathDescriptor> newPaths) {
        super(messageType);

        this.flowId = flowId;
        this.newPaths = newPaths;
        this.srcIP = null;
        this.destIP = null;
        this.viaIP = null;
        this.routeId = ControllerMessage.UNUSED_FIELD;
    }

    public ControllerMessageResponse(MessageType messageType, int clientPort, String srcIP, String destIP, String viaIP, int routeId) {
        super(messageType, clientPort);
        this.flowId = ControllerMessage.UNUSED_FIELD;
        this.newPaths = null;
        this.srcIP = srcIP;
        this.destIP = destIP;
        this.viaIP = viaIP;
        this.routeId = routeId;
    }

    public int getFlowId() {
        return this.flowId;
    }

    public void setFlowId(int flowId) {
        this.flowId = flowId;
    }

    public List<PathDescriptor> getNewPaths() {
        return this.newPaths;
    }

    public void setNewPaths(List<PathDescriptor> newPaths) {
        this.newPaths = newPaths;
    }

    public String getSrcIP() { return srcIP; }

    public void setSrcIP(String srcIP) { this.srcIP = srcIP; }

    public String getDestIP() { return this.destIP; }

    public void setDestIP(String destIP) { this.destIP = destIP; }

    public String getViaIP() { return this.viaIP; }

    public void setViaIP(String viaIP) { this.viaIP = viaIP; }

    public int getRouteId() {return this.routeId; }

    public void setRouteId(int routeId) { this.routeId = routeId; }


}
