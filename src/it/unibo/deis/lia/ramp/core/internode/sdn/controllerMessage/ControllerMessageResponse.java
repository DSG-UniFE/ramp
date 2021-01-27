package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.OsRoutingPathDescriptor;
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

    private int routeId;

    private OsRoutingPathDescriptor osRoutingPath;

    private int osROutingPathDuration;

    /**
     * PATH_RESPONSE message: messageType, newPaths
     * MULTICAST_CONTROL message: messageType, flowId, newPaths
     * OS_ROUTING_PULL_RESPONSE: messageType, routeId, osRoutingPath
     * OS_ROUTING_PUSH_RESPONSE: messageType, routeId, osRoutingPath
     * OS_ROUTING_UPDATE_PRIORITY_RESPONSE: messageType, routeId
     * TOPOLOGY_GRAPH_RESPONSE: messageType
     */
    public ControllerMessageResponse(MessageType messageType, int clientPort, Map<String, NodeStats> nodeStats, int flowId, List<PathDescriptor> newPaths) {
        super(messageType, clientPort, nodeStats);

        this.flowId = flowId;
        this.newPaths = newPaths;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPath = null;
        this.osROutingPathDuration = ControllerMessage.UNUSED_FIELD;
    }

    public ControllerMessageResponse(MessageType messageType, int flowId, List<PathDescriptor> newPaths) {
        super(messageType);

        this.flowId = flowId;
        this.newPaths = newPaths;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPath = null;
        this.osROutingPathDuration = ControllerMessage.UNUSED_FIELD;
    }

    public ControllerMessageResponse(MessageType messageType, int clientPort, int routeId, OsRoutingPathDescriptor osRoutingPath, int osROutingPathDuration) {
        super(messageType, clientPort);
        this.flowId = ControllerMessage.UNUSED_FIELD;
        this.newPaths = null;
        this.routeId = routeId;
        this.osRoutingPath = osRoutingPath;
        this.osROutingPathDuration = osROutingPathDuration;
    }

    public ControllerMessageResponse(MessageType messageType) {
        super(messageType);

        this.flowId = ControllerMessage.UNUSED_FIELD;
        this.newPaths = null;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPath = null;
        this.osROutingPathDuration = ControllerMessage.UNUSED_FIELD;
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

    public int getRouteId() {return this.routeId; }

    public void setRouteId(int routeId) { this.routeId = routeId; }

    public OsRoutingPathDescriptor getOsRoutingPath() {
        return this.osRoutingPath;
    }

    public void setOsRoutingPath(OsRoutingPathDescriptor osRoutingPath) {
        this.osRoutingPath = osRoutingPath;
    }

    public int getOsROutingPathDuration() {
        return this.osROutingPathDuration;
    }

    public void setOsROutingPathDuration(int osROutingPathDuration) {
        this.osROutingPathDuration = osROutingPathDuration;
    }
}
