package it.unibo.deis.lia.ramp.core.internode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.NetworkInterfaceStats;
import it.unibo.deis.lia.ramp.util.NodeStats;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class ControllerClient extends Thread {
	
	// Default flow ID value, to be used by default type applications
	private static final int DEFAULT_FLOW_ID = 0;
	
	// Paths time-to-live value
	private static final int FLOW_PATHS_TTL = 60*1000;
	
	private static ControllerClient controllerClient = null;
	private BoundReceiveSocket clientSocket = null;
	private boolean active;
	private UpdateManager updateManager;
	private FlowPolicy flowPolicy;
	private DataPlaneForwarder dataPlaneForwarder;
	
	// Data structure to hold paths imposed by the controller to the default flow for the different destination nodes (destNodeId, path)
	private Map<Integer, PathDescriptor> defaultFlowPaths;
	// Data structure to hold complete paths imposed by the controller for the existing flows (flowId, path)
	private Map<Integer, PathDescriptor> flowPaths;
	// Data structure to hold start times of the existing flows (flowId, startTime)
	private Map<Integer, Long> flowStartTimes;
	// Data structure to hold durations of the existing flows (flowId, duration)
	private Map<Integer, Integer> flowDurations;
	
	// Data structure to hold priorities for the existing flows (flowId, priority)
	private Map<Integer, Integer> flowPriorities;
	
	// Data structure to hold next hops for multicast forwarding associated to the existing flows (flowId, nextHops)
	private Map<Integer, List<PathDescriptor>> flowMulticastNextHops;
	
	private ControllerClient() {
		// The node must already know some neighbors for the service discovery to work
		Vector<ServiceResponse> serviceResponses = findControllerService(5, 5*1000, 1);
		ServiceResponse serviceResponse = null;
		if (serviceResponses.size() > 0) {
			serviceResponse = serviceResponses.get(0);
			try {
				// Use the protocol specified by the controller
				this.clientSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
		else
			System.out.println("ControllerClient: controller service not found, cannot bind client socket");
		
		this.active = true;
		this.updateManager = new UpdateManager();
		this.flowPolicy = FlowPolicy.REROUTING;
		this.dataPlaneForwarder = BestPathForwarder.getInstance();
		
		this.defaultFlowPaths = new ConcurrentHashMap<Integer, PathDescriptor>();
		this.flowPaths = new ConcurrentHashMap<Integer, PathDescriptor>();
		this.flowStartTimes = new ConcurrentHashMap<Integer, Long>();
		this.flowDurations = new ConcurrentHashMap<Integer, Integer>();
		
		this.flowPriorities = new ConcurrentHashMap<Integer, Integer>();
		
		this.flowMulticastNextHops = new ConcurrentHashMap<Integer, List<PathDescriptor>>();
	}
	
	private Vector<ServiceResponse> findControllerService(int ttl, int timeout, int servicesAmount) {
		Vector<ServiceResponse> serviceResponses = null;
		try {
			serviceResponses = ServiceDiscovery.findServices(ttl, "SDNController", timeout, servicesAmount, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return serviceResponses;
	}
	
	public synchronized static ControllerClient getInstance() {
		if (controllerClient == null) {
			try {
				controllerClient = new ControllerClient();
			} catch (Exception e) {
				e.printStackTrace();
			}
			controllerClient.start();
		}
		return controllerClient;
	}
	
	public void stopClient() {
		System.out.println("ControllerClient STOP");
		leaveService();
		this.active = false;
		try {
			this.clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.updateManager.stopUpdateManager();
	}
	
	public int getFlowId(ApplicationRequirements applicationRequirements, int destNodeId) {
		int flowId;
		if (applicationRequirements.getApplicationType() == ApplicationRequirements.ApplicationType.DEFAULT)
			flowId = DEFAULT_FLOW_ID;
		else {
			flowId = ThreadLocalRandom.current().nextInt();
			while (flowId == GenericPacket.UNUSED_FIELD || flowId == DEFAULT_FLOW_ID || this.flowStartTimes.containsKey(flowId))
				flowId = ThreadLocalRandom.current().nextInt();
			if (this.flowPolicy == FlowPolicy.REROUTING)
				sendNewPathRequest(new int[] {destNodeId}, applicationRequirements, null, flowId);
			else if (this.flowPolicy == FlowPolicy.SINGLE_FLOW || this.flowPolicy == FlowPolicy.QUEUES || this.flowPolicy == FlowPolicy.TRAFFIC_SHAPING)
				sendNewPriorityValueRequest(applicationRequirements, flowId);
		}
		return flowId;
	}
	
	public int getFlowId(ApplicationRequirements applicationRequirements, int[] destNodeIds, int[] destPorts) {
		int flowId;
		if (applicationRequirements.getApplicationType() == ApplicationRequirements.ApplicationType.DEFAULT)
			flowId = DEFAULT_FLOW_ID;
		else {
			flowId = ThreadLocalRandom.current().nextInt();
			while (flowId == GenericPacket.UNUSED_FIELD || flowId == DEFAULT_FLOW_ID || this.flowStartTimes.containsKey(flowId))
				flowId = ThreadLocalRandom.current().nextInt();
			if (this.flowPolicy == FlowPolicy.REROUTING)
				sendNewPathRequest(destNodeIds, applicationRequirements, null, flowId);
			else if (this.flowPolicy == FlowPolicy.SINGLE_FLOW || this.flowPolicy == FlowPolicy.QUEUES || this.flowPolicy == FlowPolicy.TRAFFIC_SHAPING)
				sendNewPriorityValueRequest(applicationRequirements, flowId);
			else if (this.flowPolicy == FlowPolicy.MULTICASTING)
				sendNewMulticastRequest(destNodeIds, destPorts, applicationRequirements, null, flowId);
		}
		return flowId;
	}
	
	public int getFlowId(ApplicationRequirements applicationRequirements, int[] destNodeIds, int[] destPorts, TopologyGraphSelector.PathSelectionMetric pathSelectionMetric) {
		int flowId;
		if (applicationRequirements.getApplicationType() == ApplicationRequirements.ApplicationType.DEFAULT)
			flowId = DEFAULT_FLOW_ID;
		else {
			flowId = ThreadLocalRandom.current().nextInt();
			while (flowId == GenericPacket.UNUSED_FIELD || flowId == DEFAULT_FLOW_ID || this.flowStartTimes.containsKey(flowId))
				flowId = ThreadLocalRandom.current().nextInt();
			if (this.flowPolicy == FlowPolicy.REROUTING)
				sendNewPathRequest(destNodeIds, applicationRequirements, pathSelectionMetric, flowId);
			else if (this.flowPolicy == FlowPolicy.SINGLE_FLOW || this.flowPolicy == FlowPolicy.QUEUES || this.flowPolicy == FlowPolicy.TRAFFIC_SHAPING)
				sendNewPriorityValueRequest(applicationRequirements, flowId);
			else if (this.flowPolicy == FlowPolicy.MULTICASTING)
				sendNewMulticastRequest(destNodeIds, destPorts, applicationRequirements, pathSelectionMetric, flowId);
		}
		return flowId;
	}
	
	public String[] getFlowPath(int destNodeId, int flowId) {
		System.out.println("ControllerClient: received request for new path for flow " + flowId);
		String[] flowPath = null;
		PathDescriptor flowPathDescriptor = null;
		
		if (flowId == DEFAULT_FLOW_ID)
			flowPathDescriptor = this.defaultFlowPaths.get(destNodeId);
		else
			flowPathDescriptor = this.flowPaths.get(flowId);
		
		// If a path exists in the maps for the specified flowId, check TTL and, if valid, return it
		if (flowPathDescriptor != null) {
			long elapsed = System.currentTimeMillis() - flowPathDescriptor.getCreationTime();
			// If the path is valid, return it
			if (elapsed < FLOW_PATHS_TTL) {
				flowPath = flowPathDescriptor.getPath();
				if (flowId != DEFAULT_FLOW_ID)
					System.out.println("ControllerClient: entry found for flow " + flowId + ", returning the new flow path");
				else
					System.out.println("ControllerClient: entry found for default flow, returning the new flow path");
			}
			// If the path is not valid and the flowId is not the default one, send a request for a new one to the controller
			else {
				if (flowId != DEFAULT_FLOW_ID) {
					System.out.println("ControllerClient: entry found for flow " + flowId + ", but its validity has expired, sending request to the controller");
					// The path request is not the first one for this application, so applicationRequirements is null
					flowPath = sendNewPathRequest(new int[] {destNodeId}, null, null, flowId);
				}
				else
					System.out.println("ControllerClient: entry found for default flow, but its validity has expired, returning null");
			}
		}
		// If a path doesn't exist in the map for the specified flowId, return null
		else {
			if (flowId != DEFAULT_FLOW_ID)
				System.out.println("ControllerClient: no entry found for flow " + flowId + ", returning null");
			else
				System.out.println("ControllerClient: no entry found for default flow, returning null");
		}
		return flowPath;
	}
	
	private String[] sendNewPathRequest(int[] destNodeIds, ApplicationRequirements applicationRequirements, TopologyGraphSelector.PathSelectionMetric pathSelectionMetric, int flowId) {
		PathDescriptor newPath = null;
		BoundReceiveSocket responseSocket = null;
		// Controller service has to be found before sending any message
		Vector<ServiceResponse> serviceResponses = findControllerService(5, 5*1000, 1);
		ServiceResponse serviceResponse = null;
		if(serviceResponses.size() > 0) {
			serviceResponse = serviceResponses.get(0);
			try {
				// Use the protocol specified by the controller
				responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
				// Send a path request to the controller for a certain flowId, currentDest is also necessary for the search on the controller side (it can be chosen as the path to be used), as well as destNodeId
				ControllerMessage requestMessage = new ControllerMessage(ControllerMessage.MessageType.PATH_REQUEST, responseSocket.getLocalPort(), destNodeIds, new int[0], applicationRequirements, pathSelectionMetric, flowId, null, null, null, null, null, null);
				E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(requestMessage));
				System.out.println("ControllerClient: request for a new path for flow " + flowId + " sent to the controller");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else
			System.out.println("ControllerClient: controller service not found, cannot send path request");
		
		// In alternativa, ricezione della risposta nel metodo run(), 
		// senza attesa, ma senza poter inviare subito il nuovo percorso
		GenericPacket gp = null;
		try {
			gp = E2EComm.receive(responseSocket);
		} catch(Exception e) {
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
			if (payload instanceof ControllerMessage) {
				ControllerMessage responseMessage = (ControllerMessage) payload;
				switch (responseMessage.getMessageType()) {
				case PATH_RESPONSE:
					// Set the received path creation time and add it to the known flow paths
					newPath = responseMessage.getNewPaths().get(0);
					if (newPath != null) {
						newPath.setCreationTime(System.currentTimeMillis());
						this.flowPaths.put(flowId, newPath);
						this.flowStartTimes.put(flowId, System.currentTimeMillis());
						this.flowDurations.put(flowId, applicationRequirements.getDuration());
						System.out.println("ControllerClient: response with a new path for flow " + flowId + " received from the controller");
					}
					else
						System.out.println("ControllerClient: null path received from the controller for flow " + flowId);
					break;
				default:
					break;
				}
			}
		}
		if (newPath != null)
			return newPath.getPath();
		else
			return null;
	}
	
	public int getFlowPriority(int flowId) {
		Integer flowPriority = this.flowPriorities.get(flowId);
		if (flowPriority != null)
			return flowPriority;
		else
			return -1;
	}
	
	private int sendNewPriorityValueRequest(ApplicationRequirements applicationRequirements, int flowId) {
		int newPriorityValue = -1;
		BoundReceiveSocket responseSocket = null;
		// Controller service has to be found before sending any message
		Vector<ServiceResponse> serviceResponses = findControllerService(5, 5*1000, 1);
		ServiceResponse serviceResponse = null;
		if (serviceResponses.size() > 0) {
			serviceResponse = serviceResponses.get(0);
			try {
				// Use the protocol specified by the controller
				responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
				// Send a priority value request to the controller for a certain flowId
				ControllerMessage requestMessage = new ControllerMessage(ControllerMessage.MessageType.PRIORITY_VALUE_REQUEST, responseSocket.getLocalPort(), new int[0], new int[0], applicationRequirements, null, flowId, null, null, null, null, null, null);
				E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(requestMessage));
				System.out.println("ControllerClient: request for a new priority value for flow " + flowId + " sent to the controller");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else
			System.out.println("ControllerClient: controller service not found, cannot send priority value request");
		
		GenericPacket gp = null;
		try {
			gp = E2EComm.receive(responseSocket);
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
			if (payload instanceof ControllerMessage) {
				ControllerMessage responseMessage = (ControllerMessage) payload;
				switch(responseMessage.getMessageType()) {
				case FLOW_PRIORITIES_UPDATE:
					newPriorityValue = responseMessage.getFlowPriorities().get(flowId);
					this.flowPriorities = responseMessage.getFlowPriorities();
					this.flowStartTimes.put(flowId, System.currentTimeMillis());
					this.flowDurations.put(flowId, applicationRequirements.getDuration());
					System.out.println("ControllerClient: response with a new priority value for flow " + flowId + " received and successfully applied");
					break;
				default:
					break;
				}
			}
		}
		return newPriorityValue;
	}
	
	public List<PathDescriptor> getFlowMulticastNextHops(int flowId) {
		return this.flowMulticastNextHops.get(flowId);
	}
	
	public List<PathDescriptor> sendNewMulticastRequest(int[] destNodeIds, int[] destPorts, ApplicationRequirements applicationRequirements, TopologyGraphSelector.PathSelectionMetric pathSelectionMetric, int flowId) {
		List<PathDescriptor> nextHops = new ArrayList<PathDescriptor>();
		BoundReceiveSocket responseSocket = null;
		Vector<ServiceResponse> serviceResponses = findControllerService(5, 5*1000, 1);
		ServiceResponse serviceResponse = null;
		if (serviceResponses.size() > 0) {
			serviceResponse = serviceResponses.get(0);
			try {
				responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
				ControllerMessage requestMessage = new ControllerMessage(ControllerMessage.MessageType.MULTICAST_REQUEST, responseSocket.getLocalPort(), destNodeIds, destPorts, applicationRequirements, pathSelectionMetric, flowId, null, null, null, null, null, null);
				E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(requestMessage));
				System.out.println("ControllerClient: request for a new multicast communication for flow " + flowId + " sent to the controller");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else
			System.out.println("ControllerClient: controller service not found, cannot send multicast request");
		
		GenericPacket gp = null;
		try {
			gp = E2EComm.receive(responseSocket);
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
			if (payload instanceof ControllerMessage) {
				ControllerMessage responseMessage = (ControllerMessage) payload;
				switch (responseMessage.getMessageType()) {
				case MULTICAST_CONTROL:
					nextHops = responseMessage.getNewPaths();
					this.flowMulticastNextHops.put(flowId, responseMessage.getNewPaths());
					this.flowStartTimes.put(flowId, System.currentTimeMillis());
					this.flowDurations.put(flowId, applicationRequirements.getDuration());
					System.out.println("ControllerClient: response with a list of the next hops for multicast communication for flow " + flowId + " received and successfully applied");
					break;
				default:
					break;
				}
			}
		}
		return nextHops;
	}
	
	private void joinService() {
		// Get network stats for the interfaces associated to the addresses of the node
		Map<String, NodeStats> networkInterfaceStats = new HashMap<String, NodeStats>();
		try {
			for (String address : Dispatcher.getLocalNetworkAddresses()) {
				NetworkInterface addressNetworkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName(address));
				NodeStats addressNetworkInterfaceStats = new NetworkInterfaceStats(Dispatcher.getLocalRampId(), addressNetworkInterface);
				networkInterfaceStats.put(address, addressNetworkInterfaceStats);
			}
		} catch (SocketException e1) {
			e1.printStackTrace();
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		ControllerMessage joinMessage = new ControllerMessage(ControllerMessage.MessageType.JOIN_SERVICE, this.clientSocket.getLocalPort(), new int[0], new int[0], null, null, ControllerMessage.UNUSED_FIELD, null, null, networkInterfaceStats, null, null, null);
		// Controller service has to be found before sending any message
		Vector<ServiceResponse> serviceResponses = findControllerService(5, 5*1000, 1);
		ServiceResponse serviceResponse = null;
		if(serviceResponses.size() > 0) {
			serviceResponse = serviceResponses.get(0);
			try {
				E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(joinMessage));
				// LocalDateTime localDateTime = LocalDateTime.now();
				// String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
				// System.out.println("ControllerClient: join request sent at " + timestamp);
				System.out.println("ControllerClient: join request sent to the controller");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else
			System.out.println("ControllerClient: controller service not found, cannot send join request");;
	}
	
	private void leaveService() {
		ControllerMessage leaveMessage = new ControllerMessage(ControllerMessage.MessageType.LEAVE_SERVICE);
		// Controller service has to be found before sending any message
		Vector<ServiceResponse> serviceResponses = findControllerService(5, 5*1000, 1);
		ServiceResponse serviceResponse = null;
		if(serviceResponses.size() > 0) {
			serviceResponse = serviceResponses.get(0);
			try {
				E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(leaveMessage));
				System.out.println("ControllerClient: leave request sent to the controller");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else
			System.out.println("ControllerClient: controller service not found, cannot send leave request");
	}

	@Override
	public void run() {
		System.out.println("ControllerClient START");
		joinService();
		this.updateManager.start();
		while (active == true) {
			try {
				// Receive packets from the controller and pass them to newly created handlers
				GenericPacket gp = E2EComm.receive(this.clientSocket, 5*1000);
				new PacketHandler(gp).start();
			} catch (Exception e) {
				// e.printStackTrace();
			}
		}
		try {
			this.clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		controllerClient = null;
		System.out.println("ControllerClient FINISHED");
	}
	
	private class PacketHandler extends Thread {
		
		private GenericPacket gp;
		
		PacketHandler(GenericPacket gp) {
			this.gp = gp;
		}
		
		@Override
		public void run() {
			if (this.gp instanceof UnicastPacket) {
				UnicastPacket up = (UnicastPacket) this.gp;
				Object payload = null;
				
				try {
					payload = E2EComm.deserialize(up.getBytePayload());
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (payload instanceof ControllerMessage) {
					ControllerMessage controllerMessage = (ControllerMessage) payload;
					long currentTime = System.currentTimeMillis();
					
					switch (controllerMessage.getMessageType()) {
					case FLOW_POLICY_UPDATE:
						flowPolicy = controllerMessage.getFlowPolicy();
						dataPlaneForwarder.deactivate();
						if (controllerMessage.getFlowPolicy() == FlowPolicy.REROUTING)
							dataPlaneForwarder = BestPathForwarder.getInstance();
						else if (controllerMessage.getFlowPolicy() == FlowPolicy.SINGLE_FLOW)
							dataPlaneForwarder = SinglePriorityForwarder.getInstance();
						else if (controllerMessage.getFlowPolicy() == FlowPolicy.QUEUES)
							dataPlaneForwarder = MultipleFlowsSinglePriorityForwarder.getInstance();
						else if (controllerMessage.getFlowPolicy() == FlowPolicy.TRAFFIC_SHAPING)
							dataPlaneForwarder = MultipleFlowsMultiplePrioritiesForwarder.getInstance();
						System.out.println("ControllerClient: flow policy update received from the controller and successfully applied");
						break;
					case DEFAULT_FLOW_PATHS_UPDATE:
						// Set the received default flow paths creation time and add them to the map
						Map<Integer, PathDescriptor> newDefaultFlowPaths = controllerMessage.getNewPathMappings();
						for (PathDescriptor pathDescriptor : newDefaultFlowPaths.values())
							pathDescriptor.setCreationTime(currentTime);
						defaultFlowPaths.putAll(newDefaultFlowPaths);
						System.out.println("ControllerClient: default flow paths update received from the controller and successfully applied");
						break;
					case FLOW_PRIORITIES_UPDATE:
						flowPriorities = controllerMessage.getFlowPriorities();
						System.out.println("ControllerClient: flow priorities update received from the controller and successfully applied");
						break;
					case MULTICAST_CONTROL:
						flowMulticastNextHops.put(controllerMessage.getFlowId(), controllerMessage.getNewPaths());
						System.out.println("ControllerClient: multicast control update received from the controller and succesfully applied");
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
		private static final int TIME_INTERVAL = 20 * 1000;
		
		private Map<String, NodeStats> networkInterfaceStats;
		private boolean active;
		
		UpdateManager() {
			this.networkInterfaceStats = new HashMap<String, NodeStats>();
			try {
				for (String address : Dispatcher.getLocalNetworkAddresses()) {
					NetworkInterface addressNetworkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName(address));
					NodeStats addressNetworkInterfaceStats = new NetworkInterfaceStats(Dispatcher.getLocalRampId(), addressNetworkInterface);
					this.networkInterfaceStats.put(address, addressNetworkInterfaceStats);
				}
			} catch (SocketException e1) {
				e1.printStackTrace();
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			this.active = true;
		}
		
		public void stopUpdateManager() {
			System.out.println("ControllerClient UpdateManager STOP");
			this.active = false;
		}
		
		private void updateFlows() {
			for (Integer flowId : flowStartTimes.keySet()) {
				long flowStartTime = flowStartTimes.get(flowId);
				int duration = flowDurations.get(flowId);
				long elapsed = System.currentTimeMillis() - flowStartTime;
				if (elapsed > (duration+(duration/4))*1000) {
					flowStartTimes.remove(flowId);
					flowDurations.remove(flowId);
					flowPaths.remove(flowId);
				}
			}
		}
		
		private void sendTopologyUpdate() {
			// Obtain neighbor nodes nodeIds and addresses through the Heartbeater
			Map<Integer, List<String>> neighborNodes = new ConcurrentHashMap<Integer, List<String>>();
			Vector<InetAddress> addresses = Heartbeater.getInstance(false).getNeighbors();
			for (InetAddress address : addresses) {
				Integer nodeId = Heartbeater.getInstance(false).getNodeId(address);
				short networkPrefixLength = Heartbeater.getInstance(false).getNetworkPrefixLength(address);
				String completeAddress = "" + address.getHostAddress() + "/" + networkPrefixLength;
				if (neighborNodes.containsKey(nodeId))
					neighborNodes.get(nodeId).add(completeAddress);
				else {
					List<String> neighborAddressList = new ArrayList<String>();
					neighborAddressList.add(completeAddress);
					neighborNodes.put(nodeId, neighborAddressList);
				}
			}
			
			// Update network stats for the interfaces associated to the addresses of the node
			for (String address : this.networkInterfaceStats.keySet())
				this.networkInterfaceStats.get(address).updateStats();
			
			// Send the obtained informations about neighbor nodes to the controller
			ControllerMessage updateMessage = new ControllerMessage(ControllerMessage.MessageType.TOPOLOGY_UPDATE, ControllerMessage.UNUSED_FIELD, new int[0], new int[0], null, null, ControllerMessage.UNUSED_FIELD, null, neighborNodes, this.networkInterfaceStats, null, null, null);
			// Controller service has to be found before sending any message
			Vector<ServiceResponse> serviceResponses = findControllerService(5, 5*1000, 1);
			ServiceResponse serviceResponse = null;
			if (serviceResponses.size() > 0) {
				serviceResponse = serviceResponses.get(0);
				try {
					E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(updateMessage));
					System.out.println("ControllerClient UpdateManager: topology update sent to the controller");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			else
				System.out.println("ControllerClient: controller service not found, cannot send topology update");
		}
		
		@Override
		public void run() {
			System.out.println("ControllerClient UpdateManager START");
			while (this.active == true) {
				// Send invoked before sleep method to allow the controller to receive informations at components startup
				sendTopologyUpdate();
				try {
					Thread.sleep(TIME_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				updateFlows();
			}
			System.out.println("ControllerClient UpdateManager FINISHED");
		}
	}

}
