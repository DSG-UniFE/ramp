package it.unibo.deis.lia.ramp.core.internode.sdn.controllerService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.Resolver;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage.*;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import it.unibo.deis.lia.ramp.core.internode.sdn.routingPolicy.RoutingPolicy;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.TopologyGraphSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathSelectors.BreadthFirstFlowPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathSelectors.FewestIntersectionsFlowPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathSelectors.MinimumNetworkLoadFlowPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.MulticastPathDescriptor;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.prioritySelector.PrioritySelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.prioritySelector.ApplicationTypeFlowPrioritySelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage.ControllerMessageUpdate;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;

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
import org.graphstream.ui.swingViewer.DefaultView;
import org.graphstream.ui.view.Viewer;


/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerService extends Thread {

    private static final int PROTOCOL = E2EComm.TCP;
    private static final int GRAPH_NODES_TTL = 60 * 1000;

    private static ControllerService controllerService = null;
    private BoundReceiveSocket serviceSocket;
    private boolean active;
    private UpdateManager updateManager;
    private TrafficEngineeringPolicy trafficEngineeringPolicy;
    private RoutingPolicy routingPolicy;


//    /**
//     * Data structure to hold descriptors for the currently active clients (clientNodeId, descriptor)
//     */
//    private Map<Integer, ClientDescriptor> activeClients;
    /**
     * Data structure to hold the currently active clients (clientNodeId)
     */
    private Set<Integer> activeClients;

    /**
     * Data structure to hold a representation of the current network topology
     * (every node is identified by its nodeId, other informations are carried as an attribute)
     */
    private Graph topologyGraph;

    /**
     * Default path selector to be used when the controller clients does not specify anything
     */
    private TopologyGraphSelector defaultPathSelector;

    /**
     * Path selector to be used when the controller client specifies the one to be used
     */
    private TopologyGraphSelector flowPathSelector;

    /**
     * Data structure to hold current paths for the existing flows (flowId, path)
     */
    private Map<Integer, PathDescriptor> flowPaths;

    /**
     * Data structure to hold start times of the existing flows (flowId, startTime)
     */
    private Map<Integer, Long> flowStartTimes;

    /**
     * Data structure to hold application requirements for the existing flows (flowId, applicationRequirements)
     */
    private Map<Integer, ApplicationRequirements> flowApplicationRequirements;

    /**
     * Data structure to hold priorities for the existing flows (flowId, priority)
     */
    private Map<Integer, Integer> flowPriorities;
    private PrioritySelector flowPrioritySelector;

    /**
     * Data structure to hold current paths for the existing flows (flowId, path)
     */
    private Map<Integer, PathDescriptor> osLevelRoutes;

    private ControllerService() throws Exception {
        this.serviceSocket = E2EComm.bindPreReceive(PROTOCOL);
        ServiceManager.getInstance(false).registerService("SDNController", this.serviceSocket.getLocalPort(), PROTOCOL);
        this.active = true;
        this.updateManager = new UpdateManager();
        this.trafficEngineeringPolicy = TrafficEngineeringPolicy.SINGLE_FLOW;
        this.routingPolicy = RoutingPolicy.REROUTING;

        this.activeClients = new HashSet<Integer>();
        // TODO Check with Giannelli
        //this.activeClients.add(Dispatcher.getLocalRampId());
        this.topologyGraph = new MultiGraph("TopologyGraph");
        this.flowPathSelector = new MinimumNetworkLoadFlowPathSelector(this.topologyGraph);
        this.defaultPathSelector = new BreadthFirstFlowPathSelector(this.topologyGraph);
        this.flowPaths = new ConcurrentHashMap<Integer, PathDescriptor>();
        this.flowStartTimes = new ConcurrentHashMap<Integer, Long>();
        this.flowApplicationRequirements = new ConcurrentHashMap<Integer, ApplicationRequirements>();

        this.flowPriorities = new ConcurrentHashMap<Integer, Integer>();
        this.flowPrioritySelector = new ApplicationTypeFlowPrioritySelector();

        this.osLevelRoutes = new ConcurrentHashMap<>();
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

    /**
     * Creates the current topology graph by using GraphStream, a dynamic graph library
     * For more info: http://graphstream-project.org/
     */
    private void createGraph() {
        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
        this.topologyGraph.addAttribute("ui.quality");
        this.topologyGraph.addAttribute("ui.antialias");
        this.topologyGraph.addAttribute("ui.stylesheet", "node {fill-color: blue; size: 40px; }");
    }

    /**
     * Displays the topology graph in a dedicated JFrame
     */
    public void displayGraph() {
        createGraph();
        this.topologyGraph.display();
    }

    /**
     * Returns an object, containing the topology graph, in order to integrate it
     * in a Swing GUI. The DefaultView object inherits from JPanel so it is possible to
     * add it directly to a JFrame
     *
     * @return DefaultView
     */
    public DefaultView getGraph() {
        createGraph();
        Viewer viewer = new Viewer(this.topologyGraph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        viewer.enableAutoLayout();
        return (DefaultView) viewer.addDefaultView(false);
    }

    public void takeGraphScreenshot(String screenshotFilePath) {
        this.topologyGraph.addAttribute("ui.screenshot", screenshotFilePath);
    }

    public void updateFlowPolicy(TrafficEngineeringPolicy trafficEngineeringPolicy) {
        this.trafficEngineeringPolicy = trafficEngineeringPolicy;
        for (Node clientNode : this.topologyGraph.getNodeSet()) {
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.TRAFFIC_ENGINEERING_POLICY_UPDATE, null, null, trafficEngineeringPolicy, null, null, null);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("ControllerService: New flow policy set: " + this.trafficEngineeringPolicy.toString());
    }

    public void updateRoutingPolicy(RoutingPolicy routingPolicy) {
        this.routingPolicy = routingPolicy;
        for (Node clientNode : this.topologyGraph.getNodeSet()) {
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.ROUTING_POLICY_UPDATE, null, null, null, routingPolicy, null, null);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("ControllerService: New routing policy set: " + this.routingPolicy.toString());
    }

    public TrafficEngineeringPolicy getTrafficEngineeringPolicy() {
        return this.trafficEngineeringPolicy;
    }

    public Iterator<Integer> getActiveClients() {
        return this.activeClients.iterator();
    }

    @Override
    public void run() {
        System.out.println("ControllerService START");
        this.updateManager.start();
        while (active) {
            try {
                /*
                 * Receive packets from the clients and pass them to newly created handlers
                 */
                GenericPacket gp = E2EComm.receive(this.serviceSocket, 5 * 1000);
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
                            handleJoinService(controllerMessage, up.getSourceNodeId());
                            break;
                        case LEAVE_SERVICE:
                            handleLeaveService(up.getSourceNodeId());
                            break;
                        case PATH_REQUEST:
                            handlePathRequest((ControllerMessageRequest) controllerMessage, clientNodeId, clientDest);
                            break;
                        case TOPOLOGY_UPDATE:
                            handleTopologyUpdate((ControllerMessageUpdate) controllerMessage, clientNodeId);
                            break;
                        case PRIORITY_VALUE_REQUEST:
                            handlePriorityValueRequest((ControllerMessageRequest) controllerMessage, clientNodeId, clientDest);
                            break;
                        case MULTICAST_REQUEST:
                            handleMulticastRequest((ControllerMessageRequest) controllerMessage, clientNodeId, clientDest);
                            break;
                        case OS_ROUTING_REQUEST:
                            handleOsRoutingRequest((ControllerMessageRequest) controllerMessage, clientNodeId, clientDest);
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        private void handleJoinService(ControllerMessage controllerMessage, int clientNodeId) {
            // System.out.println("ControllerService: join request of size " +  packetSize + " received at " + timestamp);
            /*
             * Add the source node to the active clients and to the topology
             */
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
            updateFlowPolicy(trafficEngineeringPolicy);
        }

        private void handleLeaveService(int clientNodeId) {
            /*
             * Remove the source node from the active clients and from the topology
             */
            activeClients.remove(clientNodeId);
            topologyGraph.removeNode(Integer.toString(clientNodeId));
            System.out.println("ControllerService: leave request received from client " + clientNodeId + ", successfully removed from the topology");
        }

        private void handlePathRequest(ControllerMessageRequest requestMessage, int clientNodeId, String[] clientDest) {
            /*
             * Choose the best path for the specified flow, add it to the active flow paths and send it to the source node
             */
            int destNodeId = requestMessage.getDestNodeIds()[0];
            ApplicationRequirements applicationRequirements = requestMessage.getApplicationRequirements();
            int flowId = requestMessage.getFlowId();
            System.out.println("ControllerService: path request from client " + clientNodeId + " to node " + destNodeId + " for flow " + flowId);

            PathDescriptor newPath = null;
            TopologyGraphSelector pathSelector = flowPathSelector;
            PathSelectionMetric pathSelectionMetric = requestMessage.getPathSelectionMetric();
            if (pathSelectionMetric != null) {
                if (pathSelectionMetric == PathSelectionMetric.BREADTH_FIRST)
                    pathSelector = new BreadthFirstFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS)
                    pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.MINIMUM_NETWORK_LOAD)
                    pathSelector = new MinimumNetworkLoadFlowPathSelector(topologyGraph);
            }
            /*
             * If applicationRequirements is not null, this is the first path request for the flow,
             * a path for the flow is selected
             */
            if (applicationRequirements != null) {
                System.out.println("ControllerService: first path request for flow " + flowId + ", selecting a path");
                newPath = pathSelector.selectPath(clientNodeId, destNodeId, applicationRequirements, flowPaths);
                flowStartTimes.put(flowId, System.currentTimeMillis());
                flowApplicationRequirements.put(flowId, applicationRequirements);
            }
            /*
             * If applicationRequirements is null, the time-to-live of the previous path for the flow
             * has expired, if the duration hasn't expired too a new path is selected
             */
            else {
                if (flowPaths.containsKey(flowId)) {
                    System.out.println("ControllerService: new path request for flow " + flowId + ", the flow is still valid, selecting a new path");
                    newPath = pathSelector.selectPath(clientNodeId, destNodeId, null, flowPaths);
                } else
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
            ControllerMessageResponse responseMessage = new ControllerMessageResponse(MessageType.PATH_RESPONSE, ControllerMessage.UNUSED_FIELD, newPaths);
            try {
                /*
                 * Send the response message using the inverted source path
                 */
                E2EComm.sendUnicast(clientDest, requestMessage.getClientPort(), PROTOCOL, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("ControllerService: path request for flow " + flowId + " from client " + clientNodeId + ", response message successfully sent");
        }

        private void handleTopologyUpdate(ControllerMessageUpdate updateMessage, int clientNodeId) {
            // System.out.println("ControllerService: topology update size: " + packetSize);
            /*
             * Add the received nodes to the reachable neighbors for the source node
             */
            Map<Integer, List<String>> neighborNodes = updateMessage.getNeighborNodes();
            /*
             * Data structure to hold IDs of nodes removed during the initial
             * refresh and the respective node objects (nodeId, node)
             */
            HashMap<String, MultiNode> removedNodes = new HashMap<String, MultiNode>();
            /*
             * Data structure to hold mappings between IDs of nodes removed during the
             * initial refresh and the source node addresses added on the graph by
             * them (targetNodeId, sourceNodeAddress)
             */
            HashMap<String, List<String>> removedNodesAddresses = new HashMap<String, List<String>>();
            /*
             * If the source node doesn't exist in the topology graph, create it
             */
            MultiNode sourceGraphNode = topologyGraph.getNode(Integer.toString(clientNodeId));
            if (sourceGraphNode == null) {
                sourceGraphNode = topologyGraph.addNode(Integer.toString(clientNodeId));
                sourceGraphNode.addAttribute("port", updateMessage.getClientPort());
                for (String address : updateMessage.getNodeStats().keySet()) {
                    NodeStats nodeStats = updateMessage.getNodeStats().get(address);
                    if (nodeStats instanceof NetworkInterfaceStats) {
                        NetworkInterfaceStats networkInterfaceStats = (NetworkInterfaceStats) nodeStats;
                        sourceGraphNode.addAttribute("network_stats_" + address, networkInterfaceStats);
                        System.out.println("network stats: address " + address + " bytes " + networkInterfaceStats.getReceivedBytes() + " packets " + networkInterfaceStats.getReceivedPackets());
                    }
                }
                sourceGraphNode.addAttribute("last_update", System.currentTimeMillis());
                sourceGraphNode.addAttribute("ui.label", sourceGraphNode.getId());
            }
            /*
             * If the source node already exists in the topology graph, clear all the information previously added by it
             */
            else {
                for (Edge edge : sourceGraphNode.getEachEdge()) {
                    MultiNode targetNode = edge.getOpposite(sourceGraphNode);
                    edge.removeAttribute("address_" + targetNode.getId());
                    /*
                     * Check if this neighbor is still reachable by some other node, if not, add it to the nodes to be removed
                     */
                    boolean found = false;
                    for (Edge targetNodeEdge : targetNode.getEachEdge()) {
                        if (targetNodeEdge.getAttribute("address_" + targetNode.getId()) != null) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        removedNodes.put(targetNode.getId(), targetNode);
                        Collection<Edge> edgesToSourceNode = targetNode.getEdgeSetBetween(sourceGraphNode);
                        List<String> sourceNodeAddresses = new ArrayList<String>();
                        for (Edge edgeToSourceNode : edgesToSourceNode)
                            sourceNodeAddresses.add(edgeToSourceNode.getAttribute("address_" + sourceGraphNode.getId()));
                        removedNodesAddresses.put(targetNode.getId(), sourceNodeAddresses);
                    }
                }
                /*
                 * Remove every no more reachable node
                 */
                for (String nodeId : removedNodes.keySet())
                    topologyGraph.removeNode(nodeId);
            }
            /*
             * Iterate over the new neighbors
             */
            for (Integer neighborNodeId : neighborNodes.keySet()) {
                /*
                 * Consider only the nodes which have joined the service and haven't left yet
                 */
                if (activeClients.contains(neighborNodeId)) {
                    /*
                     * If the neighbor node doesn't exist in the topology graph, create it
                     */
                    MultiNode neighborGraphNode = topologyGraph.getNode(neighborNodeId.toString());
                    if (neighborGraphNode == null) {
                        neighborGraphNode = topologyGraph.addNode(neighborNodeId.toString());
                        /*
                         * If this node was removed during the initial refresh, restore every attribute it previously had
                         */
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
                        /*
                         * Consider every existing edge between this neighbor and the source node
                         */
                        Collection<Edge> neighborEdges = sourceGraphNode.getEdgeSetBetween(neighborGraphNode);
                        boolean found = false;
                        for (Edge neighborEdge : neighborEdges) {
                            SubnetInfo subnetInfo = neighborEdge.getAttribute("subnet_info");
                            /*
                             * If the address belongs to the subnet of the edge, add it to the edge
                             */
                            if (subnetInfo.isInRange(neighborAddress)) {
                                neighborEdge.addAttribute("address_" + neighborGraphNode.getId(), neighborAddress);
                                if (neighborEdge.getAttribute("ui.label") != null)
                                    neighborEdge.addAttribute("ui.label", neighborEdge.getAttribute("address_" + sourceGraphNode.getId()) + " - " + neighborAddress);
                                else
                                    neighborEdge.addAttribute("ui.label", neighborAddress);
                                found = true;
                            }
                        }
                        /*
                         * If an edge between this neighbor and the source node doesn't exist in the topology graph for the address subnet, create it
                         */
                        if (!found) {
                            Edge neighborEdge = topologyGraph.addEdge(sourceGraphNode.getId() + neighborGraphNode.getId() + "_" + neighborEdges.size(), sourceGraphNode, neighborGraphNode);
                            neighborEdge.addAttribute("address_" + neighborGraphNode.getId(), neighborAddress);
                            neighborEdge.addAttribute("subnet_info", neighborAddressSubnetInfo);
                            neighborEdge.addAttribute("ui.label", neighborAddress);
                            System.out.println("Added edge between " + neighborEdge.getNode0().getId() + " and " + neighborEdge.getNode1().getId());
                        }
                    }
                    /*
                     * If this neighbor was removed during the initial refresh, restore every source node address the node had previously added
                     */
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
            /*
             * Update the network stats and last update attributes of the client
             */
            for (String address : updateMessage.getNodeStats().keySet()) {
                NodeStats nodeStats = updateMessage.getNodeStats().get(address);
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
        }

        private void handlePriorityValueRequest(ControllerMessageRequest requestMessage, int clientNodeId, String[] clientDest) {
            ApplicationRequirements applicationRequirements = requestMessage.getApplicationRequirements();
            int flowId = requestMessage.getFlowId();
            System.out.println("ControllerService: priority value request from client " + clientNodeId + " for flow " + flowId);
            flowApplicationRequirements.put(flowId, applicationRequirements);
            flowPriorities = flowPrioritySelector.getFlowPriorities(flowPriorities, flowApplicationRequirements);
            flowStartTimes.put(flowId, System.currentTimeMillis());

            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.FLOW_PRIORITIES_UPDATE, null, null, null, null, null, flowPriorities);
            /*
             * Send the new priority value to the client
             */
            try {
                E2EComm.sendUnicast(clientDest, requestMessage.getClientPort(), PROTOCOL, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
            /*
             * Send the new priority value to every other active node
             */
            for (Node node : topologyGraph.getNodeSet()) {
                if (Integer.parseInt(node.getId()) != clientNodeId) {
                    String[] dest = Resolver.getInstance(false).resolveBlocking(Integer.parseInt(node.getId())).get(0).getPath();
                    try {
                        E2EComm.sendUnicast(dest, node.getAttribute("port"), PROTOCOL, E2EComm.serialize(updateMessage));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("ControllerService: priority value request for flow " + flowId + " from client " + clientNodeId + ", response message successfully sent to the client and every other active node");
        }

        private void handleMulticastRequest(ControllerMessageRequest requestMessage, int clientNodeId, String[] clientDest) {
            int clientPort = requestMessage.getClientPort();
            int[] destNodeIds = requestMessage.getDestNodeIds();
            int[] destPorts = requestMessage.getDestPorts();
            ApplicationRequirements applicationRequirements = requestMessage.getApplicationRequirements();
            int flowId = requestMessage.getFlowId();
            System.out.println("ControllerService: multicast request from client " + clientNodeId + " for flow " + flowId);
            flowApplicationRequirements.put(flowId, applicationRequirements);

            /*
             * Select the paths and collect the control information to send
             */
            TopologyGraphSelector pathSelector = defaultPathSelector;
            PathSelectionMetric pathSelectionMetric = requestMessage.getPathSelectionMetric();
            if (pathSelectionMetric != null) {
                if (pathSelectionMetric == PathSelectionMetric.BREADTH_FIRST)
                    pathSelector = new BreadthFirstFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS)
                    pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.MINIMUM_NETWORK_LOAD)
                    pathSelector = new MinimumNetworkLoadFlowPathSelector(topologyGraph);
            }
            /*
             * Si inizializza la struttura che sarà impiegata alla fine per inviare le istruzioni prima
             * ai nodi che fanno parte del percorso e infine al nodo client che ha richiesto il calcolo del percorso.
             */
            Map<Integer, List<PathDescriptor>> nodesNextHops = new HashMap<Integer, List<PathDescriptor>>();
            for (int i = 0; i < destNodeIds.length; i++) {
                /*
                 * Per ciascun nodo destinatario del messaggio multicast si calcola il percorso dal client usando il TopologyGraphSelector specificato
                 */
                PathDescriptor pathDescriptor = pathSelector.selectPath(clientNodeId, destNodeIds[i], null, null);
                /*
                 * Si recuperano le informazioni relative al primo hop dall'oggetto PathDescriptor appena creato
                 */
                String[] firstHopAddress = new String[]{pathDescriptor.getPath()[0]};
                List<Integer> firstHopId = new ArrayList<Integer>();
                firstHopId.add(pathDescriptor.getPathNodeIds().get(0));
                /*
                 *  Si crea un oggetto MulticastPathDescriptor inserendo le informazioni relative al primo hop
                 */
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
                if (!found)
                    clientNextHops.add(firstHopPathDescriptor);
                for (int j = 0; j < pathDescriptor.getPath().length; j++) {
                    int currentNodeId = pathDescriptor.getPathNodeIds().get(j);
                    String[] nextHopAddress = new String[1];
                    List<Integer> nextHopId = new ArrayList<Integer>();
                    if (j == pathDescriptor.getPath().length - 1) {
                        nextHopAddress[0] = pathDescriptor.getPath()[j];
                        nextHopId.add(pathDescriptor.getPathNodeIds().get(j));
                    } else {
                        nextHopAddress[0] = pathDescriptor.getPath()[j + 1];
                        nextHopId.add(pathDescriptor.getPathNodeIds().get(j + 1));
                    }
                    PathDescriptor nextHopPathDescriptor = null;
                    if (j == pathDescriptor.getPath().length - 1)
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
                    if (!found)
                        currentNodeNextHops.add(nextHopPathDescriptor);
                }
            }

            ControllerMessageResponse responseMessage = null;
            /*
             * Send control message to every node in the path
             */
            for (Integer nodeId : nodesNextHops.keySet()) {
                if (nodeId != clientNodeId) {
                    List<PathDescriptor> nodeNextHops = nodesNextHops.get(nodeId);
                    responseMessage = new ControllerMessageResponse(MessageType.MULTICAST_CONTROL, flowId, nodeNextHops);
                    String[] dest = Resolver.getInstance(false).resolveBlocking(nodeId).get(0).getPath();
                    try {
                        E2EComm.sendUnicast(dest, topologyGraph.getNode(Integer.toString(nodeId)).getAttribute("port"), PROTOCOL, E2EComm.serialize(responseMessage));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            /*
             * Send response control message to the client after the other nodes, so that the path is ready when the message is sent
             */
            List<PathDescriptor> clientNodeNextHops = nodesNextHops.get(clientNodeId);
            responseMessage = new ControllerMessageResponse(MessageType.MULTICAST_CONTROL, flowId, clientNodeNextHops);
            try {
                E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handleOsRoutingRequest(ControllerMessageRequest requestMessage, int clientNodeId, String[] clientDest) {
            int clientPort = requestMessage.getClientPort();
            int destNodeId = requestMessage.getDestNodeIds()[0];
            BoundReceiveSocket ackSocket = null;

            ApplicationRequirements applicationRequirements = requestMessage.getApplicationRequirements();
            TopologyGraphSelector pathSelector = defaultPathSelector;
            PathSelectionMetric pathSelectionMetric = requestMessage.getPathSelectionMetric();
            if (pathSelectionMetric != null) {
                if (pathSelectionMetric == PathSelectionMetric.BREADTH_FIRST)
                    pathSelector = new BreadthFirstFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS)
                    pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.MINIMUM_NETWORK_LOAD)
                    pathSelector = new MinimumNetworkLoadFlowPathSelector(topologyGraph);
            }

            System.out.println("ControllerService: first OS routing path request for node ID" + clientNodeId + ", selecting a path");

            /*
             * Generating the routeID
             */
            int routeId = ThreadLocalRandom.current().nextInt();
            while (routeId == GenericPacket.UNUSED_FIELD || osLevelRoutes.containsKey(routeId)) {
                routeId = ThreadLocalRandom.current().nextInt();
            }

            // TODO flowPaths to remove
            PathDescriptor newOSRoutingPath = pathSelector.selectPath(clientNodeId, destNodeId, applicationRequirements, flowPaths);

            int hopCount = newOSRoutingPath.getPathNodeIds().size();
            String destinationIP = newOSRoutingPath.getPath()[hopCount - 1];

            ControllerMessageResponse responseMessage;

            try {
                ackSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Index to keep track of the intermediate nodes to contact
             * in case of OS_ROUTING_ABORT.
             */
            int intermediateNodesToNotifyAbort = 0;
            boolean aborted = false;
            GenericPacket gp = null;
            String sourceIP = null;

            /*
             * Send an OS_ROUTING_ADD_ROUTE message to the client node in order to update
             * the routing tables and to discover which source IP the client will use
             * in case of multiple interfaces. For this reason the srcIP value in
             * OS_ROUTING_ADD_ROUTE is null.
             */
            MultiNode sourceNode = topologyGraph.getNode(Integer.toString(clientNodeId));
            String[] sourceDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int sourcePort = sourceNode.getAttribute("port");

            responseMessage = new ControllerMessageResponse(MessageType.OS_ROUTING_ADD_ROUTE, ackSocket.getLocalPort(), null, destinationIP, newOSRoutingPath.getPath()[0], routeId);
            try {
                E2EComm.sendUnicast(sourceDest, sourcePort, PROTOCOL, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Get Ack from the intermediate node in order to inform the ControllerService that
             * "ip route add" command has been successfully applied.
             */
            try {
                gp = E2EComm.receive(ackSocket);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (gp instanceof UnicastPacket) {
                UnicastPacket up = (UnicastPacket) gp;
                Object payload = null;
                try {
                    payload = E2EComm.deserialize(up.getBytePayload());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (payload instanceof ControllerMessageAck) {
                    ControllerMessageAck ackMessage = (ControllerMessageAck) payload;
                    if (ackMessage.getRouteId() == routeId) {
                        switch (ackMessage.getMessageType()) {
                            case OS_ROUTING_ACK:
                                sourceIP = ackMessage.getSrcIP();
                                break;
                            case OS_ROUTING_ABORT:
                                /*
                                 * The client node was not able to add the route.
                                 * So we don't need to send a OS_ROUTING_DELETE_ROUTE
                                 * message since the route does not exist.
                                 */
                                aborted = true;
                                break;
                            default:
                                break;
                        }
                    }
                }
            }

            if (!aborted) {
                /*
                 * Send an OS_ROUTING_ADD_ROUTE message to all intermediate nodes in order to update
                 * the routing tables.
                 */
                for (int i = 0; i < hopCount && !aborted; i++) {
                    int intermediateNodeId = newOSRoutingPath.getPathNodeIds().get(i);
                    /*
                     * Send an OS_ROUTING_ADD_ROUTE only if the intermediate node is not the destination.
                     */
                    if (intermediateNodeId != destNodeId) {
                        MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                        String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                        int intermediatePort = intermediateNode.getAttribute("port");

                        responseMessage = new ControllerMessageResponse(MessageType.OS_ROUTING_ADD_ROUTE, ackSocket.getLocalPort(), sourceIP, destinationIP, newOSRoutingPath.getPath()[i + 1], routeId);
                        try {
                            E2EComm.sendUnicast(intermediateDest, intermediatePort, PROTOCOL, E2EComm.serialize(responseMessage));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        /*
                         * Get Ack from the intermediate node in order to inform the ControllerService that
                         * "ip route add" command has been successfully applied.
                         */
                        try {
                            gp = E2EComm.receive(ackSocket);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (gp instanceof UnicastPacket) {
                            UnicastPacket up = (UnicastPacket) gp;
                            Object payload = null;
                            try {
                                payload = E2EComm.deserialize(up.getBytePayload());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (payload instanceof ControllerMessageAck) {
                                ControllerMessageAck ackMessage = (ControllerMessageAck) payload;
                                if (ackMessage.getRouteId() == routeId) {
                                    switch (ackMessage.getMessageType()) {
                                        case OS_ROUTING_ACK:
                                            intermediateNodesToNotifyAbort++;
                                            break;
                                        case OS_ROUTING_ABORT:
                                            aborted = true;
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /*
             * If some of the intermediate nodes could not add the route
             * we need to delete the route for that intermediate nodes
             * that added this route correctly.
             */
            if (aborted && intermediateNodesToNotifyAbort > 0) {
                for (int j = 0; j < intermediateNodesToNotifyAbort; j++) {
                    int intermediateNodeId = newOSRoutingPath.getPathNodeIds().get(j);

                    if ((intermediateNodeId != destNodeId)) {
                        MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                        String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                        int intermediatePort = intermediateNode.getAttribute("port");

                        responseMessage = new ControllerMessageResponse(MessageType.OS_ROUTING_DELETE_ROUTE, ControllerMessage.UNUSED_FIELD, null, null, null, routeId);
                        try {
                            E2EComm.sendUnicast(intermediateDest, intermediatePort, PROTOCOL, E2EComm.serialize(responseMessage));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            /*
             * Send an OS_ROUTING_RESPONSE message to the source without waiting for any ack since
             * all routes have been added or removed. If the route has been created correctly we return
             * back the routeId otherwise we return to the client node routeId = -1.
             */
            if (aborted) {
                routeId = -1;
            } else {
                osLevelRoutes.put(routeId, newOSRoutingPath);
            }
            responseMessage = new ControllerMessageResponse(MessageType.OS_ROUTING_RESPONSE, ControllerMessage.UNUSED_FIELD, null, null, null, routeId);
            try {
                E2EComm.sendUnicast(clientDest, clientPort, PROTOCOL, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class UpdateManager extends Thread {
        /*
         * Interval between each update
         */
        private static final int TIME_INTERVAL = 10 * 1000;
        private boolean active;

        UpdateManager() {
            this.active = true;
        }

        public void stopUpdateManager() {
            System.out.println("ControllerService UpdateManager STOP");
            this.active = false;
        }

        private void updateTopology() {
            /*
             * Remove every node that is no more active
             */
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
        }

        private void updateFlows() {
            for (Integer flowId : flowStartTimes.keySet()) {
                long flowStartTime = flowStartTimes.get(flowId);
                int duration = flowApplicationRequirements.get(flowId).getDuration();
                long elapsed = System.currentTimeMillis() - flowStartTime;
                if (elapsed > (duration + (duration / 4)) * 1000) {
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
                ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DEFAULT_FLOW_PATHS_UPDATE, null, null, null, null, defaultFlowPathMappings, null);
                String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
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
                ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.FLOW_PRIORITIES_UPDATE, ControllerMessage.UNUSED_FIELD, null, null, null, null, null, flowPriorities);
                String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
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
            while (this.active) {
                try {
                    Thread.sleep(TIME_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                updateTopology();
                updateFlows();
                // TODO There is no more mutual exclusion
                if (trafficEngineeringPolicy == TrafficEngineeringPolicy.REROUTING)
                    sendDefaultFlowPathsUpdate();
                else if (trafficEngineeringPolicy == TrafficEngineeringPolicy.SINGLE_FLOW || trafficEngineeringPolicy == TrafficEngineeringPolicy.QUEUES || trafficEngineeringPolicy == TrafficEngineeringPolicy.TRAFFIC_SHAPING)
                    sendFlowPrioritiesUpdate();
            }
            System.out.println("ControllerService UpdateManager FINISHED");
        }
    }

}
