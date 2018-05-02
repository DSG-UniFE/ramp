package it.unibo.deis.lia.ramp.core.internode;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.graph.implementations.MultiNode;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.util.NetworkInterfaceStats;
import it.unibo.deis.lia.ramp.util.NodeStats;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class ControllerService extends Thread {
	
	private static final int PROTOCOL = E2EComm.TCP;
	private static final int GRAPH_NODES_TTL = 60*1000;
	
	private static ControllerService controllerService = null;
	private BoundReceiveSocket serviceSocket;
	private boolean active;
	private UpdateManager updateManager;
	private FlowPolicy flowPolicy;
	
	// Data structure to hold descriptors for the currently active clients (clientNodeId, descriptor)
	// private Map<Integer, ClientDescriptor> activeClients;
	// Data structure to hold the currently active clients (clientNodeId)
	private Set<Integer> activeClients;
	// Data structure to hold a representation of the current network topology (every node is identified by its nodeId, other informations are carried as an attribute)
	private Graph topologyGraph;
	private TopologyGraphSelector flowPathSelector;
	private TopologyGraphSelector defaultPathSelector;
	// Data structure to hold current paths for the existing flows (flowId, path)
	private Map<Integer, PathDescriptor> flowPaths;
	// Data structure to hold start times of the existing flows (flowId, startTime)
	private Map<Integer, Long> flowStartTimes;
	// Data structure to hold application requirements for the existing flows (flowId, applicationRequirements)
	private Map<Integer, ApplicationRequirements> flowApplicationRequirements;
	
	// Data structure to hold priorities for the existing flows (flowId, priority)
	private Map<Integer, Integer> flowPriorities;
	private PrioritySelector flowPrioritySelector;
	
	private ControllerService() throws Exception {
		this.serviceSocket = E2EComm.bindPreReceive(PROTOCOL);
		ServiceManager.getInstance(false).registerService("SDNController", this.serviceSocket.getLocalPort(), PROTOCOL);
		this.active = true;
		this.updateManager = new UpdateManager();
		this.flowPolicy = FlowPolicy.SINGLE_FLOW;
		
		this.activeClients = new HashSet<Integer>();
		this.activeClients.add(Dispatcher.getLocalRampId());
		this.topologyGraph = new MultiGraph("TopologyGraph");
		this.flowPathSelector = new MinimumLoadFlowPathSelector(this.topologyGraph);
		this.defaultPathSelector = new BreadthFirstFlowPathSelector(this.topologyGraph);
		this.flowPaths = new ConcurrentHashMap<Integer, PathDescriptor>();
		this.flowStartTimes = new ConcurrentHashMap<Integer, Long>();
		this.flowApplicationRequirements = new ConcurrentHashMap<Integer, ApplicationRequirements>();
		
		this.flowPriorities = new ConcurrentHashMap<Integer, Integer>();
		this.flowPrioritySelector = new ApplicationTypeFlowPrioritySelector();
	}
	
	public synchronized static ControllerService getInstance() {
		if (controllerService == null) {
			try {
				controllerService = new ControllerService();
			} catch (Exception e) {
				e.printStackTrace();
			}
			controllerService.start();
		}
		return controllerService;
	}
	
	public void stopService() {
		System.out.println("ControllerService STOP");
		ServiceManager.getInstance(false).removeService("SDNController");
		this.active = false;
		try {
			this.serviceSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.updateManager.stopUpdateManager();
	}
	
	public void displayGraph() {
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		this.topologyGraph.addAttribute("ui.quality");
		this.topologyGraph.addAttribute("ui.antialias");
		this.topologyGraph.addAttribute("ui.stylesheet", "node {fill-color: blue; size: 40px; }");
		this.topologyGraph.display();
	}
	
	public void takeGraphScreenshot(String screenshotFilePath) {
//		this.topologyGraph.addNode("5").addAttribute("ui.label", "5");
//		this.topologyGraph.addNode("6").addAttribute("ui.label", "6");
//		this.topologyGraph.addNode("7").addAttribute("ui.label", "7");
//		this.topologyGraph.addNode("8").addAttribute("ui.label", "8");
//		this.topologyGraph.addEdge("15", "1", "5").addAttribute("ui.label", "10.2.2.1 - 10.2.2.2");
//		this.topologyGraph.addEdge("25", "2", "5").addAttribute("ui.label", "169.254.1.1 - 169.254.1.2");
//		this.topologyGraph.addEdge("16", "1", "6").addAttribute("ui.label", "192.168.1.1 - 192.168.1.2");
//		this.topologyGraph.addEdge("47", "4", "7").addAttribute("ui.label", "192.168.1.1 - 192.168.1.2");
//		this.topologyGraph.addEdge("58", "5", "8").addAttribute("ui.label", "192.168.2.1 - 192.168.2.2");
//		this.topologyGraph.addEdge("68", "6", "8").addAttribute("ui.label", "172.16.0.1 - 172.16.0.2");
		this.topologyGraph.addAttribute("ui.screenshot", screenshotFilePath);
	}
	
	public void updateFlowPolicy(FlowPolicy flowPolicy) {
		this.flowPolicy = flowPolicy;
		for (Node clientNode : this.topologyGraph.getNodeSet()) {
			int clientNodeId = Integer.parseInt(clientNode.getId());
			ControllerMessage updateMessage = new ControllerMessage(ControllerMessage.MessageType.FLOW_POLICY_UPDATE, ControllerMessage.UNUSED_FIELD, new int[0], new int[0], null, null, ControllerMessage.UNUSED_FIELD, null, null, null, flowPolicy, null, null);
			String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5*1000).get(0).getPath();
			int clientPort = clientNode.getAttribute("port");
			try {
				E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(updateMessage));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void run() {
		System.out.println("ControllerService START");
		this.updateManager.start();
		while (active == true) {
			try {
				// Receive packets from the clients and pass them to newly created handlers
				GenericPacket gp = E2EComm.receive(this.serviceSocket, 5*1000);
				new PacketHandler(gp).start();
			} catch (Exception e) {
				// e.printStackTrace();
			}
		}
		try {
			this.serviceSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		controllerService = null;
		System.out.println("ControllerService FINISHED");
	}
	
	private class PacketHandler extends Thread {
		
		private GenericPacket gp;
		
		PacketHandler(GenericPacket gp) {
			this.gp = gp;
		}
		
		@Override
		public void run() {
			// int packetSize = E2EComm.objectSizePacket(gp);
			// LocalDateTime localDateTime = LocalDateTime.now();
			// String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
			
			if (this.gp instanceof UnicastPacket) {
				UnicastPacket up = (UnicastPacket) gp;
				Object payload = null;
				
				try {
					payload = E2EComm.deserialize(up.getBytePayload());
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (payload instanceof ControllerMessage) {
					ControllerMessage controllerMessage = (ControllerMessage) payload;
					int clientNodeId = up.getSourceNodeId();
					String[] clientDest = E2EComm.ipReverse(up.getSource());
					
					switch (controllerMessage.getMessageType()) {
					case JOIN_SERVICE:
						// System.out.println("ControllerService: join request of size " +  packetSize + " received at " + timestamp);
						
						// Add the source node to the active clients and to the topology
						activeClients.add(clientNodeId);
						MultiNode clientNode = topologyGraph.addNode(Integer.toString(clientNodeId));
						clientNode.addAttribute("port", controllerMessage.getClientPort());
						for (String address : controllerMessage.getNodeStats().keySet()) {
							NodeStats nodeStats = controllerMessage.getNodeStats().get(address);
							if (nodeStats instanceof NetworkInterfaceStats) {
								NetworkInterfaceStats networkInterfaceStats = (NetworkInterfaceStats) nodeStats;
								clientNode.addAttribute("network_stats_" + address, networkInterfaceStats);
							}
						}
						clientNode.addAttribute("last_update", System.currentTimeMillis());
						clientNode.addAttribute("ui.label", clientNode.getId());
						System.out.println("ControllerService: join request received from client " + clientNodeId + ", successfully added to the topology");
						break;
					case LEAVE_SERVICE:
						// Remove the source node from the active clients and from the topology
						activeClients.remove(clientNodeId);
						topologyGraph.removeNode(Integer.toString(clientNodeId));
						System.out.println("ControllerService: leave request received from client " + clientNodeId + ", successfully removed from the topology");
						break;
					case PATH_REQUEST:
						// Choose the best path for the specified flow, add it to the active flow paths and send it to the source node
						int destNodeId = controllerMessage.getDestNodeIds()[0];
						ApplicationRequirements applicationRequirements = controllerMessage.getApplicationRequirements();
						int flowId = controllerMessage.getFlowId();
						System.out.println("ControllerService: path request from client " + clientNodeId + " to node " + destNodeId + " for flow " + flowId);
						
						PathDescriptor newPath = null;
						TopologyGraphSelector pathSelector = flowPathSelector;
						TopologyGraphSelector.PathSelectionMetric pathSelectionMetric = controllerMessage.getPathSelectionMetric();
						if (pathSelectionMetric != null) {
							if (pathSelectionMetric == TopologyGraphSelector.PathSelectionMetric.BREADTH_FIRST)
								pathSelector = new BreadthFirstFlowPathSelector(topologyGraph);
							else if (pathSelectionMetric == TopologyGraphSelector.PathSelectionMetric.FEWEST_INTERSECTIONS)
								pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);
							else if (pathSelectionMetric == TopologyGraphSelector.PathSelectionMetric.MINIMUM_LOAD)
								pathSelector = new MinimumLoadFlowPathSelector(topologyGraph);
						}
						// If applicationRequirements is not null, this is the first path request for the flow, a path for the flow is selected
						if (applicationRequirements != null) {
							System.out.println("ControllerService: first path request for flow " + flowId + ", selecting a path");
							newPath = pathSelector.selectPath(clientNodeId, destNodeId, applicationRequirements, flowPaths);
							flowStartTimes.put(flowId, System.currentTimeMillis());
							flowApplicationRequirements.put(flowId, applicationRequirements);
						}
						// If applicationRequirements is null, the time-to-live of the previous path for the flow has expired, if the duration hasn't expired too a new path is selected
						else {
							if (flowPaths.containsKey(flowId)) {
								System.out.println("ControllerService: new path request for flow " + flowId + ", the flow is still valid, selecting a new path");
								newPath = pathSelector.selectPath(clientNodeId, destNodeId, applicationRequirements, flowPaths);
							}
							else
								System.out.println("ControllerService: new path request for flow " + flowId + ", but the flow isn't valid anymore, sending null path");
						}
						
						if (newPath != null) {
							for (int i = 0; i < newPath.getPath().length; i++)
								System.out.println("ControllerService: new flow path address " + i + ", " + newPath.getPath()[i]);
							newPath.setCreationTime(System.currentTimeMillis());
							flowPaths.put(flowId, newPath);
						}
						
						List<PathDescriptor> newPaths = new ArrayList<PathDescriptor>();
						newPaths.add(newPath);
						ControllerMessage responseMessage = new ControllerMessage(ControllerMessage.MessageType.PATH_RESPONSE, ControllerMessage.UNUSED_FIELD, new int[0], new int[0], null, null, ControllerMessage.UNUSED_FIELD, newPaths, null, null, null, null, null);
						try {
							// Send the response message using the inverted source path
							E2EComm.sendUnicast(clientDest, controllerMessage.getClientPort(), PROTOCOL, E2EComm.serialize(responseMessage));
						} catch (Exception e) {
							e.printStackTrace();
						}
						System.out.println("ControllerService: path request for flow " + flowId + " from client " + clientNodeId + ", response message successfully sent");
						break;
					case TOPOLOGY_UPDATE:
						// System.out.println("ControllerService: topology update size: " + packetSize);
						
						// Add the received nodes to the reachable neighbors for the source node
						Map<Integer, List<String>> neighborNodes = controllerMessage.getNeighborNodes();
						// Data structure to hold IDs of nodes removed during the initial refresh and the respective node objects (nodeId, node)
						HashMap<String, MultiNode> removedNodes = new HashMap<String, MultiNode>();
						// Data structure to hold mappings between IDs of nodes removed during the initial refresh and the source node addresses added on the graph by them (targetNodeId, sourceNodeAddress)
						HashMap<String, List<String>> removedNodesAddresses = new HashMap<String, List<String>>();
						// If the source node doesn't exist in the topology graph, create it
						MultiNode sourceGraphNode = topologyGraph.getNode(Integer.toString(clientNodeId));
						if (sourceGraphNode == null) {
							sourceGraphNode = topologyGraph.addNode(Integer.toString(clientNodeId));
							sourceGraphNode.addAttribute("port", controllerMessage.getClientPort());
							for (String address : controllerMessage.getNodeStats().keySet()) {
								NodeStats nodeStats = controllerMessage.getNodeStats().get(address);
								if (nodeStats instanceof NetworkInterfaceStats) {
									NetworkInterfaceStats networkInterfaceStats = (NetworkInterfaceStats) nodeStats;
									sourceGraphNode.addAttribute("network_stats_" + address, networkInterfaceStats);
									System.out.println("network stats: address " + address + " bytes " + networkInterfaceStats.getReceivedBytes() + " packets " + networkInterfaceStats.getReceivedPackets());
								}
							}
							sourceGraphNode.addAttribute("last_update", System.currentTimeMillis());
							sourceGraphNode.addAttribute("ui.label", sourceGraphNode.getId());
						}
						// If the source node already exists in the topology graph, clear all the informations previously added by it
						else {
							for (Edge edge : sourceGraphNode.getEachEdge()) {
								MultiNode targetNode = edge.getOpposite(sourceGraphNode);
								edge.removeAttribute("address_" + targetNode.getId());
								// Check if this neighbor is still reachable by some other node, if not, add it to the nodes to be removed
								boolean found = false;
								for (Edge targetNodeEdge : targetNode.getEachEdge()) {
									if (targetNodeEdge.getAttribute("address_" + targetNode.getId()) != null) {
										found = true;
										break;
									}
								}
								if (found == false) {
									removedNodes.put(targetNode.getId(), targetNode);
									Collection<Edge> edgesToSourceNode = targetNode.getEdgeSetBetween(sourceGraphNode);
									List<String> sourceNodeAddresses = new ArrayList<String>();
									for (Edge edgeToSourceNode : edgesToSourceNode)
										sourceNodeAddresses.add(edgeToSourceNode.getAttribute("address_" + sourceGraphNode.getId()));
									removedNodesAddresses.put(targetNode.getId(), sourceNodeAddresses);
								}
							}
							// Remove every no more reachable node
							for (String nodeId : removedNodes.keySet())
								topologyGraph.removeNode(nodeId);
						}
						
						// Iterate over the new neighbors
						for (Integer neighborNodeId : neighborNodes.keySet()) {
							// Consider only the nodes which have joined the service and haven't left yet
							if (activeClients.contains(neighborNodeId)) {
								// If the neighbor node doesn't exist in the topology graph, create it
								MultiNode neighborGraphNode = topologyGraph.getNode(neighborNodeId.toString());
								if (neighborGraphNode == null) {
									neighborGraphNode = topologyGraph.addNode(neighborNodeId.toString());
									// If this node was removed during the initial refresh, restore every attribute it previously had
									if (removedNodes.containsKey(neighborGraphNode.getId()))
										for (String key : removedNodes.get(neighborGraphNode.getId()).getAttributeKeySet())
											neighborGraphNode.addAttribute(key, (Object) removedNodes.get(neighborGraphNode.getId()).getAttribute(key));
									else
										neighborGraphNode.addAttribute("ui.label", neighborGraphNode.getId());
								}
								for (String neighborCompleteAddress : neighborNodes.get(neighborNodeId)) {
									String neighborAddress = neighborCompleteAddress.substring(0, neighborCompleteAddress.indexOf("/"));
									SubnetUtils neighborAddressSubnetUtils = new SubnetUtils(neighborCompleteAddress);
									SubnetInfo neighborAddressSubnetInfo = neighborAddressSubnetUtils.getInfo();
									// Consider every existing edge between this neighbor and the source node
									Collection<Edge> neighborEdges = sourceGraphNode.getEdgeSetBetween(neighborGraphNode);
									boolean found = false;
									for (Edge neighborEdge : neighborEdges) {
										SubnetInfo subnetInfo = neighborEdge.getAttribute("subnet_info");
										// If the address belongs to the subnet of the edge, add it to the edge
										if (subnetInfo.isInRange(neighborAddress)) {
											neighborEdge.addAttribute("address_" + neighborGraphNode.getId(), neighborAddress);
											if (neighborEdge.getAttribute("ui.label") != null)
												neighborEdge.addAttribute("ui.label", neighborEdge.getAttribute("address_" + sourceGraphNode.getId()) + " - " + neighborAddress);
											else
												neighborEdge.addAttribute("ui.label", neighborAddress);
											found = true;
										}
									}
									// If an edge between this neighbor and the source node doesn't exist in the topology graph for the address subnet, create it
									if (found == false) {
										Edge neighborEdge = topologyGraph.addEdge(sourceGraphNode.getId()+neighborGraphNode.getId()+"_"+neighborEdges.size(), sourceGraphNode, neighborGraphNode);
										neighborEdge.addAttribute("address_" + neighborGraphNode.getId(), neighborAddress);
										neighborEdge.addAttribute("subnet_info", neighborAddressSubnetInfo);
										neighborEdge.addAttribute("ui.label", neighborAddress);
										System.out.println("Added edge between " + neighborEdge.getNode0().getId() + " and " + neighborEdge.getNode1().getId());
									}
								}
								// If this neighbor was removed during the initial refresh, restore every source node address the node had previously added
								if (removedNodesAddresses.containsKey(neighborGraphNode.getId())) {
									List<String> sourceNodeAddresses = removedNodesAddresses.get(neighborGraphNode.getId());
									for (String sourceNodeAddress : sourceNodeAddresses) {
										if (sourceNodeAddress != null) {
											Collection<Edge> neighborEdges = sourceGraphNode.getEdgeSetBetween(neighborGraphNode);
											for (Edge neighborEdge : neighborEdges) {
												SubnetInfo subnetInfo = neighborEdge.getAttribute("subnet_info");
												if (subnetInfo.isInRange(sourceNodeAddress)) {
													neighborEdge.addAttribute("address_" + sourceGraphNode.getId(), sourceNodeAddress);
													neighborEdge.addAttribute("ui.label", sourceNodeAddress + " - " + neighborEdge.getAttribute("ui.label"));
												}
											}
										}
									}
								}
							}
						}
						// Update the network stats and last update attributes of the client
						for (String address : controllerMessage.getNodeStats().keySet()) {
							NodeStats nodeStats = controllerMessage.getNodeStats().get(address);
							if (nodeStats instanceof NetworkInterfaceStats) {
								NetworkInterfaceStats networkInterfaceStats = (NetworkInterfaceStats) nodeStats;
								sourceGraphNode.addAttribute("network_stats_" + address, networkInterfaceStats);
								System.out.println("network stats: address " + address + " bytes " + networkInterfaceStats.getReceivedBytes() + " packets " + networkInterfaceStats.getReceivedPackets());
							}
						}
						sourceGraphNode.addAttribute("last_update", System.currentTimeMillis());
						System.out.println("ControllerService: topology update received from client " + clientNodeId + ", reachable neighbor nodes successfully updated for the client");
						for (Node node : topologyGraph.getNodeSet()) {
							System.out.println("Topology node, id: " + node.getId());
							for (Edge edge : node.getEachEdge()) {
								System.out.println("Node edge between " + edge.getNode0().getId() + " and " + edge.getNode1().getId());
								for (String key : edge.getAttributeKeySet())
									System.out.println("Node edge address " + key + ": " + edge.getAttribute(key));
							}
						}
						
//						// Add the received nodes to the reachable neighbors for the source node
//						Map<Integer, String> neighborNodes = controllerMessage.getNeighborNodes();
//						// Data structure to hold IDs of nodes removed during the initial refresh and the respective node objects (nodeId, node)
//						HashMap<String, Node> removedNodes = new HashMap<String, Node>();
//						// Data structure to hold mappings between IDs of nodes removed during the initial refresh and the source node addresses added on the graph by them (targetNodeId, sourceNodeAddress)
//						HashMap<String, String> removedNodesAddresses = new HashMap<String, String>();
//						// If the source node doesn't exist in the topology graph, create it
//						Node sourceGraphNode = topologyGraph.getNode(Integer.toString(clientNodeId));
//						if (sourceGraphNode == null) {
//							sourceGraphNode = topologyGraph.addNode(Integer.toString(clientNodeId));
//							sourceGraphNode.addAttribute("ui.label", sourceGraphNode.getId());
//						}
//						// If the source node already exists in the topology graph, clear all the informations previously added by it
//						else {
//							for (Edge edge : sourceGraphNode.getEachEdge()) {
//								Node targetNode = edge.getOpposite(sourceGraphNode);
//								edge.removeAttribute("address_" + targetNode.getId());
//								// Check if this neighbor is still reachable by some other node, if not, add it to the nodes to be removed
//								boolean found = false;
//								for (Edge targetNodeEdge : targetNode.getEachEdge()) {
//									if (targetNodeEdge.getAttribute("address_" + targetNode.getId()) != null) {
//										found = true;
//										break;
//									}
//								}
//								if (found == false) {
//									removedNodes.put(targetNode.getId(), targetNode);
//									removedNodesAddresses.put(targetNode.getId(), edge.getAttribute("address_" + sourceGraphNode.getId()));
//								}
//							}
//							// Remove every no more reachable node
//							for (String nodeId : removedNodes.keySet())
//								topologyGraph.removeNode(nodeId);
//						}
//						
//						// Iterate over the new neighbors
//						for (Integer neighborNodeId : neighborNodes.keySet()) {
//							// Consider only the nodes which have joined the service and haven't left yet
//							if (activeClients.contains(neighborNodeId)) {
//								// If the neighbor node doesn't exist in the topology graph, create it
//								Node neighborGraphNode = topologyGraph.getNode(neighborNodeId.toString());
//								if (neighborGraphNode == null) {
//									neighborGraphNode = topologyGraph.addNode(neighborNodeId.toString());
//									if (removedNodes.containsKey(neighborGraphNode.getId()))
//										for (String key : removedNodes.get(neighborGraphNode.getId()).getAttributeKeySet())
//											neighborGraphNode.addAttribute(key, (Object) removedNodes.get(neighborGraphNode.getId()).getAttribute(key));
//									else
//										neighborGraphNode.addAttribute("ui.label", neighborGraphNode.getId());
//								}
//								// If an edge between this neighbor and the source node doesn't exist in the topology graph, create it
//								Edge neighborEdge = sourceGraphNode.getEdgeBetween(neighborGraphNode);
//								if (neighborEdge == null) {
//									neighborEdge = topologyGraph.addEdge(sourceGraphNode.getId()+neighborGraphNode.getId(), sourceGraphNode, neighborGraphNode);
//									System.out.println("Added edge between " + neighborEdge.getNode0().getId() + " and " + neighborEdge.getNode1().getId());
//								}
//								// Add the address of the neighbor on the edge
//								String neighborAddress = neighborNodes.get(neighborNodeId);
//								neighborEdge.addAttribute("address_" + neighborGraphNode.getId(), neighborAddress);
//								if (neighborEdge.getAttribute("ui.label") != null)
//									neighborEdge.addAttribute("ui.label", neighborEdge.getAttribute("address_" + sourceGraphNode.getId()) + " - " + neighborAddress);
//								else
//									neighborEdge.addAttribute("ui.label", neighborAddress);
//								// If this neighbor was removed during the initial refresh, restore the source node address
//								if (removedNodesAddresses.containsKey(neighborGraphNode.getId())) {
//									String sourceNodeAddress = removedNodesAddresses.get(neighborGraphNode.getId());
//									if (sourceNodeAddress != null) {
//										neighborEdge.addAttribute("address_" + sourceGraphNode.getId(), sourceNodeAddress);
//										neighborEdge.addAttribute("ui.label", sourceNodeAddress + " - " + neighborEdge.getAttribute("ui.label"));
//									}
//								}
//							}
//						}
//						// Update the last update attribute of the client
//						sourceGraphNode.addAttribute("last_update", System.currentTimeMillis());
//						System.out.println("ControllerService: topology update received from client " + clientNodeId + ", reachable neighbor nodes successfully updated for the client");
//						for (Node node : topologyGraph.getNodeSet()) {
//							System.out.println("Topology node, id: " + node.getId());
//							for (Edge edge : node.getEachEdge()) {
//								System.out.println("Node edge between " + edge.getNode0().getId() + " and " + edge.getNode1().getId());
//								for (String key : edge.getAttributeKeySet())
//									System.out.println("Node edge address " + key + ": " + edge.getAttribute(key));
//							}
//						}
						break;
					case PRIORITY_VALUE_REQUEST:
						applicationRequirements = controllerMessage.getApplicationRequirements();
						flowId = controllerMessage.getFlowId();
						System.out.println("ControllerService: priority value request from client " + clientNodeId + " for flow " + flowId);
						flowApplicationRequirements.put(flowId, applicationRequirements);
						flowPriorities = flowPrioritySelector.getFlowPriorities(flowPriorities, flowApplicationRequirements);
						flowStartTimes.put(flowId, System.currentTimeMillis());
						
						responseMessage = new ControllerMessage(ControllerMessage.MessageType.FLOW_PRIORITIES_UPDATE, GenericPacket.UNUSED_FIELD, new int[0], new int[0], null, null, GenericPacket.UNUSED_FIELD, null, null, null, null, null, flowPriorities);
						// Send the new priority value to the client
						try {
							E2EComm.sendUnicast(clientDest, controllerMessage.getClientPort(), PROTOCOL, E2EComm.serialize(responseMessage));
						} catch (Exception e) {
							e.printStackTrace();
						}
						// Send the new priority value to every other active node
						for (Node node : topologyGraph.getNodeSet()) {
							if (Integer.parseInt(node.getId()) != clientNodeId) {
								String[] dest = Resolver.getInstance(false).resolveBlocking(Integer.parseInt(node.getId())).get(0).getPath();
								try {
									E2EComm.sendUnicast(dest, node.getAttribute("port"), PROTOCOL, E2EComm.serialize(responseMessage));
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
						System.out.println("ControllerService: priority value request for flow " + flowId + " from client " + clientNodeId + ", response message successfully sent to the client and every other active node");
						break;
					case MULTICAST_REQUEST:
						int clientPort = controllerMessage.getClientPort();
						int[] destNodeIds = controllerMessage.getDestNodeIds();
						int[] destPorts = controllerMessage.getDestPorts();
						applicationRequirements = controllerMessage.getApplicationRequirements();
						flowId = controllerMessage.getFlowId();
						System.out.println("ControllerService: multicast request from client " + clientNodeId + " for flow " + flowId);
						flowApplicationRequirements.put(flowId, applicationRequirements);
						
						// Select the paths and collect the control informations to send
						pathSelector = defaultPathSelector;
						pathSelectionMetric = controllerMessage.getPathSelectionMetric();
						if (pathSelectionMetric != null) {
							if (pathSelectionMetric == TopologyGraphSelector.PathSelectionMetric.BREADTH_FIRST)
								pathSelector = new BreadthFirstFlowPathSelector(topologyGraph);
							else if (pathSelectionMetric == TopologyGraphSelector.PathSelectionMetric.FEWEST_INTERSECTIONS)
								pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);
							else if (pathSelectionMetric == TopologyGraphSelector.PathSelectionMetric.MINIMUM_LOAD)
								pathSelector = new MinimumLoadFlowPathSelector(topologyGraph);
						}
						Map<Integer, List<PathDescriptor>> nodesNextHops = new HashMap<Integer, List<PathDescriptor>>();
						for (int i = 0; i < destNodeIds.length; i++) {
							PathDescriptor pathDescriptor = pathSelector.selectPath(clientNodeId, destNodeIds[i], null, null);
							String[] firstHopAddress = new String[] {pathDescriptor.getPath()[0]};
							List<Integer> firstHopId = new ArrayList<Integer>();
							firstHopId.add(pathDescriptor.getPathNodeIds().get(0));
							PathDescriptor firstHopPathDescriptor = new MulticastPathDescriptor(firstHopAddress, firstHopId, -1);
							List<PathDescriptor> clientNextHops = nodesNextHops.get(clientNodeId);
							if (clientNextHops == null) {
								clientNextHops = new ArrayList<PathDescriptor>();
								nodesNextHops.put(clientNodeId, clientNextHops);
							}
							boolean found = false;
							for (PathDescriptor clientNextHop : clientNextHops)
								if (clientNextHop.getPathNodeIds().get(0) == firstHopPathDescriptor.getPathNodeIds().get(0))
									found = true;
							if (found == false)
								clientNextHops.add(firstHopPathDescriptor);
							for (int j = 0; j < pathDescriptor.getPath().length; j++) {
								int currentNodeId = pathDescriptor.getPathNodeIds().get(j);
								String[] nextHopAddress = new String[1];
								List<Integer> nextHopId = new ArrayList<Integer>();
								if (j == pathDescriptor.getPath().length-1) {
									nextHopAddress[0] = pathDescriptor.getPath()[j];
									nextHopId.add(pathDescriptor.getPathNodeIds().get(j));
								}
								else {
									nextHopAddress[0] = pathDescriptor.getPath()[j+1];
									nextHopId.add(pathDescriptor.getPathNodeIds().get(j+1));
								}
								PathDescriptor nextHopPathDescriptor = null;
								if (j == pathDescriptor.getPath().length-1)
									nextHopPathDescriptor = new MulticastPathDescriptor(nextHopAddress, nextHopId, destPorts[i]);
								else
									nextHopPathDescriptor = new MulticastPathDescriptor(nextHopAddress, nextHopId, -1);
								List<PathDescriptor> currentNodeNextHops = nodesNextHops.get(currentNodeId);
								if (currentNodeNextHops == null) {
									currentNodeNextHops = new ArrayList<PathDescriptor>();
									nodesNextHops.put(currentNodeId, currentNodeNextHops);
								}
								found = false;
								for (PathDescriptor currentNodeNextHop : currentNodeNextHops)
									if (currentNodeNextHop.getPathNodeIds().get(0) == nextHopPathDescriptor.getPathNodeIds().get(0))
										found = true;
								if (found == false)
									currentNodeNextHops.add(nextHopPathDescriptor);
							}
						}
						
						// Send control message to every node in the path
						for (Integer nodeId : nodesNextHops.keySet()) {
							if (nodeId != clientNodeId) {
								List<PathDescriptor> nodeNextHops = nodesNextHops.get(nodeId);
								responseMessage = new ControllerMessage(ControllerMessage.MessageType.MULTICAST_CONTROL, ControllerMessage.UNUSED_FIELD, new int[0], new int[0], null, null, flowId, nodeNextHops, null, null, null, null, null);
								String[] dest = Resolver.getInstance(false).resolveBlocking(nodeId).get(0).getPath();
								try {
									E2EComm.sendUnicast(dest, topologyGraph.getNode(Integer.toString(nodeId)).getAttribute("port"), PROTOCOL, E2EComm.serialize(responseMessage));
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
						// Send response control message to the client after the other nodes, so that the path is ready when the message is sent
						List<PathDescriptor> clientNodeNextHops = nodesNextHops.get(clientNodeId);
						responseMessage = new ControllerMessage(ControllerMessage.MessageType.MULTICAST_CONTROL, ControllerMessage.UNUSED_FIELD, new int[0], new int[0], null, null, flowId, clientNodeNextHops, null, null, null, null, null);
						try {
							E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(responseMessage));
						} catch (Exception e) {
							e.printStackTrace();
						}
						
//						Kruskal kruskal = new Kruskal("kruskal", "intree", "notintree");
//						kruskal.init(topologyGraph);
//						kruskal.compute();
//						Map<Integer, List<PathDescriptor>> nodesNextHops = new HashMap<Integer, List<PathDescriptor>>();
//						for (int i = 0; i < destNodeIds.length; i++) {
//							MultiNode destNode = topologyGraph.getNode(Integer.toString(destNodeIds[i]));
//							MultiNode nextNode = null;
//							Iterator<Edge> iterator = destNode.getEdgeIterator();
//							while (iterator.hasNext()) {
//								Edge edge = iterator.next();
//								if (edge.getAttribute("kruskal").equals("intree")) {
//									nextNode = edge.getOpposite(destNode);
//									if (nextNode.getId().equals(Integer.toString(clientNodeId))) {
//										String nodeAddress = edge.getAttribute("address_" + destNode.getId());
//										List<Integer> nodeIdList = new ArrayList<Integer>();
//										nodeIdList.add(Integer.parseInt(destNode.getId()));
//										PathDescriptor pathDescriptor = new PathDescriptor(new String[] {nodeAddress}, nodeIdList);
//										List<PathDescriptor> nodeNextHops = nodesNextHops.get(Integer.parseInt(nextNode.getId()));
//										if (nodeNextHops == null) {
//											nodeNextHops = new ArrayList<PathDescriptor>();
//											nodesNextHops.put(Integer.parseInt(nextNode.getId()), nodeNextHops);
//										}
//										nodeNextHops.add(pathDescriptor);
//									}
//								}
//							}
//						}
						break;
					default:
						break;
					}
				}
			}
		}
	}
	
	private class UpdateManager extends Thread {
		
		// Interval between each update
		private static final int TIME_INTERVAL = 10*1000;
		private boolean active;
		
		UpdateManager() {
			this.active = true;
		}
		
		public void stopUpdateManager() {
			System.out.println("ControllerService UpdateManager STOP");
			this.active = false;
		}
		
		private void updateTopology() {
//			// Add the new neighbors to the reachable neighbors for the controller node
//			
//			// Data structure to hold IDs of nodes removed during the initial refresh and the respective node objects (nodeId, node)
//			HashMap<String, Node> removedNodes = new HashMap<String, Node>();
//			// Data structure to hold mappings between IDs of nodes removed during the initial refresh and the controller node addresses previously added on the graph by them (targetNodeId, controllerNodeAddress)
//			HashMap<String, String> removedNodesAddresses = new HashMap<String, String>();
//			// If the controller node doesn't exist in the topology graph, create it
//			Node controllerNode = topologyGraph.getNode(Dispatcher.getLocalRampIdString());
//			if (controllerNode == null) {
//				controllerNode = topologyGraph.addNode(Dispatcher.getLocalRampIdString());
//				controllerNode.addAttribute("ui.label", controllerNode.getId());
//			}
//			// If the controller node already exists in the topology graph, clear all the informations previously added by it
//			else {
//				for (Edge edge : controllerNode.getEachEdge()) {
//					Node targetNode = edge.getOpposite(controllerNode);
//					edge.removeAttribute("address_" + targetNode.getId());
//					// Check if this neighbor is still reachable by some other node, if not, add it to the nodes to be removed
//					boolean found = false;
//					for (Edge targetNodeEdge : targetNode.getEachEdge()) {
//						if (targetNodeEdge.getAttribute("address_" + targetNode.getId()) != null) {
//							found = true;
//							break;
//						}
//					}
//					if (found == false) {
//						removedNodes.put(targetNode.getId(), targetNode);
//						removedNodesAddresses.put(targetNode.getId(), edge.getAttribute("address_" + controllerNode.getId()));
//					}
//				}
//				// Remove every no more reachable node
//				for (String nodeId : removedNodes.keySet())
//					topologyGraph.removeNode(nodeId);
				// Remove every node that is no more active
				MultiNode controllerNode = topologyGraph.getNode(Dispatcher.getLocalRampIdString());
				Long nodeLastUpdate = null;
				for (Node node : topologyGraph.getNodeSet()) {
					nodeLastUpdate = node.getAttribute("last_update");
					if (nodeLastUpdate != null) {
						long elapsed = System.currentTimeMillis() - nodeLastUpdate;
						if (elapsed > GRAPH_NODES_TTL && !node.equals(controllerNode)) {
							activeClients.remove(Integer.parseInt(node.getId()));
							topologyGraph.removeNode(node);
						}
					}
				}
//			}
//			
//			// Iterate over the new neighbors
//			Vector<InetAddress> addresses = Heartbeater.getInstance(false).getNeighbors();
//			for (InetAddress address : addresses) {
//				Integer nodeId = Heartbeater.getInstance(false).getNodeId(address);
//				// Consider the nodes which have joined the service and haven't left yet
//				if (activeClients.contains(nodeId)) {
//					// If the neighbor node doesn't exist in the topology graph, create it
//					Node neighborNode = topologyGraph.getNode(nodeId.toString());
//					if (neighborNode == null) {
//						neighborNode = topologyGraph.addNode(nodeId.toString());
//						if (removedNodes.containsKey(neighborNode.getId()))
//							for (String key : removedNodes.get(neighborNode.getId()).getAttributeKeySet())
//								neighborNode.addAttribute(key, (Object) removedNodes.get(neighborNode.getId()).getAttribute(key));
//						else
//							neighborNode.addAttribute("ui.label", neighborNode.getId());
//					}
//					// If an edge between this neighbor and the controller node doesn't exist in the topology graph, create it
//					Edge neighborEdge = controllerNode.getEdgeBetween(neighborNode);
//					if (neighborEdge == null)
//						neighborEdge = topologyGraph.addEdge(controllerNode.getId()+neighborNode.getId(), controllerNode, neighborNode);
//					// Add the address of the neighbor on the edge
//					String neighborAddress = address.getHostAddress();
//					neighborEdge.addAttribute("address_" + neighborNode.getId(), neighborAddress);
//					if (neighborEdge.getAttribute("ui.label") != null)
//						neighborEdge.addAttribute("ui.label", neighborEdge.getAttribute("address_" + controllerNode.getId()) + " - " + neighborAddress);
//					else
//						neighborEdge.addAttribute("ui.label", neighborAddress);
//					// If this neighbor was removed during the initial refresh, restore the controller node address
//					if (removedNodesAddresses.containsKey(neighborNode.getId())) {
//						String controllerNodeAddress = removedNodesAddresses.get(neighborNode.getId());
//						if (controllerNodeAddress != null) {
//							neighborEdge.addAttribute("address_" + controllerNode.getId(), controllerNodeAddress);
//							neighborEdge.addAttribute("ui.label", controllerNodeAddress + " - " + neighborEdge.getAttribute("ui.label"));
//						}
//					}
//				}
//			}
//			System.out.println("ControllerService: local topology updated");
//			for (Node node : topologyGraph.getNodeSet()) {
//				System.out.println("Topology node, id: " + node.getId());
//				for (Edge edge : node.getEachEdge()) {
//					System.out.println("Node edge between " + edge.getNode0().getId() + " and " + edge.getNode1().getId());
//					for (String key : edge.getAttributeKeySet())
//						System.out.println("Node edge address " + key + ": " + edge.getAttribute(key));
//				}
//			}
		}
		
		private void updateFlows() {
			for (Integer flowId : flowStartTimes.keySet()) {
				long flowStartTime = flowStartTimes.get(flowId);
				int duration = flowApplicationRequirements.get(flowId).getDuration();
				long elapsed = System.currentTimeMillis() - flowStartTime;
				if (elapsed > (duration+(duration/4))*1000) {
					flowStartTimes.remove(flowId);
					flowApplicationRequirements.remove(flowId);
					flowPaths.remove(flowId);
				}
			}
		}
		
		private void sendDefaultFlowPathsUpdate() {
			for (Node clientNode : topologyGraph.getNodeSet()) {
				int clientNodeId = Integer.parseInt(clientNode.getId());
				Map<Integer, PathDescriptor> defaultFlowPathMappings = defaultPathSelector.getAllPathsFromSource(clientNodeId);
				ControllerMessage updateMessage = new ControllerMessage(ControllerMessage.MessageType.DEFAULT_FLOW_PATHS_UPDATE, ControllerMessage.UNUSED_FIELD, new int[0], new int[0], null, null, ControllerMessage.UNUSED_FIELD, null, null, null, null, defaultFlowPathMappings, null);
				String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5*1000).get(0).getPath();
				int clientPort = clientNode.getAttribute("port");
				try {
					E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(updateMessage));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		private void sendFlowPrioritiesUpdate() {
			for (Node clientNode : topologyGraph.getNodeSet()) {
				int clientNodeId = Integer.parseInt(clientNode.getId());
				flowPriorities = flowPrioritySelector.getFlowPriorities(flowPriorities, flowApplicationRequirements);
				ControllerMessage updateMessage = new ControllerMessage(ControllerMessage.MessageType.FLOW_PRIORITIES_UPDATE, ControllerMessage.UNUSED_FIELD, new int[0], new int[0], null, null, ControllerMessage.UNUSED_FIELD, null, null, null, null, null, flowPriorities);
				String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5*1000).get(0).getPath();
				int clientPort = clientNode.getAttribute("port");
				try {
					E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(updateMessage));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		@Override
		public void run() {
			System.out.println("ControllerService UpdateManager START");
			while (this.active == true) {
				try {
					Thread.sleep(TIME_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				updateTopology();
				updateFlows();
				if (flowPolicy == FlowPolicy.REROUTING)
					sendDefaultFlowPathsUpdate();
				else if (flowPolicy == FlowPolicy.SINGLE_FLOW || flowPolicy == FlowPolicy.QUEUES || flowPolicy == FlowPolicy.TRAFFIC_SHAPING)
					sendFlowPrioritiesUpdate();
			}
			System.out.println("ControllerService UpdateManager FINISHED");
		}
	}

}
