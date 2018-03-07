package test.sdncontroller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
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
			ApplicationRequirements applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
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
		sendThreeFilesToDifferentReceivers();
		statsPrinter.stopStatsPrinter();
		
		controllerClient.stopClient();
		ramp.stopRamp();
	}
	
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
				if (networkIFs[i].getName().equals("wlan2"))
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
