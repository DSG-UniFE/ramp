package test.sdncontroller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Vector;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class ControllerClientTestSender {
	
	private static ControllerClient controllerClient;
	private static RampEntryPoint ramp;
	
	private static void sendTwoMessages() {
		String message = "Hello, world!";
		
		System.out.println("ControllerClientTestSender: waiting 40 seconds");
		try {
			Thread.sleep(40*1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		Vector<ServiceResponse> serviceResponses = null;
		try {
			serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSend", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse serviceResponse = null;
		if (serviceResponses.size() > 0)
			serviceResponse = serviceResponses.get(0);
		
		ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.VIDEO_STREAM, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
		int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
		int[] destPorts = new int[0];
		int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
		
		System.out.println("ControllerClientTestSender: sending message \""
			+ message + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), flowId: " + flowId);
		try {
			E2EComm.sendUnicast(
					serviceResponse.getServerDest(),
					serviceResponse.getServerNodeId(),
					serviceResponse.getServerPort(),
					serviceResponse.getProtocol(),
					false,
					GenericPacket.UNUSED_FIELD,
					E2EComm.DEFAULT_BUFFERSIZE,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					flowId,
					E2EComm.serialize(message)
			);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: message sent to the receiver");
		
		System.out.println("ControllerClientTestSender: waiting 5 seconds");
		try {
			Thread.sleep(5*1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		message = "Second message.";
		
		applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
		flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
		
		System.out.println("ControllerClientTestSender: sending message \""
				+ message + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), flowId: " + flowId);
		try {
			E2EComm.sendUnicast(
					serviceResponse.getServerDest(),
					serviceResponse.getServerNodeId(),
					serviceResponse.getServerPort(),
					serviceResponse.getProtocol(),
					false,
					GenericPacket.UNUSED_FIELD,
					E2EComm.DEFAULT_BUFFERSIZE,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					flowId,
					E2EComm.serialize(message)
			);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: message sent to the receiver");
		
		System.out.println("ControllerClientTestSender: waiting 20 seconds");
		try {
			Thread.sleep(20*1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private static void sendTwoFilesToDifferentReceivers() {
		System.out.println("ControllerClientTestSender: waiting 10 seconds");
		try {
			Thread.sleep(10*1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		Vector<ServiceResponse> serviceResponses = null;
		try {
			serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendFirst", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse serviceResponse = null;
		if (serviceResponses.size() > 0)
			serviceResponse = serviceResponses.get(0);

		BoundReceiveSocket responseSocket = null;
		try {
			responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		
		String fileName = "./ramp_controllerclienttest.jar";
		String message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending first file name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: first file name sent to the receiver");
		
		String response = null;
		GenericPacket gp = null;
		try {
			gp = E2EComm.receive(responseSocket);
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		if (gp instanceof UnicastPacket) {
			UnicastPacket up = (UnicastPacket) gp;
			Object payload = null;
			try {
				payload = E2EComm.deserialize(up.getBytePayload());
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (payload instanceof String) {
				response = (String) payload;
			}
		}
		
		if (response.equals("ok")) {
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			// File firstFile = new File("./ramp_controllerclienttest.jar");
			File firstFile = new File (fileName);
			FileInputStream firstFileInputStream = null;
			try {
				firstFileInputStream = new FileInputStream(firstFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
			System.out.println("ControllerClientTestSender: sending the first file to the receiver (nodeId: "
				+ serviceResponse.getServerNodeId() + "), flowId: " + flowId);
			try {
				E2EComm.sendUnicast(
						serviceResponse.getServerDest(),
						serviceResponse.getServerNodeId(),
						serviceResponse.getServerPort(),
						serviceResponse.getProtocol(),
						false,
						GenericPacket.UNUSED_FIELD,
						E2EComm.DEFAULT_BUFFERSIZE,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						flowId,
						firstFileInputStream);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("ControllerClientTestSender: first file sent to the receiver");
		}
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		Vector<ServiceResponse> secondServiceResponses = null;
		try {
			secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse secondServiceResponse = null;
		if (secondServiceResponses.size() > 0)
			secondServiceResponse = secondServiceResponses.get(0);
		
		fileName = "./ramp_controllerclienttestsender.jar";
		message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending second file name to the receiver (nodeId: " + secondServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(secondServiceResponse.getServerDest(), secondServiceResponse.getServerPort(), secondServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: second file name sent to the receiver");
		
		response = null;
		gp = null;
		try {
			gp = E2EComm.receive(responseSocket);
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		if (gp instanceof UnicastPacket) {
			UnicastPacket up = (UnicastPacket) gp;
			Object payload = null;
			try {
				payload = E2EComm.deserialize(up.getBytePayload());
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (payload instanceof String) {
				response = (String) payload;
			}
		}
		
		if (response.equals("ok")) {
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {secondServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			// File secondFile = new File("./ramp_controllerclienttestsender.jar");
			File secondFile = new File(fileName);
			FileInputStream secondFileInputStream = null;
			try {
				secondFileInputStream = new FileInputStream(secondFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
			System.out.println("ControllerClientTestSender: sending the second file to the receiver (nodeId: "
				+ secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			try {
				E2EComm.sendUnicast(
						secondServiceResponse.getServerDest(),
						secondServiceResponse.getServerNodeId(),
						secondServiceResponse.getServerPort(),
						secondServiceResponse.getProtocol(),
						false,
						GenericPacket.UNUSED_FIELD,
						E2EComm.DEFAULT_BUFFERSIZE,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						flowId,
						secondFileInputStream);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("ControllerClientTestSender: second file sent to the receiver");
		}
		
		System.out.println("ControllerClientTestSender: waiting 400 seconds");
		try {
			Thread.sleep(400*1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	private static void sendMessageToMultipleReceivers() {
		String message = "Hello, world!";
		
		System.out.println("ControllerClientTestSender: waiting 10 seconds");
		try {
			Thread.sleep(10*1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		Vector<ServiceResponse> serviceResponses = null;
		try {
			serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSend", 5*1000, 2, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse firstServiceResponse = null;
		ServiceResponse secondServiceResponse = null;
		if (serviceResponses.size() == 2) {
			firstServiceResponse = serviceResponses.get(0);
			secondServiceResponse = serviceResponses.get(1);
		}
		
		ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
		int[] destNodeIds = new int[] {firstServiceResponse.getServerNodeId(), secondServiceResponse.getServerNodeId()};
		int[] destPorts = new int[] {firstServiceResponse.getServerPort(), secondServiceResponse.getServerPort()};
		int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
		
		System.out.println("ControllerClientTestSender: sending message \""
			+ message + "\" to the receivers (first nodeId: " + firstServiceResponse.getServerNodeId() + ", second nodeId: " + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
		try {
			E2EComm.sendUnicast(
					new String[] {GeneralUtils.getLocalHost()},
					Dispatcher.getLocalRampId(),
					40000,
					firstServiceResponse.getProtocol(),
					false,
					GenericPacket.UNUSED_FIELD,
					E2EComm.DEFAULT_BUFFERSIZE,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					GenericPacket.UNUSED_FIELD,
					flowId,
					E2EComm.serialize(message)
			);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: message sent to the receiver");
		
		System.out.println("ControllerClientTestSender: waiting 5 seconds");
		try {
			Thread.sleep(5*1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		
		ramp = RampEntryPoint.getInstance(true, null);
		
		// Wait a few second to allow the node to discover neighbors, otherwise the service cannot be found
		try {
			Thread.sleep(5*1000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		// Force neighbors update to make sure to know them
		ramp.forceNeighborsUpdate();
		
		System.out.println("ControllerClientTestSender: registering shutdown hook");
		// Setup signal handling in order to always stop RAMP gracefully
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (ramp != null && controllerClient != null) {
						System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
						controllerClient.stopClient();
						ramp.stopRamp();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}));
		
		controllerClient = ControllerClient.getInstance();

		sendMessageToMultipleReceivers();
		
		controllerClient.stopClient();
		ramp.stopRamp();
	}

}
