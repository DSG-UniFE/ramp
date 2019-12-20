package it.unibo.deis.lia.ramp.core.internode.sdn.controllerService;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.Resolver;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataPlaneMessage.DataPlaneMessage;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManager;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.DataPlaneRulesManager;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage.*;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.graphUtils.GraphUtils;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors.BreadthFirstOsRoutingPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors.FewestIntersectionsOsRoutingPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors.MinimumNetworkLoadOsRoutingPathSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.osRoutingPathSelectors.OsRoutingTopologyGraphSelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.OsRoutingPathDescriptor;
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
import it.unibo.deis.lia.ramp.core.internode.sdn.prioritySelector.TrafficTypeFlowPrioritySelector;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage.ControllerMessageUpdate;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.Graphs;
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

    /**
     * Control flow ID value, to be used for control communications between ControllerService and ControllerClients.
     */
    private static final int CONTROL_FLOW_ID = 0;

    /**
     * Protocol used by the ControllerService to talk with ControllerClients
     */
    private static final int PROTOCOL = E2EComm.TCP;

    /**
     *
     */
    private static final int GRAPH_NODES_TTL = 60 * 1000;

    /**
     * ControllerService instance
     */
    private static ControllerService controllerService = null;

    /**
     * Socket used by the ControllerService to be reachable
     */
    private BoundReceiveSocket serviceSocket;

    /**
     * Boolean value that reports if the ControllerController is currently active.
     */
    private static boolean active;

    /**
     * This component is responsible to periodically send updates
     * to the subscribed ControllerClients.
     */
    private UpdateManager updateManager;

    /**
     * Current {@link TrafficEngineeringPolicy}
     */
    private TrafficEngineeringPolicy trafficEngineeringPolicy;

    /**
     * Current {@link RoutingPolicy}
     */
    private RoutingPolicy routingPolicy;

    /**
     * {@link DataTypesManager} instance
     */
    private DataTypesManager dataTypesManager;

    /**
     * {@link DataPlaneRulesManager} instance
     */
    private DataPlaneRulesManager dataPlaneRulesManager;

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
     * Default path selection metric to be used when the controller clients does not specify anything
     */
    private PathSelectionMetric defaultPathSelectionMetric;

    /**
     * Path selector to be used for oSRouting-based communication when the controller client does not specify anything
     */
    private OsRoutingTopologyGraphSelector osRoutingPathSelector;

    /**
     * Data structure to hold current paths for the existing flows (flowId, path)
     */
    private Map<Integer, PathDescriptor> flowPaths;

    /**
     * Data structure to hold start times of the existing flows (flowId, startTime)
     */
    private Map<Integer, Long> flowStartTimes;

    /**
     * Data structure to hold the path selection metric of the existing flows (flowId, pathSelectionMeteic)
     */
    private Map<Integer, PathSelectionMetric> flowPathSelectionMetrics;

    /**
     * Data structure to hold application requirements for the existing flows (flowId, applicationRequirements)
     */
    private Map<Integer, ApplicationRequirements> flowApplicationRequirements;

    /**
     * Data structure to hold priorities for the existing flows (flowId, priority)
     */
    private Map<Integer, Integer> flowPriorities;

    /**
     * This Object will return the priority value for a given TrafficType
     */
    private PrioritySelector flowPrioritySelector;

    /**
     * Data structure to hold current forward paths for the existing os routing paths (routeId, path)
     */
    private Map<Integer, OsRoutingPathDescriptor> forwardOsRoutingPaths;

    /**
     * Data structure to hold current backward paths for the existing os routing paths (routeId, path)
     */
    private Map<Integer, OsRoutingPathDescriptor> backwardOsRoutingPaths;

    /**
     * Data structure to hold start times of the existing os routing paths (routeId, startTime)
     */
    private Map<Integer, Long> osRoutesStartTimes;

    /**
     * Data structure to hold application requirements for the existing os routing paths (routeId, applicationRequirements)
     */
    private Map<Integer, ApplicationRequirements> osRoutesApplicationRequirements;

    /**
     * This String specifies the directory the ControllerService must use in order to store files sent
     * to ControllerClients, for instance the dgs file for the topologyGraph or other files to be used
     * in future extensions of this class.
     */
    private String sdnControllerDirectory = "./temp/sdnController";

    private ControllerService() throws Exception {
        this.serviceSocket = E2EComm.bindPreReceive(PROTOCOL);
        ServiceManager.getInstance(false).registerService("SDNController", this.serviceSocket.getLocalPort(), PROTOCOL);
        active = true;
        this.updateManager = new UpdateManager();

        this.trafficEngineeringPolicy = TrafficEngineeringPolicy.SINGLE_FLOW;
        this.routingPolicy = RoutingPolicy.REROUTING;

        this.activeClients = new HashSet<>();
        // TODO Check with Giannelli
        //this.activeClients.add(Dispatcher.getLocalRampId());
        this.topologyGraph = new MultiGraph("TopologyGraph");
        this.defaultPathSelector = new BreadthFirstFlowPathSelector(this.topologyGraph);
        this.defaultPathSelectionMetric = PathSelectionMetric.BREADTH_FIRST;
        this.osRoutingPathSelector = new MinimumNetworkLoadOsRoutingPathSelector(this.topologyGraph);

        this.flowPaths = new ConcurrentHashMap<>();
        this.flowStartTimes = new ConcurrentHashMap<>();
        this.flowPathSelectionMetrics = new ConcurrentHashMap<>();
        this.flowApplicationRequirements = new ConcurrentHashMap<>();

        this.flowPriorities = new ConcurrentHashMap<>();
        this.flowPrioritySelector = new TrafficTypeFlowPrioritySelector();

        this.forwardOsRoutingPaths = new ConcurrentHashMap<>();
        this.backwardOsRoutingPaths = new ConcurrentHashMap<>();

        this.osRoutesStartTimes = new ConcurrentHashMap<>();
        this.osRoutesApplicationRequirements = new ConcurrentHashMap<>();

        this.dataTypesManager = DataTypesManager.getInstance();

        this.dataPlaneRulesManager = DataPlaneRulesManager.getInstance();
        this.setName("ControllerServiceThread");

        File dir = new File(sdnControllerDirectory);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                System.out.println("ControllerService: " + dir.getName() + " folder created.");
            } else {
                System.out.println("ControllerService: the folder " + dir.getName() + " already exists.");
            }
        }
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

    public synchronized static boolean isActive() {
        return active;
    }


    public void stopService() {
        System.out.println("ControllerService STOP");
        ServiceManager.getInstance(false).removeService("SDNController");
        active = false;
        try {
            this.serviceSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.updateManager.stopUpdateManager();

        if (!ControllerClient.isActive()) {
            this.dataPlaneRulesManager.deactivate();
            this.dataPlaneRulesManager = null;
            this.dataTypesManager.deactivate();
            this.dataTypesManager = null;
        }

        controllerService = null;
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

    /**
     * Save the current topology graph as an image file
     */
    public void takeGraphScreenshot(String screenshotFilePath) {
        this.topologyGraph.addAttribute("ui.screenshot", screenshotFilePath);
    }

    /**
     * Get the list of the Controller Client currently managed by this Controller Service
     */
    public Set<Integer> getActiveClients() {
        return this.activeClients;
    }

    /**
     * Get the active TrafficEngineeringPolicy
     *
     * @see TrafficEngineeringPolicy
     */
    public TrafficEngineeringPolicy getTrafficEngineeringPolicy() {
        return this.trafficEngineeringPolicy;
    }

    /**
     * Update the active TrafficEngineeringPolicy for all the topology
     */
    public void updateTrafficEngineeringPolicy(TrafficEngineeringPolicy trafficEngineeringPolicy) {
        this.trafficEngineeringPolicy = trafficEngineeringPolicy;
        for (Node clientNode : this.topologyGraph.getNodeSet()) {
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.TRAFFIC_ENGINEERING_POLICY_UPDATE, null, null, trafficEngineeringPolicy, null, null, null, null, null, null);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("ControllerService: New flow policy set: " + this.trafficEngineeringPolicy.toString());
    }

    /**
     * Get the active RoutingPolicy
     *
     * @see RoutingPolicy
     */
    public RoutingPolicy getRoutingPolicy() {
        return routingPolicy;
    }

    public void updateRoutingPolicy(RoutingPolicy routingPolicy) {
        this.routingPolicy = routingPolicy;
        for (Node clientNode : this.topologyGraph.getNodeSet()) {
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.ROUTING_POLICY_UPDATE, null, null, null, routingPolicy, null, null, null, null, null);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("ControllerService: New routing policy set: " + this.routingPolicy.toString());
    }

    /**
     * Get the list of DataTypes currently usable by the network
     * controlled by this Controller Service.
     *
     * @return
     */
    public Set<String> getAvailableDataTypes() {
        return this.dataTypesManager.getAvailableDataTypes();
    }

    /**
     * Add a new DataType defined by the administrator of this SDN and
     * spread its definition to all the Controller Clients managed by
     * this ControllerService.
     *
     * @param dataTypeFileName
     * @param dataTypeFile
     * @return
     */
    public boolean addUserDefinedDataType(String dataTypeFileName, File dataTypeFile) {
        BoundReceiveSocket ackSocket = null;
        GenericPacket gp = null;

        try {
            ackSocket = E2EComm.bindPreReceive(PROTOCOL);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collection<Node> nodeSet = this.topologyGraph.getNodeSet();
        Node[] nodesArray = nodeSet.toArray(new Node[0]);
        int nodesArrayLen = nodesArray.length;
        /*
         * Index to keep track of the nodes to contact
         * in case of DATA_PLANE_DATA_TYPE_ABORT.
         */
        int intermediateNodesToNotifyAbort = 0;
        boolean aborted = false;

        byte[] fileContent = null;

        try {
            fileContent = Files.readAllBytes(dataTypeFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        String dataTypeClassName = dataTypeFileName.replaceFirst("[.][^.]+$", "");
        DataPlaneMessage dataPlaneMessage = new DataPlaneMessage(dataTypeFileName, dataTypeClassName, fileContent);

        for (int i = 0; i < nodesArrayLen && !aborted; i++) {
            Node clientNode = nodesArray[i];
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DATA_PLANE_ADD_DATA_TYPE, ackSocket.getLocalPort(), null, null, null, routingPolicy, null, null, dataPlaneMessage, null, null);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Get Ack from each node in order to inform the ControllerService that
             * the user defined DataType class has been successfully added.
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
                    switch (ackMessage.getMessageType()) {
                        case DATA_PLANE_DATA_TYPE_ACK:
                            intermediateNodesToNotifyAbort++;
                            break;
                        case DATA_PLANE_DATA_TYPE_ABORT:
                            aborted = true;
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        if (aborted && intermediateNodesToNotifyAbort > 0) {
            for (int j = 0; j < intermediateNodesToNotifyAbort; j++) {
                Node clientNode = nodesArray[j];
                int clientNodeId = Integer.parseInt(clientNode.getId());
                ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DATA_PLANE_REMOVE_DATA_TYPE, ackSocket.getLocalPort(), null, null, null, null, null, null, null, dataTypeClassName, null);
                String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
                int clientPort = clientNode.getAttribute("port");
                try {
                    E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (aborted) {
            System.out.println("ControllerService: user defined DataType: " + dataTypeFileName + " added.");
            return false;
        } else {
            System.out.println("ControllerService: user defined DataType: " + dataTypeFileName + " not added.");
            return true;
        }
    }

    /**
     * Get the list of DataPlaneRules currently usable by the network
     * controlled by this Controller Service.
     *
     * @return
     */
    public Set<String> getAvailableDataPlaneRules() {
        return this.dataPlaneRulesManager.getAvailableDataPlaneRules();
    }

    /**
     * Get the list of DataPlaneRules currently active by the network
     * controlled by this Controller Service.
     *
     * @return
     */
    public Map<String, List<String>> getActiveDataPlaneRules() {
        return dataPlaneRulesManager.getActiveDataPlaneRulesByDataType();
    }

    /**
     * Add a new DataPlaneRule defined by the administrator of this SDN and
     * spread its definition to all the Controller Clients managed by
     * this ControllerService.
     *
     * @param dataPlaneRuleFileName
     * @param dataPlaneRuleFile
     * @return
     */
    public boolean addUserDefinedDataPlaneRule(String dataPlaneRuleFileName, File dataPlaneRuleFile) {
        BoundReceiveSocket ackSocket = null;
        GenericPacket gp = null;

        try {
            ackSocket = E2EComm.bindPreReceive(PROTOCOL);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collection<Node> nodeSet = this.topologyGraph.getNodeSet();
        Node[] nodesArray = nodeSet.toArray(new Node[0]);
        int nodesArrayLen = nodesArray.length;

        /*
         * Index to keep track of the nodes to contact
         * in case of DATA_PLANE_RULE_ABORT.
         */
        int intermediateNodesToNotifyAbort = 0;
        boolean aborted = false;

        byte[] fileContent = null;

        try {
            fileContent = Files.readAllBytes(dataPlaneRuleFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        String dataPlaneRuleClassName = dataPlaneRuleFileName.replaceFirst("[.][^.]+$", "");
        DataPlaneMessage dataPlaneMessage = new DataPlaneMessage(dataPlaneRuleFileName, dataPlaneRuleClassName, fileContent);

        for (int i = 0; i < nodesArrayLen && !aborted; i++) {
            Node clientNode = nodesArray[i];
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DATA_PLANE_ADD_RULE_FILE, ackSocket.getLocalPort(), null, null, null, null, null, null, dataPlaneMessage, null, null);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Get Ack from each node in order to inform the ControllerService that
             * the user defined DataPlaneRule class has been successfully added.
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
                    switch (ackMessage.getMessageType()) {
                        case DATA_PLANE_RULE_ACK:
                            intermediateNodesToNotifyAbort++;
                            break;
                        case DATA_PLANE_RULE_ABORT:
                            aborted = true;
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        if (aborted && intermediateNodesToNotifyAbort > 0) {
            for (int j = 0; j < intermediateNodesToNotifyAbort; j++) {
                Node clientNode = nodesArray[j];
                int clientNodeId = Integer.parseInt(clientNode.getId());

                ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DATA_PLANE_REMOVE_RULE_FILE, ackSocket.getLocalPort(), null, null, null, null, null, null, null, null, dataPlaneRuleClassName);
                String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
                int clientPort = clientNode.getAttribute("port");
                try {
                    E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (aborted) {
            System.out.println("ControllerService: user defined DataPlaneRule: " + dataPlaneRuleFileName + " added.");
            return false;
        } else {
            System.out.println("ControllerService: user defined DataPlaneRule: " + dataPlaneRuleFileName + " not added.");
            return true;
        }
    }

    /**
     * Enable a usable DataPlaneRule for a given DataType
     *
     * @param dataType
     * @param dataPlaneRule
     * @return
     */
    public boolean addDataPlaneRule(String dataType, String dataPlaneRule) {
        return addDataPlaneRule(dataType, dataPlaneRule, null);
    }

    public boolean addDataPlaneRule(String dataType, String dataPlaneRule, List<Integer> clientsNodeToNotify) {
        BoundReceiveSocket ackSocket = null;
        GenericPacket gp = null;

        try {
            ackSocket = E2EComm.bindPreReceive(PROTOCOL);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collection<Node> nodeSet;
        /*
         * If clientsNodeToNotify is null the addDataPlaneRule
         * is applied for all the clients nodes.
         * Otherwise are notified only the clientNodes specified.
         */
        if (clientsNodeToNotify == null) {
            nodeSet = this.topologyGraph.getNodeSet();
        } else {
            nodeSet = new ArrayList<>();
            for (Integer clientNode : clientsNodeToNotify) {
                nodeSet.add(this.topologyGraph.getNode(clientNode.toString()));
            }
        }

        Node[] nodesArray = nodeSet.toArray(new Node[0]);
        int nodesArrayLen = nodesArray.length;

        /*
         * Index to keep track of the intermediate nodes to contact
         * in case of DATA_PLANE_RULE_ABORT.
         */
        int intermediateNodesToNotifyAbort = 0;
        boolean aborted = false;

        for (int i = 0; i < nodesArrayLen && !aborted; i++) {
            Node clientNode = nodesArray[i];
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DATA_PLANE_ADD_RULE, ackSocket.getLocalPort(), null, null, null, routingPolicy, null, null, null, dataType, dataPlaneRule);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Get Ack from the each node in order to inform the ControllerService that
             * the data plane rule has been successfully added.
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
                    switch (ackMessage.getMessageType()) {
                        case DATA_PLANE_RULE_ACK:
                            intermediateNodesToNotifyAbort++;
                            break;
                        case DATA_PLANE_RULE_ABORT:
                            aborted = true;
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        if (aborted && intermediateNodesToNotifyAbort > 0) {
            for (int j = 0; j < intermediateNodesToNotifyAbort; j++) {
                Node clientNode = nodesArray[j];
                int clientNodeId = Integer.parseInt(clientNode.getId());
                ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DATA_PLANE_REMOVE_RULE, ackSocket.getLocalPort(), null, null, null, routingPolicy, null, null, null, dataType, dataPlaneRule);
                String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
                int clientPort = clientNode.getAttribute("port");
                try {
                    E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (aborted) {
            System.out.println("ControllerService: data plane rule: " + dataPlaneRule + " not added for data type: " + dataType);
            return false;
        } else {
            System.out.println("ControllerService: data plane rule: " + dataPlaneRule + " added for data type: " + dataType);
            return true;
        }
    }

    /**
     * Disable an active DataPlaneRule for a given DataType
     *
     * @param dataType
     * @param dataPlaneRule
     */
    public void removeDataPlaneRule(String dataType, String dataPlaneRule) {
        removeDataPlaneRule(dataType, dataPlaneRule, null);
    }

    public void removeDataPlaneRule(String dataType, String dataPlaneRule, List<Integer> clientsNodeToNotify) {
        Collection<Node> nodeSet;
        /*
         * If clientsNodeToNotify is null the addDataPlaneRule
         * is applied for all the clients nodes.
         * Otherwise are notified only the clientNodes specified.
         */
        if (clientsNodeToNotify == null) {
            nodeSet = this.topologyGraph.getNodeSet();
        } else {
            nodeSet = new ArrayList<>();
            for (Integer clientNode : clientsNodeToNotify) {
                nodeSet.add(this.topologyGraph.getNode(clientNode.toString()));
            }
        }

        for (Node clientNode : nodeSet) {
            int clientNodeId = Integer.parseInt(clientNode.getId());
            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DATA_PLANE_REMOVE_RULE, null, null, null, routingPolicy, null, null, null, dataType, dataPlaneRule);
            String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
            int clientPort = clientNode.getAttribute("port");
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("ControllerService: data plane rule: " + dataPlaneRule + " removed for data type: " + dataType);
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
                        case FIX_PATH_REQUEST:
                            handleFixPathRequest((ControllerMessageRequest) controllerMessage, clientNodeId, clientDest);
                            break;
                        case TOPOLOGY_GRAPH_REQUEST:
                            handleTopologyGraphRequest((ControllerMessageRequest) controllerMessage, clientNodeId, clientDest);
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
            clientNode.addAttribute("ui.style", "fill-color: rgb(35,118,185);");
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
            /*
             * Update the ControllerClient with the current policies.
             */
            updateTrafficEngineeringPolicy(trafficEngineeringPolicy);
            updateRoutingPolicy(routingPolicy);
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
            TopologyGraphSelector pathSelector = defaultPathSelector;
            PathSelectionMetric pathSelectionMetric = requestMessage.getPathSelectionMetric();
            if (pathSelectionMetric != null) {
                if (pathSelectionMetric == PathSelectionMetric.BREADTH_FIRST)
                    pathSelector = new BreadthFirstFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS)
                    pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.MINIMUM_NETWORK_LOAD)
                    pathSelector = new MinimumNetworkLoadFlowPathSelector(topologyGraph);
            } else {
                pathSelectionMetric = defaultPathSelectionMetric;
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
                flowPathSelectionMetrics.put(flowId, pathSelectionMetric);
            }
            /*
             * If applicationRequirements is null, the time-to-live of the previous path for the flow
             * has expired, if the duration hasn't expired too a new path is selected
             */
            else {
                if (flowPaths.containsKey(flowId)) {
                    System.out.println("ControllerService: new path request for flow " + flowId + ", the flow is still valid, selecting a new path");
                    PathSelectionMetric savedPathSelectionMetric = flowPathSelectionMetrics.get(flowId);
                    if (savedPathSelectionMetric != null) {
                        if (savedPathSelectionMetric == PathSelectionMetric.BREADTH_FIRST)
                            pathSelector = new BreadthFirstFlowPathSelector(topologyGraph);
                        else if (savedPathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS)
                            pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraph);
                        else if (savedPathSelectionMetric == PathSelectionMetric.MINIMUM_NETWORK_LOAD)
                            pathSelector = new MinimumNetworkLoadFlowPathSelector(topologyGraph);
                    }
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

            List<PathDescriptor> newPaths = new ArrayList<>();
            newPaths.add(newPath);
            ControllerMessageResponse responseMessage = new ControllerMessageResponse(MessageType.PATH_RESPONSE, ControllerMessage.UNUSED_FIELD, newPaths);
            try {
                /*
                 * Send the response message using the inverted source path
                 */
                E2EComm.sendUnicast(clientDest, clientNodeId, requestMessage.getClientPort(), PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("ControllerService: path request for flow " + flowId + " from client " + clientNodeId + ", response message successfully sent");
        }

        private void handleFixPathRequest(ControllerMessageRequest requestMessage, int clientNodeId, String[] clientDest) {
            /*
             * Choose the best path for the existing flow, update its info in the active flow paths and send it
             * to the middle node node and push it to the original source node.
             */
            int sourceNodeId = requestMessage.getSourceNodeId();
            int destNodeId = requestMessage.getDestNodeIds()[0];
            int flowId = requestMessage.getFlowId();
            System.out.println("ControllerService: fix path request from client " + clientNodeId + " for the path from the node " + sourceNodeId + " to node " + destNodeId + " for flow " + flowId);

            /*
             * Retrieve the current not working path
             */
            PathDescriptor currentPathDescriptor = flowPaths.get(flowId);

            /*
             * Remove the edge that represents the broken link from the
             * topology graph. Don't remove the node because it should be
             * possible that node could be reached trough another path.
             */
            List<Integer> currentPathNodeIds = currentPathDescriptor.getPathNodeIds();

            int clientNodeIdIndex = -1;
            int nextNodeIdIndex = 0;
            for(int i=0; i<currentPathNodeIds.size(); i++) {
                if(currentPathNodeIds.get(i) == clientNodeId) {
                    clientNodeIdIndex = i;
                    break;
                }
            }

            if(clientNodeIdIndex >= 0 ) {
                nextNodeIdIndex = clientNodeIdIndex + 1;
            }

            int nextHopNodeId = currentPathDescriptor.getPathNodeIds().get(nextNodeIdIndex);

            /*
             * Take a snapshot of the topology graph in order
             * to avoid any concurrency issues given by the
             * Update Manager. In this way we let the UpdateManager
             * to update by itself.
             */
            Graph topologyGraphSnapshot = Graphs.clone(topologyGraph);
            MultiNode clientNode = topologyGraphSnapshot.getNode(Integer.toString(clientNodeId));
            MultiNode nextHopNode = topologyGraphSnapshot.getNode(Integer.toString(nextHopNodeId));
            /*
             * TODO the edge removal should be done better because there may be
             * TODO more than one edges (i.e. networks) connecting the two nodes.
             */
            topologyGraphSnapshot.removeEdge(clientNode,nextHopNode);

            /*
             * This is the fixed path for the node
             * that discovered the error
             */
            PathDescriptor newFixedPath = null;
            /*
             * This is the new path that the
             * original source node must use
             * from now on.
             */
            PathDescriptor newPath = null;

            /*
             * Take a snapshot of the current graph in
             * order to avoid any modification taken by
             * the Update Manager.
             */
            TopologyGraphSelector pathSelector = defaultPathSelector;

            /*
             * When this method is called we assume that a flowId for
             * the current communication already exists.
             */
            if (flowPaths.containsKey(flowId)) {
                PathSelectionMetric pathSelectionMetric = flowPathSelectionMetrics.get(flowId);
                if (pathSelectionMetric != null) {
                    if (pathSelectionMetric == PathSelectionMetric.BREADTH_FIRST)
                        pathSelector = new BreadthFirstFlowPathSelector(topologyGraphSnapshot);
                    else if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS)
                        pathSelector = new FewestIntersectionsFlowPathSelector(topologyGraphSnapshot);
                    else if (pathSelectionMetric == PathSelectionMetric.MINIMUM_NETWORK_LOAD)
                        pathSelector = new MinimumNetworkLoadFlowPathSelector(topologyGraphSnapshot);
                }

                System.out.println("ControllerService: fix path request for flow " + flowId + ", selecting a path");

                newFixedPath = pathSelector.selectPath(clientNodeId, destNodeId, null, flowPaths);
                newPath = pathSelector.selectPath(sourceNodeId, destNodeId, null, flowPaths);

                if (newPath != null) {
                    for (int i = 0; i < newPath.getPath().length; i++) {
                        System.out.println("ControllerService: new fixed flow path address " + i + ", " + newPath.getPath()[i]);
                    }
                    /*
                     * We just update the flowPath data structure, all the other info
                     * like creation time, duration and path selection metric remain
                     * the same.
                     */
                    flowPaths.put(flowId, newPath);
                }
            }

            List<PathDescriptor> newPaths = new ArrayList<>();
            newPaths.add(newFixedPath);
            ControllerMessageResponse responseMessage = new ControllerMessageResponse(MessageType.FIX_PATH_RESPONSE, ControllerMessage.UNUSED_FIELD, newPaths);
            try {
                /*
                 * Send the response message using the inverted source path
                 */
                E2EComm.sendUnicast(clientDest, clientNodeId, requestMessage.getClientPort(), PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("ControllerService: fix path request for flow " + flowId + " from client " + clientNodeId + " for the source node " + sourceNodeId + ", response message successfully sent");

            /*
             * If a new path for the existing flow id is found we push it to
             * the original source so it can update it.
             */
            if(newPath != null) {
                newPaths = new ArrayList<>();
                newPaths.add(newPath);
                String[] sourceNodeDest;
                if(sourceNodeId == clientNodeId) {
                    sourceNodeDest = clientDest;
                } else {
                    /*
                     * TODO The Resolver behaviour in retrieving the right
                     * TODO for the original sender seems not working properly.
                     * TODO In particular when the clientNode is different from
                     * TODO the sourceNode the result is not correct.
                     */
                    sourceNodeDest = Resolver.getInstance(false).resolveBlocking(sourceNodeId, 5 * 1000).get(0).getPath();
                }

                MultiNode sourceNode = topologyGraphSnapshot.getNode(Integer.toString(sourceNodeId));
                int sourceNodePort = sourceNode.getAttribute("port");

                responseMessage = new ControllerMessageResponse(MessageType.FIX_PATH_PUSH_RESPONSE, flowId, newPaths);

                try {
                    /*
                     * Send the response message using the inverted source path
                     */
                    E2EComm.sendUnicast(sourceNodeDest, sourceNodeId, sourceNodePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("ControllerService: fix path request for flow " + flowId + " from client " + clientNodeId + " for the source node " + sourceNodeId + ", response message successfully pushed to the original sender");
        }

        private void handleTopologyGraphRequest(ControllerMessageRequest requestMessage, int clientNodeId, String[] clientDest) {
            int clientPort = requestMessage.getClientPort();

            File f = GraphUtils.saveTopologyGraphIntoDGSFile(topologyGraph, sdnControllerDirectory + "/" + "topologyGraph.dgs");
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            try {
                E2EComm.sendUnicast(
                        clientDest,
                        clientPort,
                        PROTOCOL,
                        fis
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("ControllerService: topology graph request from client " + clientNodeId + " successfully sent");
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
            HashMap<String, MultiNode> removedNodes = new HashMap<>();
            /*
             * Data structure to hold mappings between IDs of nodes removed during the
             * initial refresh and the source node addresses added on the graph by
             * them (targetNodeId, sourceNodeAddress)
             */
            HashMap<String, List<String>> removedNodesAddresses = new HashMap<>();
            /*
             * If the source node doesn't exist in the topology graph, create it
             */
            MultiNode sourceGraphNode = topologyGraph.getNode(Integer.toString(clientNodeId));
            if (sourceGraphNode == null) {
                sourceGraphNode = topologyGraph.addNode(Integer.toString(clientNodeId));
                sourceGraphNode.addAttribute("port", updateMessage.getClientPort());
                sourceGraphNode.addAttribute("ui.style", "fill-color: rgb(35,118,185);");
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
                        List<String> sourceNodeAddresses = new ArrayList<>();
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
                        else {
                            neighborGraphNode.addAttribute("ui.label", neighborGraphNode.getId());
                            neighborGraphNode.addAttribute("ui.style", "fill-color: rgb(35,118,185);");
                        }
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

            ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.FLOW_PRIORITIES_UPDATE, null, null, null, null, null, flowPriorities, null, null, null);
            /*
             * Send the new priority value to the client
             */
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, requestMessage.getClientPort(), PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
            /*
             * Send the new priority value to every other active node
             */
            for (Node node : topologyGraph.getNodeSet()) {
                int nodeId = Integer.parseInt(node.getId());
                if (nodeId != clientNodeId) {
                    String[] dest = Resolver.getInstance(false).resolveBlocking(nodeId).get(0).getPath();
                    try {
                        E2EComm.sendUnicast(dest, nodeId, node.getAttribute("port"), PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
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
            } else {
                pathSelectionMetric = defaultPathSelectionMetric;
            }
            /*
             * Si inizializza la struttura che sarà impiegata alla fine per inviare le istruzioni prima
             * ai nodi che fanno parte del percorso e infine al nodo client che ha richiesto il calcolo del percorso.
             */
            Map<Integer, List<PathDescriptor>> nodesNextHops = new HashMap<>();
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
                    clientNextHops = new ArrayList<>();
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
                        currentNodeNextHops = new ArrayList<>();
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
                        E2EComm.sendUnicast(dest, nodeId, topologyGraph.getNode(Integer.toString(nodeId)).getAttribute("port"), PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
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
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handleOsRoutingRequest(ControllerMessageRequest requestMessage, int clientNodeId, String[] clientDest) {
            int clientPort = requestMessage.getClientPort();
            int destNodeId = requestMessage.getDestNodeIds()[0];
            BoundReceiveSocket ackSocket = null;

            ApplicationRequirements applicationRequirements = requestMessage.getApplicationRequirements();
            OsRoutingTopologyGraphSelector pathSelector = osRoutingPathSelector;
            PathSelectionMetric pathSelectionMetric = requestMessage.getPathSelectionMetric();
            if (pathSelectionMetric != null) {
                if (pathSelectionMetric == PathSelectionMetric.BREADTH_FIRST)
                    pathSelector = new BreadthFirstOsRoutingPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS)
                    pathSelector = new FewestIntersectionsOsRoutingPathSelector(topologyGraph);
                else if (pathSelectionMetric == PathSelectionMetric.MINIMUM_NETWORK_LOAD)
                    pathSelector = new MinimumNetworkLoadOsRoutingPathSelector(topologyGraph);
            }

            System.out.println("ControllerService: first OS routing path request for node ID" + clientNodeId + ", selecting a path");

            /*
             * Generating the routeID
             */
            int routeId = ThreadLocalRandom.current().nextInt();
            while (routeId == GenericPacket.UNUSED_FIELD || forwardOsRoutingPaths.containsKey(routeId)) {
                routeId = ThreadLocalRandom.current().nextInt();
            }

            boolean aborted = false;

            /*
             * TODO Improve the pathSelector
             * At the moment the TopologyGraphSelector interface is designed with flowId in mind.
             * For this reason has been created a new interface called OsRoutingTopologyGraphSelector.
             * The algorithms implementing this interface need to be improved, especially
             * FewestIntersectionsOsRoutingPathSelector and MinimumNetworkLoadOsRoutingPathSelector that
             * don't work well with multiple edges connecting two nodes. These issues are inherited from
             * the original algorithms FewestIntersectionsFlowPathSelector and MinimumNetworkLoadFlowPathSelector.
             * So it is necessary to fix them first.
             */
            OsRoutingPathDescriptor oSRoutingForwardPath = pathSelector.selectPath(clientNodeId, destNodeId, applicationRequirements, forwardOsRoutingPaths);

            OsRoutingPathDescriptor oSRoutingBackwardPath = null;
            if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS) {
                OsRoutingPathDescriptor reversePath = pathSelector.reversePath(oSRoutingForwardPath);
                Map<Integer, OsRoutingPathDescriptor> candidateBackwardOsRoutingPaths = new ConcurrentHashMap<>(backwardOsRoutingPaths);
                candidateBackwardOsRoutingPaths.put(routeId, reversePath);
                oSRoutingBackwardPath = pathSelector.selectPath(destNodeId, clientNodeId, applicationRequirements, candidateBackwardOsRoutingPaths);
            } else {
                if (oSRoutingForwardPath != null) {
                    oSRoutingBackwardPath = pathSelector.reversePath(oSRoutingForwardPath);
                }
            }

            int forwardPathHopCount = 0;
            String forwardPathSourceIp = null;
            String forwardPathDestinationIp = null;

            int backwardPathHopCount = 0;
            String backwardPathSourceIp = null;
            String backwardPathDestinationIp = null;

            /*
             * Sometimes happens that no path is found because the Controller has not
             * received yet the info about the ControllerClient that has just joined the network.
             */
            if (oSRoutingForwardPath == null || oSRoutingBackwardPath == null) {
                aborted = true;
            } else {
                forwardPathSourceIp = oSRoutingForwardPath.getSourceIp();
                forwardPathDestinationIp = oSRoutingForwardPath.getDestinationIP();
                forwardPathHopCount = oSRoutingForwardPath.getPath().length;

                backwardPathSourceIp = oSRoutingBackwardPath.getSourceIp();
                backwardPathDestinationIp = oSRoutingBackwardPath.getDestinationIP();
                backwardPathHopCount = oSRoutingBackwardPath.getPath().length;
            }

            ControllerMessageUpdate updateMessage;
            ControllerMessageResponse responseMessage;

            try {
                ackSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Index to keep track of the intermediate nodes to contact
             * in case of OS_ROUTING_ABORT for the forward path.
             */
            int forwardPathIntermediateNodesToNotifyAbort = 0;
            int backwardPathIntermediateNodesToNotifyAbort = 0;
            GenericPacket gp = null;
            String[] sourceDest = null;
            int sourcePort = -1;
            String[] destDest = null;
            int destPort = -1;

            /*
             * The first part of the protocol is a negotiation between source and the destination
             * to find the interfaces to use during the communication
             */

            /*
             * Send an OS_ROUTING_ADD_ROUTE message to the client node (the sender) in order to update
             * the routing tables and to discover which source IP the source will use
             * in case of multiple interfaces for the forward path. For this reason the srcIP value in
             * OS_ROUTING_ADD_ROUTE is null.
             */
            if (!aborted) {
                MultiNode sourceNode = topologyGraph.getNode(Integer.toString(clientNodeId));
                sourceDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
                sourcePort = sourceNode.getAttribute("port");

                updateMessage = new ControllerMessageUpdate(MessageType.OS_ROUTING_ADD_ROUTE, ackSocket.getLocalPort(), forwardPathSourceIp, forwardPathDestinationIp, oSRoutingForwardPath.getPath()[0], routeId);
                try {
                    E2EComm.sendUnicast(sourceDest, clientNodeId, sourcePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                } catch (Exception e) {
                    aborted = true;
                    e.printStackTrace();
                }

                /*
                 * Get Ack from the client node in order to inform the ControllerService that
                 * "ip route add" command has been successfully applied.
                 */
                try {
                    gp = E2EComm.receive(ackSocket);
                } catch (Exception e) {
                    aborted = true;
                    e.printStackTrace();
                }

                if (gp instanceof UnicastPacket) {
                    UnicastPacket up = (UnicastPacket) gp;
                    Object payload = null;
                    try {
                        payload = E2EComm.deserialize(up.getBytePayload());
                    } catch (Exception e) {
                        aborted = true;
                        e.printStackTrace();
                    }

                    if (payload instanceof ControllerMessageAck) {
                        ControllerMessageAck ackMessage = (ControllerMessageAck) payload;
                        if (ackMessage.getRouteId() == routeId) {
                            switch (ackMessage.getMessageType()) {
                                case OS_ROUTING_ACK:
                                    System.out.println("ControllerService: OS_ROUTING_ACK received");
                                    break;
                                case OS_ROUTING_ABORT:
                                    /*
                                     * The client node was not able to add the route for the specified destination.
                                     * So we don't need to send a OS_ROUTING_DELETE_ROUTE
                                     * message since the route does not exist. The path computation is not aborted yet
                                     * because there may be another IP available for the destination.
                                     */
                                    aborted = true;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
            }

            if (!aborted) {
                /*
                 * Send an OS_ROUTING_ADD_ROUTE message to the destination node (the sender of the backward path) in order to update
                 * the routing tables and to discover which source IP the destination will use
                 * in case of multiple interfaces for the backward path. For this reason the srcIP value in
                 * OS_ROUTING_ADD_ROUTE is null.
                 */
                MultiNode destinationNode = topologyGraph.getNode(Integer.toString(destNodeId));
                destDest = Resolver.getInstance(false).resolveBlocking(destNodeId, 5 * 1000).get(0).getPath();
                destPort = destinationNode.getAttribute("port");

                updateMessage = new ControllerMessageUpdate(MessageType.OS_ROUTING_ADD_ROUTE, ackSocket.getLocalPort(), backwardPathSourceIp, backwardPathDestinationIp, oSRoutingBackwardPath.getPath()[0], routeId);
                try {
                    E2EComm.sendUnicast(destDest, destNodeId, destPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                } catch (Exception e) {
                    aborted = true;
                    e.printStackTrace();
                }

                /*
                 * Get Ack from the destination node in order to inform the ControllerService that
                 * "ip route add" command has been successfully applied.
                 */
                try {
                    gp = E2EComm.receive(ackSocket);
                } catch (Exception e) {
                    aborted = true;
                    e.printStackTrace();
                }

                if (gp instanceof UnicastPacket) {
                    UnicastPacket up = (UnicastPacket) gp;
                    Object payload = null;
                    try {
                        payload = E2EComm.deserialize(up.getBytePayload());
                    } catch (Exception e) {
                        aborted = true;
                        e.printStackTrace();
                    }

                    if (payload instanceof ControllerMessageAck) {
                        ControllerMessageAck ackMessage = (ControllerMessageAck) payload;
                        if (ackMessage.getRouteId() == routeId) {
                            switch (ackMessage.getMessageType()) {
                                case OS_ROUTING_ACK:
                                    System.out.println("ControllerService: OS_ROUTING_ACK received");
                                    break;
                                case OS_ROUTING_ABORT:
                                    /*
                                     * The destination node was not able to add the route.
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
            }

            /*
             * Now that both the source and the destination have found an interface available to communicate,
             * we need to enable it by configuring the routing tables of all intermediates nodes both for
             * the forward path and the backward path
             */
            if (!aborted) {
                /*
                 * Send an OS_ROUTING_ADD_ROUTE message to all intermediate nodes of the forward path in order to update
                 * the routing tables.
                 */
                for (int i = 1; i <= forwardPathHopCount && !aborted; i++) {
                    int intermediateNodeId = oSRoutingForwardPath.getPathNodeIds().get(i);
                    /*
                     * Send an OS_ROUTING_ADD_ROUTE to all intermediate nodes, except the destination.
                     */
                    if (intermediateNodeId != destNodeId) {
                        MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                        String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                        int intermediatePort = intermediateNode.getAttribute("port");

                        updateMessage = new ControllerMessageUpdate(MessageType.OS_ROUTING_ADD_ROUTE, ackSocket.getLocalPort(), forwardPathSourceIp, forwardPathDestinationIp, oSRoutingForwardPath.getPath()[i], routeId);
                        try {
                            E2EComm.sendUnicast(intermediateDest, intermediateNodeId, intermediatePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                        } catch (Exception e) {
                            aborted = true;
                            e.printStackTrace();
                        }

                        /*
                         * Get Ack from the intermediate node in order to inform the ControllerService that
                         * "ip route add" command has been successfully applied.
                         */
                        try {
                            gp = E2EComm.receive(ackSocket);
                        } catch (Exception e) {
                            aborted = true;
                            e.printStackTrace();
                        }

                        if (gp instanceof UnicastPacket) {
                            UnicastPacket up = (UnicastPacket) gp;
                            Object payload = null;
                            try {
                                payload = E2EComm.deserialize(up.getBytePayload());
                            } catch (Exception e) {
                                aborted = true;
                                e.printStackTrace();
                            }

                            if (payload instanceof ControllerMessageAck) {
                                ControllerMessageAck ackMessage = (ControllerMessageAck) payload;
                                if (ackMessage.getRouteId() == routeId) {
                                    switch (ackMessage.getMessageType()) {
                                        case OS_ROUTING_ACK:
                                            forwardPathIntermediateNodesToNotifyAbort++;
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

            if (!aborted) {
                /*
                 * Send an OS_ROUTING_ADD_ROUTE message to all intermediate nodes of the backward path in order to update
                 * the routing tables.
                 */
                for (int j = 1; j <= backwardPathHopCount && !aborted; j++) {
                    int intermediateNodeId = oSRoutingBackwardPath.getPathNodeIds().get(j);
                    /*
                     * Send an OS_ROUTING_ADD_ROUTE to all intermediate nodes, except the destination.
                     */
                    if (intermediateNodeId != clientNodeId) {
                        MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                        String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                        int intermediatePort = intermediateNode.getAttribute("port");

                        updateMessage = new ControllerMessageUpdate(MessageType.OS_ROUTING_ADD_ROUTE, ackSocket.getLocalPort(), backwardPathSourceIp, backwardPathDestinationIp, oSRoutingBackwardPath.getPath()[j], routeId);
                        try {
                            E2EComm.sendUnicast(intermediateDest, intermediateNodeId, intermediatePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                        } catch (Exception e) {
                            aborted = true;
                            e.printStackTrace();
                        }
                        /*
                         * Get Ack from the intermediate node in order to inform the ControllerService that
                         * "ip route add" command has been successfully applied.
                         */
                        try {
                            gp = E2EComm.receive(ackSocket);
                        } catch (Exception e) {
                            aborted = true;
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
                                            backwardPathIntermediateNodesToNotifyAbort++;
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
             * Close the ControllerService ackSocket since it is no longer needed.
             */
            if (ackSocket != null) {
                try {
                    ackSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


            if (aborted) {
                updateMessage = new ControllerMessageUpdate(MessageType.OS_ROUTING_DELETE_ROUTE, ControllerMessage.UNUSED_FIELD, null, null, null, routeId);

                /*
                 * If some of the intermediate nodes could not configure the forward route
                 * we need to delete the route for that intermediate nodes
                 * that added this route correctly.
                 */
                if (forwardPathIntermediateNodesToNotifyAbort > 0) {
                    for (int k = 1; k <= forwardPathIntermediateNodesToNotifyAbort; k++) {
                        int intermediateNodeId = oSRoutingForwardPath.getPathNodeIds().get(k);

                        if ((intermediateNodeId != destNodeId)) {
                            MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                            String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                            int intermediatePort = intermediateNode.getAttribute("port");

                            try {
                                E2EComm.sendUnicast(intermediateDest, intermediateNodeId, intermediatePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (forwardPathSourceIp != null) {
                    try {
                        E2EComm.sendUnicast(sourceDest, clientNodeId, sourcePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                /*
                 * If some of the intermediate nodes could not configure the backward route
                 * we need to delete the route for that intermediate nodes
                 * that added this route correctly.
                 */
                if (backwardPathIntermediateNodesToNotifyAbort > 0) {
                    for (int l = 1; l <= backwardPathIntermediateNodesToNotifyAbort; l++) {
                        int intermediateNodeId = oSRoutingBackwardPath.getPathNodeIds().get(l);

                        if ((intermediateNodeId != clientNodeId)) {
                            MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                            String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                            int intermediatePort = intermediateNode.getAttribute("port");

                            try {
                                E2EComm.sendUnicast(intermediateDest, intermediateNodeId, intermediatePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (backwardPathSourceIp != null) {
                    try {
                        E2EComm.sendUnicast(destDest, destNodeId, destPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                routeId = -1;
            } else {
                long currentTime = System.currentTimeMillis();

                oSRoutingForwardPath.setCreationTime(currentTime);
                oSRoutingBackwardPath.setCreationTime(currentTime);

                forwardOsRoutingPaths.put(routeId, oSRoutingForwardPath);
                backwardOsRoutingPaths.put(routeId, oSRoutingBackwardPath);
                osRoutesStartTimes.put(routeId, currentTime);
                osRoutesApplicationRequirements.put(routeId, applicationRequirements);

                System.out.println("FORWARD PATH:");
                for (String p : oSRoutingForwardPath.getPath()) {
                    System.out.println(p);
                }
                System.out.println("BACKWARD PATH:");
                for (String p : oSRoutingBackwardPath.getPath()) {
                    System.out.println(p);
                }
                /*
                 * Send an OS_ROUTING_PUSH_RESPONSE message to the destination selected by the source without waiting for any ack since
                 * all routes have been added.
                 */
                responseMessage = new ControllerMessageResponse(MessageType.OS_ROUTING_PUSH_RESPONSE, ControllerMessage.UNUSED_FIELD, routeId, oSRoutingBackwardPath, applicationRequirements.getDuration());
                Node receiverNode = topologyGraph.getNode(Integer.toString(destNodeId));
                int receiverPort = receiverNode.getAttribute("port");
                try {
                    E2EComm.sendUnicast(destDest, destNodeId, receiverPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                /*
                 * Send an OS_ROUTING_PUSH_RESPONSE message to all the intermediate nodes of the forward path so that
                 * they can keep track of the existing route when it will expire and locally deleted.
                 * all routes have been added.
                 */
                responseMessage = new ControllerMessageResponse(MessageType.OS_ROUTING_PUSH_RESPONSE, ControllerMessage.UNUSED_FIELD, routeId, null, applicationRequirements.getDuration());
                for (int m = 1; m <= forwardPathHopCount; m++) {
                    int intermediateNodeId = oSRoutingForwardPath.getPathNodeIds().get(m);

                    if ((intermediateNodeId != destNodeId)) {
                        MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                        String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                        int intermediatePort = intermediateNode.getAttribute("port");

                        try {
                            E2EComm.sendUnicast(intermediateDest, intermediateNodeId, intermediatePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                /*
                 * To optimize the overhead due to this confirmation messages we avoid to send
                 * twice the same message in case of BreadthFirst and Minimum Network Load
                 * because in these cases the forward path and the backward one contain the same
                 * intermediate nodes. In case of fewest intersections the intermediate nodes of the
                 * forward path and the backward path are in most common cases different.
                 */
                if (pathSelectionMetric == PathSelectionMetric.FEWEST_INTERSECTIONS) {
                    for (int n = 1; n <= backwardPathHopCount; n++) {
                        int intermediateNodeId = oSRoutingBackwardPath.getPathNodeIds().get(n);

                        if ((intermediateNodeId != clientNodeId)) {
                            MultiNode intermediateNode = topologyGraph.getNode(Integer.toString(intermediateNodeId));
                            String[] intermediateDest = Resolver.getInstance(false).resolveBlocking(intermediateNodeId, 5 * 1000).get(0).getPath();
                            int intermediatePort = intermediateNode.getAttribute("port");

                            try {
                                E2EComm.sendUnicast(intermediateDest, intermediateNodeId, intermediatePort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            /*
             * Send an OS_ROUTING_PULL_RESPONSE message to the source without waiting for any ack since
             * all routes have been added or removed. If the route has been created correctly we return
             * back the routeId otherwise we return to the client node routeId = -1.
             */
            responseMessage = new ControllerMessageResponse(MessageType.OS_ROUTING_PULL_RESPONSE, ControllerMessage.UNUSED_FIELD, routeId, oSRoutingForwardPath, ControllerMessage.UNUSED_FIELD);
            try {
                E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(responseMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class UpdateManager extends Thread {
        /**
         * Interval between each update
         */
        private static final int TIME_INTERVAL = 10 * 1000;

        /**
         * Boolean value that reports if the UpdateManager is currently active.
         */
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
                    flowPathSelectionMetrics.remove(flowId);
                    flowPaths.remove(flowId);
                }
            }
        }

        private void updateOsRoutes() {
            for (Integer routeId : osRoutesStartTimes.keySet()) {
                long osRouteStartTime = osRoutesStartTimes.get(routeId);
                int duration = osRoutesApplicationRequirements.get(routeId).getDuration();
                long elapsed = System.currentTimeMillis() - osRouteStartTime;
                if (elapsed > (duration + (duration / 4)) * 1000) {
                    osRoutesStartTimes.remove(routeId);
                    osRoutesApplicationRequirements.remove(routeId);
                    forwardOsRoutingPaths.remove(routeId);
                    backwardOsRoutingPaths.remove(routeId);
                }
            }
        }

        private void sendDefaultFlowPathsUpdate() {
            for (Node clientNode : topologyGraph.getNodeSet()) {
                int clientNodeId = Integer.parseInt(clientNode.getId());
                Map<Integer, PathDescriptor> defaultFlowPathMappings = defaultPathSelector.getAllPathsFromSource(clientNodeId);
                ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.DEFAULT_FLOW_PATHS_UPDATE, null, null, null, null, defaultFlowPathMappings, null, null, null, null);
                String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
                int clientPort = clientNode.getAttribute("port");
                try {
                    E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void sendFlowPrioritiesUpdate() {
            for (Node clientNode : topologyGraph.getNodeSet()) {
                int clientNodeId = Integer.parseInt(clientNode.getId());
                flowPriorities = flowPrioritySelector.getFlowPriorities(flowPriorities, flowApplicationRequirements);
                ControllerMessageUpdate updateMessage = new ControllerMessageUpdate(MessageType.FLOW_PRIORITIES_UPDATE, ControllerMessage.UNUSED_FIELD, null, null, null, null, null, flowPriorities, null, null, null);
                String[] clientDest = Resolver.getInstance(false).resolveBlocking(clientNodeId, 5 * 1000).get(0).getPath();
                int clientPort = clientNode.getAttribute("port");
                try {
                    E2EComm.sendUnicast(clientDest, clientNodeId, clientPort, PROTOCOL, CONTROL_FLOW_ID, E2EComm.serialize(updateMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void update() {
            updateTopology();
            updateFlows();
            updateOsRoutes();
            if (routingPolicy == RoutingPolicy.REROUTING)
                sendDefaultFlowPathsUpdate();
            if (trafficEngineeringPolicy == TrafficEngineeringPolicy.SINGLE_FLOW || trafficEngineeringPolicy == TrafficEngineeringPolicy.QUEUES || trafficEngineeringPolicy == TrafficEngineeringPolicy.TRAFFIC_SHAPING)
                sendFlowPrioritiesUpdate();
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
                update();
            }
            System.out.println("ControllerService UpdateManager FINISHED");
        }
    }
}
