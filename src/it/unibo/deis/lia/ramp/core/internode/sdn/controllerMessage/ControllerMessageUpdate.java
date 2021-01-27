package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataPlaneMessage.DataPlaneMessage;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.core.internode.sdn.routingPolicy.RoutingPolicy;
import it.unibo.deis.lia.ramp.util.NodeStats;

import java.util.List;
import java.util.Map;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessageUpdate extends ControllerMessage {
    /**
     * Data structure to hold neighbor nodes (nodeId, addresses)
     */
    private Map<Integer, List<String>> neighborNodes;

    private TrafficEngineeringPolicy trafficEngineeringPolicy;

    private RoutingPolicy routingPolicy;

    /**
     * Data structure to hold default flow paths (destNodeId, path)
     */
    private Map<Integer, PathDescriptor> newPathMappings;

    /**
     * Data structure to hold flow priorities (flowId, priority)
     */
    private Map<Integer, Integer> flowPriorities;

    private DataPlaneMessage dataPlaneMessage;

    private String dataType;

    private String dataPlaneRule;

    private String srcIp;

    private String destIp;

    private String viaIp;

    private int routeId;

    private boolean osRoutingPriority;

    /**
     * TOPOLOGY_UPDATE message: messageType, neighborNodes, nodeStats
     * TRAFFIC_ENGINEERING_POLICY_UPDATE message: messageType, trafficEngineeringPolicy
     * ROUTING_POLICY_UPDATE: messageType, routingPolicy
     * DEFAULT_FLOW_PATHS_UPDATE message: messageType, newPathMappings
     * FLOW_PRIORITIES_UPDATE message: messageType, flowPriorities
     * OS_ROUTING_ADD_ROUTE: messageType, clientPort srcIP, destIP, viaIP, routeId
     * OS_ROUTING_DELETE_ROUTE: messageType, routeId
     * OS_ROUTING_PRIORITY_UPDATE: messageType, clientPort, routeId, osRoutingPriority
     * DATA_PLANE_ADD_DATA_TYPE message: messageType, DataPlaneMessage
     * DATA_PLANE_REMOVE_DATA_TYPE message: messageType, dataType
     * DATA_PLANE_ADD_RULE_FILE message: messageType, DataPlaneMessage
     * DATA_PLANE_REMOVE_RULE_FILE message: messageType, dataPlaneRule
     * DATA_PLANE_ADD_RULE message: messageType, dataType, dataPlaneRule
     * DATA_PLANE_REMOVE_RULE message: messageType, dataType, dataPlaneRule
     */
    public ControllerMessageUpdate(MessageType messageType, int clientPort, Map<String, NodeStats> nodeStats, Map<Integer, List<String>> neighborNodes, TrafficEngineeringPolicy trafficEngineeringPolicy, RoutingPolicy routingPolicy, Map<Integer, PathDescriptor> newPathMappings, Map<Integer, Integer> flowPriorities, DataPlaneMessage dataPlaneMessage, String dataType, String dataPlaneRule) {
        super(messageType, clientPort, nodeStats);

        this.neighborNodes = neighborNodes;
        this.trafficEngineeringPolicy = trafficEngineeringPolicy;
        this.routingPolicy = routingPolicy;
        this.newPathMappings = newPathMappings;
        this.flowPriorities = flowPriorities;
        this.dataPlaneMessage = dataPlaneMessage;
        this.dataType = dataType;
        this.dataPlaneRule = dataPlaneRule;
        this.srcIp = null;
        this.destIp = null;
        this.viaIp = null;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPriority = false;
    }

    public ControllerMessageUpdate(MessageType messageType, Map<String, NodeStats> nodeStats, Map<Integer, List<String>> neighborNodes, TrafficEngineeringPolicy trafficEngineeringPolicy, RoutingPolicy routingPolicy, Map<Integer, PathDescriptor> newPathMappings, Map<Integer, Integer> flowPriorities, DataPlaneMessage dataPlaneMessage, String dataType, String dataPlaneRule) {
        super(messageType, nodeStats);

        this.neighborNodes = neighborNodes;
        this.trafficEngineeringPolicy = trafficEngineeringPolicy;
        this.routingPolicy = routingPolicy;
        this.newPathMappings = newPathMappings;
        this.flowPriorities = flowPriorities;
        this.dataPlaneMessage = dataPlaneMessage;
        this.dataType = dataType;
        this.dataPlaneRule = dataPlaneRule;
        this.srcIp = null;
        this.destIp = null;
        this.viaIp = null;
        this.routeId = ControllerMessage.UNUSED_FIELD;
        this.osRoutingPriority = false;
    }

    public ControllerMessageUpdate(MessageType messageType, int clientPort, String srcIP, String destIP, String viaIP, int routeId) {
        super(messageType, clientPort);
        this.srcIp = srcIP;
        this.destIp = destIP;
        this.viaIp = viaIP;
        this.routeId = routeId;
    }

    public ControllerMessageUpdate(MessageType messageType, int clientPort, int routeId, boolean osRoutingPriority) {
        super(messageType, clientPort);
        this.routeId = routeId;
        this.osRoutingPriority = osRoutingPriority;
    }

    public Map<Integer, List<String>> getNeighborNodes() {
        return this.neighborNodes;
    }

    public void setNeighborNodes(Map<Integer, List<String>> neighborNodes) {
        this.neighborNodes = neighborNodes;
    }

    public TrafficEngineeringPolicy getTrafficEngineeringPolicy() {
        return this.trafficEngineeringPolicy;
    }

    public void setTrafficEngineeringPolicy(TrafficEngineeringPolicy trafficEngineeringPolicy) {
        this.trafficEngineeringPolicy = trafficEngineeringPolicy;
    }

    public RoutingPolicy getRoutingPolicy() {
        return this.routingPolicy;
    }

    public void setRoutingPolicy(RoutingPolicy routingPolicy) {
        this.routingPolicy = routingPolicy;
    }

    public Map<Integer, PathDescriptor> getNewPathMappings() {

        return this.newPathMappings;
    }

    public void setNewPathMappings(Map<Integer, PathDescriptor> newPathMappings) {
        this.newPathMappings = newPathMappings;
    }

    public Map<Integer, Integer> getFlowPriorities() {
        return this.flowPriorities;
    }

    public void setFlowPriorities(Map<Integer, Integer> flowPriorities) {
        this.flowPriorities = flowPriorities;
    }

    public void setDataPlaneMessage(DataPlaneMessage dataPlaneMessage) {
        this.dataPlaneMessage = dataPlaneMessage;
    }

    public DataPlaneMessage getDataPlaneMessage() {
        return dataPlaneMessage;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataPlaneRule(String dataPlaneRule) {
        this.dataPlaneRule = dataPlaneRule;
    }

    public String getDataPlaneRule() {
        return dataPlaneRule;
    }

    public String getSrcIP() { return this.srcIp; }

    public void setSrcIP(String srcIP) { this.srcIp = srcIP; }

    public String getDestIP() { return this.destIp; }

    public void setDestIP(String destIP) { this.destIp = destIP; }

    public String getViaIP() { return this.viaIp; }

    public void setViaIP(String viaIP) { this.viaIp = viaIP; }

    public int getRouteId() {return this.routeId; }

    public void setRouteId(int routeId) { this.routeId = routeId; }

    public boolean isOsRoutingPriority() { return this.osRoutingPriority; }

    public void setOsRoutingPriority(boolean osRoutingPriority) { this.osRoutingPriority = osRoutingPriority; }
}
