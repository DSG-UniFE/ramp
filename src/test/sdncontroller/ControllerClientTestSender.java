<<<<<<< HEAD
package test.sdncontroller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Vector;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceRequest;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class ControllerClientTestSender {
	
	private static ControllerClient controllerClient;
	private static RampEntryPoint ramp;
	
	// Test method for Best Path policy (send two consecutive strings)
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
		
		// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
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
	
	// Test method for no SDN policy (send a single file, selected through the fileName variable)
	private static void sendAFile() {
		System.out.println("ControllerClientTestSender: waiting 10 seconds");
		try {
			Thread.sleep(10*1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
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

		BoundReceiveSocket responseSocket = null;
		try {
			responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		
		String fileName = "./ramp_controllerclienttest.jar";
		String message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending file name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: file name sent to the receiver");
		
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
			// ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			// int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
			// int[] destPorts = new int[0];
			// int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			int flowId = GenericPacket.UNUSED_FIELD;
			
			// File file = new File("./ramp_controllerclienttest.jar");
			File file = new File (fileName);
			FileInputStream fileInputStream = null;
			try {
				fileInputStream = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
			System.out.println("ControllerClientTestSender: sending the file to the receiver (nodeId: "
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
						fileInputStream);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("ControllerClientTestSender: file sent to the receiver");
			
			String finalMessage = null;
			gp = null;
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
				if (payload instanceof String)
					finalMessage = (String) payload;
			}
			if (finalMessage.equals("file_received"))
				System.out.println("ControllerClientTestSender: final message received from the receiver, file transfer completed");
			else
				System.out.println("ControllerClientTestSender: wrong final message received from the receiver");
		}
	}
	
	// Test method for traffic engineering policies (send two files addressing different RAMP services)
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
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
			Thread.sleep(5000);
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
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
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("file_received") && secondFinalMessage.equals("file_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
	}
	
	// Test method for traffic engineering policies (send three files addressing different RAMP services)
	private static void sendThreeFilesToDifferentReceivers() {
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
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
			Thread.sleep(3000);
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
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
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		Vector<ServiceResponse> thirdServiceResponses = null;
		try {
			thirdServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendThird", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse thirdServiceResponse = null;
		if (thirdServiceResponses.size() > 0)
			thirdServiceResponse = thirdServiceResponses.get(0);
		
		fileName = "./ramp_controllerclienttestreceiver.jar";
		message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending third file name to the receiver (nodeId: " + thirdServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(thirdServiceResponse.getServerDest(), thirdServiceResponse.getServerPort(), thirdServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: third file name sent to the receiver");
		
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {thirdServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			// File secondFile = new File("./ramp_controllerclienttestsender.jar");
			File thirdFile = new File(fileName);
			FileInputStream thirdFileInputStream = null;
			try {
				thirdFileInputStream = new FileInputStream(thirdFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
			System.out.println("ControllerClientTestSender: sending the third file to the receiver (nodeId: "
				+ thirdServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			try {
				E2EComm.sendUnicast(
						thirdServiceResponse.getServerDest(),
						thirdServiceResponse.getServerNodeId(),
						thirdServiceResponse.getServerPort(),
						thirdServiceResponse.getProtocol(),
						false,
						GenericPacket.UNUSED_FIELD,
						E2EComm.DEFAULT_BUFFERSIZE,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						flowId,
						thirdFileInputStream);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("ControllerClientTestSender: third file sent to the receiver");
		}
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		String thirdFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				thirdFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("file_received") && secondFinalMessage.equals("file_received") && thirdFinalMessage.equals("file_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
	}
	
	// Test method for traffic engineering policies with UDP protocol (send two series of consecutive packets addressing different RAMP services)
	private static void sendTwoSeriesOfPacketsToDifferentReceivers() {
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
		final ServiceResponse serviceResponse = serviceResponses.get(0);

		BoundReceiveSocket responseSocket = null;
		try {
			responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		Vector<ServiceResponse> secondServiceResponses = null;
		try {
			secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		final ServiceResponse secondServiceResponse = secondServiceResponses.get(0);
		
		BoundReceiveSocket secondResponseSocket = null;
		try {
			secondResponseSocket = E2EComm.bindPreReceive(secondServiceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("" + secondServiceResponse.getServerPort() + secondResponseSocket.getLocalPort());
		
		String fileName = "first_series";
		String message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending first series of packets name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: first series of packets name sent to the receiver");
		
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
		
		Thread firstThread = null;
		if (response.equals("ok")) {
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the first series of packets to the receiver (nodeId: "
				+ serviceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[60000];
			firstThread = new Thread() {
				public void run() {
					long now = System.currentTimeMillis();
					for (int i = 0; i < 1000; i++) {
						try {
							LocalDateTime localDateTime = LocalDateTime.now();
							String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
							System.out.println("First thread " + timestamp);
							E2EComm.sendUnicast(
									serviceResponse.getServerDest(),
									serviceResponse.getServerNodeId(),
									serviceResponse.getServerPort(),
									E2EComm.UDP,
									false,
									GenericPacket.UNUSED_FIELD,
									E2EComm.DEFAULT_BUFFERSIZE,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									flowId,
									payload);
							long sleep = 20 - (System.currentTimeMillis()-now);
							if (sleep > 0)
								Thread.sleep(sleep);
							now = System.currentTimeMillis();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			};
			firstThread.start();
			System.out.println("ControllerClientTestSender: first series of packets sent to the receiver");
		}
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		fileName = "second_series";
		message = fileName + ";" + secondResponseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending second series of packets name to the receiver (nodeId: " + secondServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(secondServiceResponse.getServerDest(), secondServiceResponse.getServerPort(), secondServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: second series of packets name sent to the receiver");
		
		response = null;
		gp = null;
		try {
			gp = E2EComm.receive(secondResponseSocket);
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
		
		Thread secondThread = null;
		if (response.equals("ok")) {
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {secondServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the second series of packets to the receiver (nodeId: "
				+ secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[60000];
			secondThread = new Thread() {
				public void run() {
					long now = System.currentTimeMillis();
					for (int i = 0; i < 1000; i++) {
						try {
							LocalDateTime localDateTime = LocalDateTime.now();
							String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
							System.out.println("Second thread " + timestamp);
							E2EComm.sendUnicast(
									secondServiceResponse.getServerDest(),
									secondServiceResponse.getServerNodeId(),
									secondServiceResponse.getServerPort(),
									E2EComm.UDP,
									false,
									GenericPacket.UNUSED_FIELD,
									E2EComm.DEFAULT_BUFFERSIZE,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									flowId,
									payload);
							long sleep = 20 - (System.currentTimeMillis()-now);
							Thread.sleep(sleep);
							now = System.currentTimeMillis();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			};
			secondThread.start();
			System.out.println("ControllerClientTestSender: second series of packets sent to the receiver");
		}
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
		try {
			gp = E2EComm.receive(secondResponseSocket);
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("series_received") && secondFinalMessage.equals("series_received")) {
			firstThread.interrupt();
			secondThread.interrupt();
			System.out.println("ControllerClientTestSender: final messages received from the receivers, series transfer completed");
		}
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
	}
	
	// Test method for traffic engineering policies using UDP protocol (send three series of consecutive packets addressing different RAMP services)
	private static void sendThreeSeriesOfPacketsToDifferentReceivers() {
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
		ServiceResponse serviceResponse = serviceResponses.get(0);

		BoundReceiveSocket responseSocket = null;
		try {
			responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		
		String fileName = "first_series";
		String message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending first series of packets name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: first series of packets name sent to the receiver");
		
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the first series of packets to the receiver (nodeId: "
				+ serviceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[10000];
			new Thread() {
				public void run() {
					try {
						for (int i = 0; i < 5000; i++) {
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
									payload);
							Thread.sleep(1, 600000);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			System.out.println("ControllerClientTestSender: first series of packets sent to the receiver");
		}
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		Vector<ServiceResponse> secondServiceResponses = null;
		try {
			secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse secondServiceResponse = secondServiceResponses.get(0);
		
		fileName = "second_series";
		message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending second series of packets name to the receiver (nodeId: " + secondServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(secondServiceResponse.getServerDest(), secondServiceResponse.getServerPort(), secondServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: second series of packets name sent to the receiver");
		
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {secondServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the second series of packets to the receiver (nodeId: "
				+ secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[10000];
			new Thread() {
				public void run() {
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
								payload);
						Thread.sleep(1, 600000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			System.out.println("ControllerClientTestSender: second file sent to the receiver");
		}
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		Vector<ServiceResponse> thirdServiceResponses = null;
		try {
			thirdServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendThird", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse thirdServiceResponse = thirdServiceResponses.get(0);
		
		fileName = "third_series";
		message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending third series of packets name to the receiver (nodeId: " + thirdServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(thirdServiceResponse.getServerDest(), thirdServiceResponse.getServerPort(), thirdServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: third series of packets name sent to the receiver");
		
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
			// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {thirdServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the third series of packets to the receiver (nodeId: "
				+ thirdServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[10000];
			new Thread() {
				public void run() {
					try {
						E2EComm.sendUnicast(
								thirdServiceResponse.getServerDest(),
								thirdServiceResponse.getServerNodeId(),
								thirdServiceResponse.getServerPort(),
								thirdServiceResponse.getProtocol(),
								false,
								GenericPacket.UNUSED_FIELD,
								E2EComm.DEFAULT_BUFFERSIZE,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								flowId,
								payload);
						Thread.sleep(1, 600000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			System.out.println("ControllerClientTestSender: third series of packets sent to the receiver");
		}
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		String thirdFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				thirdFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("series_received") && secondFinalMessage.equals("series_received") && thirdFinalMessage.equals("series_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
	}
	
	// Test method for Tree-based Multicast policy (send a string to multiple receivers)
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
		
		// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
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
	
	// Test method for Tree-based Multicast policy (send two payloads to multiple receivers using separate communication and then repeat adopting the Tree-based Multicast policy)
	private static void sendMultipleMessagesToMultipleReceivers() throws Exception {
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
		final ServiceResponse firstServiceResponse = serviceResponses.get(0);
		final ServiceResponse secondServiceResponse = serviceResponses.get(1);
		
		final BoundReceiveSocket firstResponseSocket = E2EComm.bindPreReceive(firstServiceResponse.getProtocol());
		final BoundReceiveSocket secondResponseSocket = E2EComm.bindPreReceive(secondServiceResponse.getProtocol());
		
		new Thread() {
			public void run() {
				String message = Integer.toString(firstResponseSocket.getLocalPort());
				
				System.out.println("ControllerClientTestSender: sending first message port to the first receiver (nodeId: " + firstServiceResponse.getServerNodeId() + ")");
				try {
					E2EComm.sendUnicast(firstServiceResponse.getServerDest(), firstServiceResponse.getServerPort(), firstServiceResponse.getProtocol(), E2EComm.serialize(message));
				} catch (Exception e3) {
					e3.printStackTrace();
				}
				System.out.println("ControllerClientTestSender: first message port sent to the first receiver");
				
				String response = null;
				GenericPacket gp = null;
				try {
					gp = E2EComm.receive(firstResponseSocket);
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
					// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
					ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 200);
					int[] destNodeIds = new int[] {firstServiceResponse.getServerNodeId()};
					int[] destPorts = new int[] {firstServiceResponse.getServerPort()};
					int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
					
					byte[] messagePayload = new byte[20000000];
					System.out.println("ControllerClientTestSender: sending the first message to the first receiver (nodeId: "
						+ firstServiceResponse.getServerNodeId() + "), flowId: " + flowId);
					try {
						E2EComm.sendUnicast(
								firstServiceResponse.getServerDest(),
								firstServiceResponse.getServerNodeId(),
								firstServiceResponse.getServerPort(),
								firstServiceResponse.getProtocol(),
								false,
								GenericPacket.UNUSED_FIELD,
								E2EComm.DEFAULT_BUFFERSIZE,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								flowId,
								messagePayload);
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("ControllerClientTestSender: first message sent to the first receiver");
				}
			}
		}.start();
		
		new Thread() {
			public void run() {
				String message = Integer.toString(secondResponseSocket.getLocalPort());
				
				System.out.println("ControllerClientTestSender: sending first message port to the second receiver (nodeId: " + secondServiceResponse.getServerNodeId() + ")");
				try {
					E2EComm.sendUnicast(secondServiceResponse.getServerDest(), secondServiceResponse.getServerPort(), secondServiceResponse.getProtocol(), E2EComm.serialize(message));
				} catch (Exception e3) {
					e3.printStackTrace();
				}
				System.out.println("ControllerClientTestSender: first message port sent to the second receiver");
				
				String response = null;
				GenericPacket gp = null;
				try {
					gp = E2EComm.receive(secondResponseSocket);
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
					// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
					ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 200);
					int[] destNodeIds = new int[] {secondServiceResponse.getServerNodeId()};
					int[] destPorts = new int[] {secondServiceResponse.getServerPort()};
					int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
					
					byte[] messagePayload = new byte[20000000];
					System.out.println("ControllerClientTestSender: sending the first message to the second receiver (nodeId: "
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
								messagePayload);
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("ControllerClientTestSender: first message sent to the second receiver");
				}
			}
		}.start();
		
//		String firstFinalMessage = null;
//		GenericPacket gp = null;
//		try {
//			gp = E2EComm.receive(firstResponseSocket);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		if (gp instanceof UnicastPacket) {
//			UnicastPacket up = (UnicastPacket) gp;
//			Object payload = null;
//			try {
//				payload = E2EComm.deserialize(up.getBytePayload());
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			if (payload instanceof String)
//				firstFinalMessage = (String) payload;
//		}
//		String secondFinalMessage = null;
//		gp = null;
//		try {
//			gp = E2EComm.receive(secondResponseSocket);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		if (gp instanceof UnicastPacket) {
//			UnicastPacket up = (UnicastPacket) gp;
//			Object payload = null;
//			try {
//				payload = E2EComm.deserialize(up.getBytePayload());
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			if (payload instanceof String)
//				secondFinalMessage = (String) payload;
//		}
//		if (firstFinalMessage.equals("message_received") && secondFinalMessage.equals("message_received"))
//			System.out.println("ControllerClientTestSender: first two messages sent, waiting 5 seconds and sending the third message");
		
		System.out.println("ControllerClientTestSender: waiting 20 seconds and sending the third message");
		try {
			Thread.sleep(20*1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		// Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
		ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
		int[] destNodeIds = new int[] {firstServiceResponse.getServerNodeId(), secondServiceResponse.getServerNodeId()};
		int[] destPorts = new int[] {firstServiceResponse.getServerPort(), secondServiceResponse.getServerPort()};
		int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
		
		byte[] messagePayload = new byte[20000000];
		System.out.println("ControllerClientTestSender: sending the third message to the receivers (first nodeId: "
				+ firstServiceResponse.getServerNodeId() + ", second nodeId: " + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
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
					messagePayload
			);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: message sent to the receiver");
		
		String firstFinalMessage = null;
		GenericPacket gp = null;
		try {
			gp = E2EComm.receive(firstResponseSocket);
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
		try {
			gp = E2EComm.receive(secondResponseSocket);
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("message_received") && secondFinalMessage.equals("message_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, message transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
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

//		ServiceRequest serviceRequest = new ServiceRequest("SDNController", 65535, null);
//		BroadcastPacket bp = null;
//		try {
//			bp = new BroadcastPacket((byte)5, 65535, 2, -1, E2EComm.serialize(serviceRequest));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.out.println("ControllerClientTestSender: service request size: " + E2EComm.objectSizePacket(bp));
//		ServiceResponse serviceResponse = new ServiceResponse("SDNController", 65535, E2EComm.TCP, null);
//		try {
//			bp = new BroadcastPacket((byte)5, 65535, 1, -1, E2EComm.serialize(serviceResponse));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.out.println("ControllerClientTestReceiver: service response size: " + E2EComm.objectSizePacket(bp));
		
		StatsPrinter statsPrinter = new StatsPrinter("output_external.csv");
		statsPrinter.start();
		try {
			// Test method to run, match it with the one in ControllerClientTestReceiver
			sendTwoSeriesOfPacketsToDifferentReceivers();
		} catch (Exception e) {
			e.printStackTrace();
		}
		statsPrinter.stopStatsPrinter();
		
		controllerClient.stopClient();
		ramp.stopRamp();
	}
	
	// Utility to log all network traffic on a specific network interface
	private static class StatsPrinter extends Thread {
		
		private static final int TIME_INTERVAL = 500;

		private String outputFileName;
		private boolean active;
		
		StatsPrinter(String outputFileName) {
			this.outputFileName = outputFileName;
			this.active = true;
		}
		
		public void stopStatsPrinter() {
			this.active = false;
		}
		
		public void run() {
			File outputFile = new File(outputFileName);
			PrintWriter printWriter = null;
			try {
				printWriter = new PrintWriter(outputFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			printWriter.println("timestamp,throughput");
			
			SystemInfo systemInfo = new SystemInfo();
			HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
			NetworkIF[] networkIFs = hardwareAbstractionLayer.getNetworkIFs();
			NetworkIF transmissionInterface = null;
			for (int i = 0; i < networkIFs.length; i++)
				// Select the network interface to monitor
				if (networkIFs[i].getName().equals("eth0"))
					transmissionInterface = networkIFs[i];
			long startTransmittedBytes = 0;
			transmissionInterface.updateNetworkStats();
			startTransmittedBytes = startTransmittedBytes + transmissionInterface.getBytesSent();
			
			while (this.active == true) {
				try {
					Thread.sleep(TIME_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				long transmittedBytes = 0;
				transmissionInterface.updateNetworkStats();
				transmittedBytes = transmittedBytes + transmissionInterface.getBytesSent();
				System.out.println("bytes sent: " + transmissionInterface.getBytesSent());
				long periodTransmittedBytes = transmittedBytes - startTransmittedBytes;
				startTransmittedBytes = transmittedBytes;
				double throughput = periodTransmittedBytes / ((double) TIME_INTERVAL / 1000);
				LocalDateTime localDateTime = LocalDateTime.now();
				String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
				printWriter.println(timestamp + "," + throughput);
			}
			
			printWriter.close();
		}
	}

}
=======
package test.sdncontroller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Vector;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceRequest;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

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
	
	private static void sendAFile() {
		System.out.println("ControllerClientTestSender: waiting 10 seconds");
		try {
			Thread.sleep(10*1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
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

		BoundReceiveSocket responseSocket = null;
		try {
			responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		
		String fileName = "./ramp_controllerclienttest.jar";
		String message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending file name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: file name sent to the receiver");
		
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
			// ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			// int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
			// int[] destPorts = new int[0];
			// int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			int flowId = GenericPacket.UNUSED_FIELD;
			
			// File file = new File("./ramp_controllerclienttest.jar");
			File file = new File (fileName);
			FileInputStream fileInputStream = null;
			try {
				fileInputStream = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
			System.out.println("ControllerClientTestSender: sending the file to the receiver (nodeId: "
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
						fileInputStream);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("ControllerClientTestSender: file sent to the receiver");
			
			String finalMessage = null;
			gp = null;
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
				if (payload instanceof String)
					finalMessage = (String) payload;
			}
			if (finalMessage.equals("file_received"))
				System.out.println("ControllerClientTestSender: final message received from the receiver, file transfer completed");
			else
				System.out.println("ControllerClientTestSender: wrong final message received from the receiver");
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
			Thread.sleep(5000);
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
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("file_received") && secondFinalMessage.equals("file_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
	}
	
	private static void sendThreeFilesToDifferentReceivers() {
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
			Thread.sleep(3000);
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
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		Vector<ServiceResponse> thirdServiceResponses = null;
		try {
			thirdServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendThird", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse thirdServiceResponse = null;
		if (thirdServiceResponses.size() > 0)
			thirdServiceResponse = thirdServiceResponses.get(0);
		
		fileName = "./ramp_controllerclienttestreceiver.jar";
		message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending third file name to the receiver (nodeId: " + thirdServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(thirdServiceResponse.getServerDest(), thirdServiceResponse.getServerPort(), thirdServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: third file name sent to the receiver");
		
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
			int[] destNodeIds = new int[] {thirdServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			// File secondFile = new File("./ramp_controllerclienttestsender.jar");
			File thirdFile = new File(fileName);
			FileInputStream thirdFileInputStream = null;
			try {
				thirdFileInputStream = new FileInputStream(thirdFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
			System.out.println("ControllerClientTestSender: sending the third file to the receiver (nodeId: "
				+ thirdServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			try {
				E2EComm.sendUnicast(
						thirdServiceResponse.getServerDest(),
						thirdServiceResponse.getServerNodeId(),
						thirdServiceResponse.getServerPort(),
						thirdServiceResponse.getProtocol(),
						false,
						GenericPacket.UNUSED_FIELD,
						E2EComm.DEFAULT_BUFFERSIZE,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						GenericPacket.UNUSED_FIELD,
						flowId,
						thirdFileInputStream);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("ControllerClientTestSender: third file sent to the receiver");
		}
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		String thirdFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				thirdFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("file_received") && secondFinalMessage.equals("file_received") && thirdFinalMessage.equals("file_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
	}
	
	static void sendTwoSeriesOfPacketsToDifferentReceivers(ControllerClient controllerClient) {
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
		final ServiceResponse serviceResponse = serviceResponses.get(0);

		BoundReceiveSocket responseSocket = null;
		try {
			responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		Vector<ServiceResponse> secondServiceResponses = null;
		try {
			secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		final ServiceResponse secondServiceResponse = secondServiceResponses.get(0);
		
		BoundReceiveSocket secondResponseSocket = null;
		try {
			secondResponseSocket = E2EComm.bindPreReceive(secondServiceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("" + secondServiceResponse.getServerPort() + secondResponseSocket.getLocalPort());
		
		String fileName = "first_series";
		String message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending first series of packets name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: first series of packets name sent to the receiver");
		
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
		
		Thread firstThread = null;
		if (response.equals("ok")) {
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the first series of packets to the receiver (nodeId: "
				+ serviceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[60000];
			firstThread = new Thread() {
				public void run() {
					long now = System.currentTimeMillis();
					for (int i = 0; i < 1000; i++) {
						try {
							LocalDateTime localDateTime = LocalDateTime.now();
							String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
							System.out.println("First thread " + timestamp);
							E2EComm.sendUnicast(
									serviceResponse.getServerDest(),
									serviceResponse.getServerNodeId(),
									serviceResponse.getServerPort(),
									E2EComm.UDP,
									false,
									GenericPacket.UNUSED_FIELD,
									E2EComm.DEFAULT_BUFFERSIZE,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									flowId,
									payload);
							long sleep = 20 - (System.currentTimeMillis()-now);
							if (sleep > 0)
								Thread.sleep(sleep);
							now = System.currentTimeMillis();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			};
			firstThread.start();
			System.out.println("ControllerClientTestSender: first series of packets sent to the receiver");
		}
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		fileName = "second_series";
		message = fileName + ";" + secondResponseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending second series of packets name to the receiver (nodeId: " + secondServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(secondServiceResponse.getServerDest(), secondServiceResponse.getServerPort(), secondServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: second series of packets name sent to the receiver");
		
		response = null;
		gp = null;
		try {
			gp = E2EComm.receive(secondResponseSocket);
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
		
		Thread secondThread = null;
		if (response.equals("ok")) {
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {secondServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the second series of packets to the receiver (nodeId: "
				+ secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[60000];
			secondThread = new Thread() {
				public void run() {
					long now = System.currentTimeMillis();
					for (int i = 0; i < 1000; i++) {
						try {
							LocalDateTime localDateTime = LocalDateTime.now();
							String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
							System.out.println("Second thread " + timestamp);
							E2EComm.sendUnicast(
									secondServiceResponse.getServerDest(),
									secondServiceResponse.getServerNodeId(),
									secondServiceResponse.getServerPort(),
									E2EComm.UDP,
									false,
									GenericPacket.UNUSED_FIELD,
									E2EComm.DEFAULT_BUFFERSIZE,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									GenericPacket.UNUSED_FIELD,
									flowId,
									payload);
							long sleep = 20 - (System.currentTimeMillis()-now);
							Thread.sleep(sleep);
							now = System.currentTimeMillis();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			};
			secondThread.start();
			System.out.println("ControllerClientTestSender: second series of packets sent to the receiver");
		}
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
		try {
			gp = E2EComm.receive(secondResponseSocket);
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("series_received") && secondFinalMessage.equals("series_received")) {
			firstThread.interrupt();
			secondThread.interrupt();
			System.out.println("ControllerClientTestSender: final messages received from the receivers, series transfer completed");
		}
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
	}
	
	private static void sendThreeSeriesOfPacketsToDifferentReceivers() {
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
		ServiceResponse serviceResponse = serviceResponses.get(0);

		BoundReceiveSocket responseSocket = null;
		try {
			responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		
		String fileName = "first_series";
		String message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending first series of packets name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: first series of packets name sent to the receiver");
		
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
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {serviceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the first series of packets to the receiver (nodeId: "
				+ serviceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[10000];
			new Thread() {
				public void run() {
					try {
						for (int i = 0; i < 5000; i++) {
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
									payload);
							Thread.sleep(1, 600000);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			System.out.println("ControllerClientTestSender: first series of packets sent to the receiver");
		}
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		Vector<ServiceResponse> secondServiceResponses = null;
		try {
			secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse secondServiceResponse = secondServiceResponses.get(0);
		
		fileName = "second_series";
		message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending second series of packets name to the receiver (nodeId: " + secondServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(secondServiceResponse.getServerDest(), secondServiceResponse.getServerPort(), secondServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: second series of packets name sent to the receiver");
		
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
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
			int[] destNodeIds = new int[] {secondServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the second series of packets to the receiver (nodeId: "
				+ secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[10000];
			new Thread() {
				public void run() {
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
								payload);
						Thread.sleep(1, 600000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			System.out.println("ControllerClientTestSender: second file sent to the receiver");
		}
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		Vector<ServiceResponse> thirdServiceResponses = null;
		try {
			thirdServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendThird", 5*1000, 1, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		ServiceResponse thirdServiceResponse = thirdServiceResponses.get(0);
		
		fileName = "third_series";
		message = fileName + ";" + responseSocket.getLocalPort();
		
		System.out.println("ControllerClientTestSender: sending third series of packets name to the receiver (nodeId: " + thirdServiceResponse.getServerNodeId() + ")");
		try {
			E2EComm.sendUnicast(thirdServiceResponse.getServerDest(), thirdServiceResponse.getServerPort(), thirdServiceResponse.getProtocol(), E2EComm.serialize(message));
		} catch (Exception e3) {
			e3.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: third series of packets name sent to the receiver");
		
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
			int[] destNodeIds = new int[] {thirdServiceResponse.getServerNodeId()};
			int[] destPorts = new int[0];
			int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
			// int flowId = GenericPacket.UNUSED_FIELD;
			
			System.out.println("ControllerClientTestSender: sending the third series of packets to the receiver (nodeId: "
				+ thirdServiceResponse.getServerNodeId() + "), flowId: " + flowId);
			byte[] payload = new byte[10000];
			new Thread() {
				public void run() {
					try {
						E2EComm.sendUnicast(
								thirdServiceResponse.getServerDest(),
								thirdServiceResponse.getServerNodeId(),
								thirdServiceResponse.getServerPort(),
								thirdServiceResponse.getProtocol(),
								false,
								GenericPacket.UNUSED_FIELD,
								E2EComm.DEFAULT_BUFFERSIZE,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								flowId,
								payload);
						Thread.sleep(1, 600000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			System.out.println("ControllerClientTestSender: third series of packets sent to the receiver");
		}
		
		String firstFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		String thirdFinalMessage = null;
		gp = null;
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
			if (payload instanceof String)
				thirdFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("series_received") && secondFinalMessage.equals("series_received") && thirdFinalMessage.equals("series_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
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
	
	private static void sendMultipleMessagesToMultipleReceivers() throws Exception {
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
		final ServiceResponse firstServiceResponse = serviceResponses.get(0);
		final ServiceResponse secondServiceResponse = serviceResponses.get(1);
		
		final BoundReceiveSocket firstResponseSocket = E2EComm.bindPreReceive(firstServiceResponse.getProtocol());
		final BoundReceiveSocket secondResponseSocket = E2EComm.bindPreReceive(secondServiceResponse.getProtocol());
		
		new Thread() {
			public void run() {
				String message = Integer.toString(firstResponseSocket.getLocalPort());
				
				System.out.println("ControllerClientTestSender: sending first message port to the first receiver (nodeId: " + firstServiceResponse.getServerNodeId() + ")");
				try {
					E2EComm.sendUnicast(firstServiceResponse.getServerDest(), firstServiceResponse.getServerPort(), firstServiceResponse.getProtocol(), E2EComm.serialize(message));
				} catch (Exception e3) {
					e3.printStackTrace();
				}
				System.out.println("ControllerClientTestSender: first message port sent to the first receiver");
				
				String response = null;
				GenericPacket gp = null;
				try {
					gp = E2EComm.receive(firstResponseSocket);
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
					ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 200);
					int[] destNodeIds = new int[] {firstServiceResponse.getServerNodeId()};
					int[] destPorts = new int[] {firstServiceResponse.getServerPort()};
					int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
					
					byte[] messagePayload = new byte[20000000];
					System.out.println("ControllerClientTestSender: sending the first message to the first receiver (nodeId: "
						+ firstServiceResponse.getServerNodeId() + "), flowId: " + flowId);
					try {
						E2EComm.sendUnicast(
								firstServiceResponse.getServerDest(),
								firstServiceResponse.getServerNodeId(),
								firstServiceResponse.getServerPort(),
								firstServiceResponse.getProtocol(),
								false,
								GenericPacket.UNUSED_FIELD,
								E2EComm.DEFAULT_BUFFERSIZE,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								GenericPacket.UNUSED_FIELD,
								flowId,
								messagePayload);
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("ControllerClientTestSender: first message sent to the first receiver");
				}
			}
		}.start();
		
		new Thread() {
			public void run() {
				String message = Integer.toString(secondResponseSocket.getLocalPort());
				
				System.out.println("ControllerClientTestSender: sending first message port to the second receiver (nodeId: " + secondServiceResponse.getServerNodeId() + ")");
				try {
					E2EComm.sendUnicast(secondServiceResponse.getServerDest(), secondServiceResponse.getServerPort(), secondServiceResponse.getProtocol(), E2EComm.serialize(message));
				} catch (Exception e3) {
					e3.printStackTrace();
				}
				System.out.println("ControllerClientTestSender: first message port sent to the second receiver");
				
				String response = null;
				GenericPacket gp = null;
				try {
					gp = E2EComm.receive(secondResponseSocket);
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
					ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 200);
					int[] destNodeIds = new int[] {secondServiceResponse.getServerNodeId()};
					int[] destPorts = new int[] {secondServiceResponse.getServerPort()};
					int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
					
					byte[] messagePayload = new byte[20000000];
					System.out.println("ControllerClientTestSender: sending the first message to the second receiver (nodeId: "
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
								messagePayload);
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("ControllerClientTestSender: first message sent to the second receiver");
				}
			}
		}.start();
		
//		String firstFinalMessage = null;
//		GenericPacket gp = null;
//		try {
//			gp = E2EComm.receive(firstResponseSocket);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		if (gp instanceof UnicastPacket) {
//			UnicastPacket up = (UnicastPacket) gp;
//			Object payload = null;
//			try {
//				payload = E2EComm.deserialize(up.getBytePayload());
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			if (payload instanceof String)
//				firstFinalMessage = (String) payload;
//		}
//		String secondFinalMessage = null;
//		gp = null;
//		try {
//			gp = E2EComm.receive(secondResponseSocket);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		if (gp instanceof UnicastPacket) {
//			UnicastPacket up = (UnicastPacket) gp;
//			Object payload = null;
//			try {
//				payload = E2EComm.deserialize(up.getBytePayload());
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			if (payload instanceof String)
//				secondFinalMessage = (String) payload;
//		}
//		if (firstFinalMessage.equals("message_received") && secondFinalMessage.equals("message_received"))
//			System.out.println("ControllerClientTestSender: first two messages sent, waiting 5 seconds and sending the third message");
		
		System.out.println("ControllerClientTestSender: waiting 20 seconds and sending the third message");
		try {
			Thread.sleep(20*1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
		int[] destNodeIds = new int[] {firstServiceResponse.getServerNodeId(), secondServiceResponse.getServerNodeId()};
		int[] destPorts = new int[] {firstServiceResponse.getServerPort(), secondServiceResponse.getServerPort()};
		int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
		
		byte[] messagePayload = new byte[20000000];
		System.out.println("ControllerClientTestSender: sending the third message to the receivers (first nodeId: "
				+ firstServiceResponse.getServerNodeId() + ", second nodeId: " + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
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
					messagePayload
			);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("ControllerClientTestSender: message sent to the receiver");
		
		String firstFinalMessage = null;
		GenericPacket gp = null;
		try {
			gp = E2EComm.receive(firstResponseSocket);
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
			if (payload instanceof String)
				firstFinalMessage = (String) payload;
		}
		String secondFinalMessage = null;
		gp = null;
		try {
			gp = E2EComm.receive(secondResponseSocket);
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
			if (payload instanceof String)
				secondFinalMessage = (String) payload;
		}
		if (firstFinalMessage.equals("message_received") && secondFinalMessage.equals("message_received"))
			System.out.println("ControllerClientTestSender: final messages received from the receivers, message transfer completed");
		else
			System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
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

//		ServiceRequest serviceRequest = new ServiceRequest("SDNController", 65535, null);
//		BroadcastPacket bp = null;
//		try {
//			bp = new BroadcastPacket((byte)5, 65535, 2, -1, E2EComm.serialize(serviceRequest));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.out.println("ControllerClientTestSender: service request size: " + E2EComm.objectSizePacket(bp));
//		ServiceResponse serviceResponse = new ServiceResponse("SDNController", 65535, E2EComm.TCP, null);
//		try {
//			bp = new BroadcastPacket((byte)5, 65535, 1, -1, E2EComm.serialize(serviceResponse));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		System.out.println("ControllerClientTestReceiver: service response size: " + E2EComm.objectSizePacket(bp));
		
		StatsPrinter statsPrinter = new StatsPrinter("output_external.csv");
		statsPrinter.start();
		try {
			sendTwoSeriesOfPacketsToDifferentReceivers(controllerClient);
		} catch (Exception e) {
			e.printStackTrace();
		}
		statsPrinter.stopStatsPrinter();
		
		controllerClient.stopClient();
		ramp.stopRamp();
	}
	
	static class StatsPrinter extends Thread {
		
		private static final int TIME_INTERVAL = 500;

		private String outputFileName;
		private boolean active;
		
		StatsPrinter(String outputFileName) {
			this.outputFileName = outputFileName;
			this.active = true;
		}
		
		public void stopStatsPrinter() {
			this.active = false;
		}
		
		public void run() {
			File outputFile = new File(outputFileName);
			PrintWriter printWriter = null;
			try {
				printWriter = new PrintWriter(outputFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			printWriter.println("timestamp,throughput");
			
			SystemInfo systemInfo = new SystemInfo();
			HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
			NetworkIF[] networkIFs = hardwareAbstractionLayer.getNetworkIFs();
			NetworkIF transmissionInterface = null;
			for (int i = 0; i < networkIFs.length; i++)
				if (networkIFs[i].getName().equals("eth0"))
					transmissionInterface = networkIFs[i];
			long startTransmittedBytes = 0;
			transmissionInterface.updateNetworkStats();
			startTransmittedBytes = startTransmittedBytes + transmissionInterface.getBytesSent();
			
			while (this.active == true) {
				try {
					Thread.sleep(TIME_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				long transmittedBytes = 0;
				transmissionInterface.updateNetworkStats();
				transmittedBytes = transmittedBytes + transmissionInterface.getBytesSent();
				System.out.println("bytes sent: " + transmissionInterface.getBytesSent());
				long periodTransmittedBytes = transmittedBytes - startTransmittedBytes;
				startTransmittedBytes = transmittedBytes;
				double throughput = periodTransmittedBytes / ((double) TIME_INTERVAL / 1000);
				LocalDateTime localDateTime = LocalDateTime.now();
				String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
				printWriter.println(timestamp + "," + throughput);
			}
			
			printWriter.close();
		}
	}

}
>>>>>>> branch 'master' of https://github.com/DSG-UniFE/ramp.git
