package it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage;

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

    /**
     * TOPOLOGY_UPDATE message: messageType, neighborNodes, nodeStats
     * TRAFFIC_ENGINEERING_POLICY_UPDATE message: messageType, trafficEngineeringPolicy
     * ROUTING_POLICY_UPDATE: messageType, routingPolicy
     * DEFAULT_FLOW_PATHS_UPDATE message: messageType, newPathMappings
     * FLOW_PRIORITIES_UPDATE message: messageType, flowPriorities
     */
    public ControllerMessageUpdate(MessageType messageType, int clientPort, Map<String, NodeStats> nodeStats, Map<Integer, List<String>> neighborNodes, TrafficEngineeringPolicy trafficEngineeringPolicy, RoutingPolicy routingPolicy, Map<Integer, PathDescriptor> newPathMappings, Map<Integer, Integer> flowPriorities) {
        super(messageType, clientPort, nodeStats);

        this.neighborNodes = neighborNodes;
        this.trafficEngineeringPolicy = trafficEngineeringPolicy;
        this.routingPolicy = routingPolicy;
        this.newPathMappings = newPathMappings;
        this.flowPriorities = flowPriorities;
    }

    public ControllerMessageUpdate(MessageType messageType, Map<String, NodeStats> nodeStats, Map<Integer, List<String>> neighborNodes, TrafficEngineeringPolicy trafficEngineeringPolicy, RoutingPolicy routingPolicy, Map<Integer, PathDescriptor> newPathMappings, Map<Integer, Integer> flowPriorities) {
        super(messageType, nodeStats);

        this.neighborNodes = neighborNodes;
        this.trafficEngineeringPolicy = trafficEngineeringPolicy;
        this.routingPolicy = routingPolicy;
        this.newPathMappings = newPathMappings;
        this.flowPriorities = flowPriorities;
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

    public RoutingPolicy getRoutingPolicy() { return this.routingPolicy; }

    public void setRoutingPolicy(RoutingPolicy routingPolicy) { this.routingPolicy = routingPolicy; }

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
}