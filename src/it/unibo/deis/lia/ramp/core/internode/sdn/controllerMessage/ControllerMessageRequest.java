package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.util.NodeStats;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage.trafficCongestionLevel;

import java.util.Map;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessageRequest extends ControllerMessage {
    private int[] destNodeIds;

    private int[] destPorts;

    private ApplicationRequirements applicationRequirements;

    private PathSelectionMetric pathSelectionMetric;

    private int flowId;

    private int routeId;

    private boolean osRoutingPriority;

    private int sourceNodeId;

    private trafficCongestionLevel trafficCongestionLevel;

    /**
     * PATH_REQUEST message: messageType, clientPort, destNodeIds, applicationRequirements, pathSelectionMetric, flowId,
     * MULTICAST_REQUEST message: messageType, clientPort, destNodeIds, destPorts, applicationRequirements, pathSelectionMetric, flowId
     * PRIORITY_VALUE_REQUEST message: messageType, clientPort, applicationRequirements, flowId
     * FIX_PATH_REQUEST message: messageType, clientPort, destNodeIds, flowId
     * OS_ROUTING_REQUEST message: messageType, clientPort, destNodeIds, destPorts, applicationRequirements, pathSelectionMetric
     * TOPOLOGY_GRAPH_REQUEST message: messageType, clientPort
     */
    public ControllerMessageRequest(MessageType messageType, int clientPort, Map<String, NodeStats> nodeStats, int[] destNodeIds, int[] destPorts, ApplicationRequirements applicationRequirements, PathSelectionMetric pathSelectionMetric, int flowId) {
        super(messageType, clientPort, nodeStats);

        this.destNodeIds = destNodeIds;
        this.destPorts = destPorts;
        this.applicationRequirements = applicationRequirements;
        this.pathSelectionMetric = pathSelectionMetric;
        this.flowId = flowId;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPriority = false;
        this.sourceNodeId = ControllerMessage.UNUSED_FIELD;
        this.trafficCongestionLevel = null;
    }

    public ControllerMessageRequest(MessageType messageType, int clientPort, int[] destNodeIds, int[] destPorts, ApplicationRequirements applicationRequirements, PathSelectionMetric pathSelectionMetric, int flowId) {
        super(messageType, clientPort);
        this.destNodeIds = destNodeIds;
        this.destPorts = destPorts;
        this.applicationRequirements = applicationRequirements;
        this.pathSelectionMetric = pathSelectionMetric;
        this.flowId = flowId;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPriority = false;
        this.sourceNodeId = ControllerMessage.UNUSED_FIELD;
        this.trafficCongestionLevel = null;
    }

    public ControllerMessageRequest(MessageType messageType, int clientPort, int routeId, boolean osRoutingPriority) {
        super(messageType, clientPort);
        this.destNodeIds = null;
        this.destPorts = null;
        this.applicationRequirements = null;
        this.pathSelectionMetric = null;
        this.flowId = ControllerMessage.UNUSED_FIELD;
        this.routeId = routeId;
        this.osRoutingPriority = osRoutingPriority;
        this.sourceNodeId = ControllerMessage.UNUSED_FIELD;
        this.trafficCongestionLevel = null;
    }

    public ControllerMessageRequest(MessageType messageType, int clientPort) {
        super(messageType, clientPort);
        this.destNodeIds = null;
        this.destPorts = null;
        this.applicationRequirements = null;
        this.pathSelectionMetric = null;
        this.flowId = ControllerMessage.UNUSED_FIELD;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPriority = false;
        this.sourceNodeId = ControllerMessage.UNUSED_FIELD;
        this.trafficCongestionLevel = null;
    }

    public ControllerMessageRequest(MessageType messageType, int clientPort, trafficCongestionLevel traffic_congestion) {
        super(messageType, clientPort);
        this.destNodeIds = null;
        this.destPorts = null;
        this.applicationRequirements = null;
        this.pathSelectionMetric = null;
        this.flowId = ControllerMessage.UNUSED_FIELD;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPriority = false;
        this.sourceNodeId = ControllerMessage.UNUSED_FIELD;
        this.trafficCongestionLevel = traffic_congestion;
    }

    public int[] getDestNodeIds() {
        return this.destNodeIds;
    }

    public void setDestNodeIds(int [] destNodeIds) {
        this.destNodeIds = destNodeIds;
    }

    public int[] getDestPorts() {
        return this.destPorts;
    }

    public void setDestPorts(int [] destPorts) {
        this.destPorts = destPorts;
    }

    public ApplicationRequirements getApplicationRequirements() {
        return this.applicationRequirements;
    }

    public void setApplicationRequirements(ApplicationRequirements applicationRequirements) {
        this.applicationRequirements = applicationRequirements;
    }

    public PathSelectionMetric getPathSelectionMetric() {
        return this.pathSelectionMetric;
    }

    public void setPathSelectionMetric(PathSelectionMetric pathSelectionMetric) {
        this.pathSelectionMetric = pathSelectionMetric;
    }

    public int getFlowId() {
        return this.flowId;
    }

    public void setFlowId(int flowId) {
        this.flowId = flowId;
    }

    public int getRouteId() { return this.routeId; }

    public void setRouteId(int routeId) { this.routeId = routeId; }

    public boolean isOsRoutingPriority() { return this.osRoutingPriority; }

    public void setOsRoutingPriority(boolean osRoutingPriority) { this.osRoutingPriority = osRoutingPriority; }

    public int getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(int sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public trafficCongestionLevel getTrafficCongestionType() {
        return this.trafficCongestionLevel;
    }

    public void setTrafficCongestionType(trafficCongestionLevel trafficCongestionLevel) {
        this.trafficCongestionLevel = trafficCongestionLevel;
    }
}
