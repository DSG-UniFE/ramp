package it.unibo.deis.lia.ramp.core.internode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import it.unibo.deis.lia.ramp.util.NodeStats;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class ControllerMessage implements Serializable {

	private static final long serialVersionUID = -3323203535555375193L;
	public static final int UNUSED_FIELD = -1;
	
	private MessageType messageType;
	private int clientPort;
	private int[] destNodeIds;
	private int[] destPorts;
	private ApplicationRequirements applicationRequirements;
	private TopologyGraphSelector.PathSelectionMetric pathSelectionMetric;
	private int flowId;
	private List<PathDescriptor> newPaths;
	// Data structure to hold neighbor nodes (nodeId, addresses)
	private Map<Integer, List<String>> neighborNodes;
	// Data structure to hold node stats (address, nodeStats)
	private Map<String, NodeStats> nodeStats;
	private FlowPolicy flowPolicy;
	// Data structure to hold default flow paths (destNodeId, path)
	private Map<Integer, PathDescriptor> newPathMappings;
	// Data structure to hold flow priorities (flowId, priority)
	private Map<Integer, Integer> flowPriorities;
	
	// JOIN_SERVICE message: messageType, clientPort
	// LEAVE_SERVICE message: messageType
	// PATH_REQUEST message: messageType, destNodeIds, applicationRequirements, pathSelectionMetric, flowId, clientPort
	// PATH_RESPONSE message: messageType, newPaths
	// TOPOLOGY_UPDATE message: messageType, neighborNodes, nodeStats
	// PRIORITY_VALUE_REQUEST message: messageType, clientPort, applicationRequirements, flowId
	// MULTICAST_REQUEST message: messageType, clientPort, destNodeIds, applicationRequirements, pathSelectionMetric, flowId
	// MULTICAST_CONTROL message: messageType, flowId, newPaths
	// FLOW_POLICY_UPDATE message: messageType, flowPolicy
	// DEFAULT_FLOW_PATHS_UPDATE message: messageType, newPathMappings
	// FLOW_PRIORITIES_UPDATE message: messageType, flowPriorities
	protected ControllerMessage(MessageType messageType, int clientPort, int[] destNodeIds, int[] destPorts, ApplicationRequirements applicationRequirements, TopologyGraphSelector.PathSelectionMetric pathSelectionMetric, int flowId, List<PathDescriptor> newPaths,
			Map<Integer, List<String>> neighborNodes, Map<String, NodeStats> nodeStats, FlowPolicy flowPolicy, Map<Integer, PathDescriptor> newPathMappings, Map<Integer, Integer> flowPriorities) {
		this.messageType = messageType;
		this.clientPort = clientPort;
		this.destNodeIds = destNodeIds;
		this.destPorts = destPorts;
		this.applicationRequirements = applicationRequirements;
		this.pathSelectionMetric = pathSelectionMetric;
		this.flowId = flowId;
		this.newPaths = newPaths;
		this.neighborNodes = neighborNodes;
		this.nodeStats = nodeStats;
		this.flowPolicy = flowPolicy;
		this.newPathMappings = newPathMappings;
		this.flowPriorities = flowPriorities;
	}
	
	protected ControllerMessage(MessageType messageType) {
		this(messageType, UNUSED_FIELD, new int[0], new int[0], null, null, UNUSED_FIELD, null, null, null, null, null, null);
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public void setMessageType(MessageType type) {
		this.messageType = type;
	}

	public int getClientPort() {
		return clientPort;
	}

	public void setClientPort(int clientPort) {
		this.clientPort = clientPort;
	}

	public int[] getDestNodeIds() {
		return destNodeIds;
	}

	public void setDestNodeIds(int[] destNodeIds) {
		this.destNodeIds = destNodeIds;
	}

	public int[] getDestPorts() {
		return destPorts;
	}

	public void setDestPorts(int[] destPorts) {
		this.destPorts = destPorts;
	}

	public ApplicationRequirements getApplicationRequirements() {
		return applicationRequirements;
	}

	public void setApplicationRequirements(ApplicationRequirements applicationRequirements) {
		this.applicationRequirements = applicationRequirements;
	}

	public TopologyGraphSelector.PathSelectionMetric getPathSelectionMetric() {
		return pathSelectionMetric;
	}

	public void setPathSelectionMetric(TopologyGraphSelector.PathSelectionMetric pathSelectionMetric) {
		this.pathSelectionMetric = pathSelectionMetric;
	}

	public int getFlowId() {
		return flowId;
	}

	public void setFlowId(int flowId) {
		this.flowId = flowId;
	}

	public List<PathDescriptor> getNewPaths() {
		return newPaths;
	}

	public void setNewPaths(List<PathDescriptor> newPaths) {
		this.newPaths = newPaths;
	}

	public Map<Integer, List<String>> getNeighborNodes() {
		return neighborNodes;
	}

	public void setNeighborNodes(Map<Integer, List<String>> neighborNodes) {
		this.neighborNodes = neighborNodes;
	}

	public Map<String, NodeStats> getNodeStats() {
		return nodeStats;
	}

	public void setNodeStats(Map<String, NodeStats> nodeStats) {
		this.nodeStats = nodeStats;
	}

	public FlowPolicy getFlowPolicy() {
		return flowPolicy;
	}

	public void setFlowPolicy(FlowPolicy flowPolicy) {
		this.flowPolicy = flowPolicy;
	}

	public Map<Integer, PathDescriptor> getNewPathMappings() {
		return newPathMappings;
	}

	public void setNewPathMappings(Map<Integer, PathDescriptor> newPathMappings) {
		this.newPathMappings = newPathMappings;
	}

	public Map<Integer, Integer> getFlowPriorities() {
		return flowPriorities;
	}

	public void setFlowPriorities(Map<Integer, Integer> flowPriorities) {
		this.flowPriorities = flowPriorities;
	}

	public enum MessageType {
		JOIN_SERVICE,
		LEAVE_SERVICE,
		PATH_REQUEST,
		PATH_RESPONSE,
		TOPOLOGY_UPDATE,
		PRIORITY_VALUE_REQUEST,
		MULTICAST_REQUEST,
		MULTICAST_CONTROL,
		FLOW_POLICY_UPDATE,
		DEFAULT_FLOW_PATHS_UPDATE,
		FLOW_PRIORITIES_UPDATE
	}

}
