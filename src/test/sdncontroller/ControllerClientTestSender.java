package test.sdncontroller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.TrafficType;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 * <p>
 * This test must be executed on the node that is responsible to
 * start a new communication by selecting the preferred static method
 * to be called in the main placed in the bottom.
 */
public class ControllerClientTestSender {

    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    /**
     * Test method for Best Path policy (send two consecutive strings)
     */
    private static void sendTwoMessages() {
        String message = "Hello, world!";

        System.out.println("ControllerClientTestSender: waiting 40 seconds");
        try {
            Thread.sleep(40 * 1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSend", 5 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceResponse serviceResponse = null;
        if (serviceResponses.size() > 0)
            serviceResponse = serviceResponses.get(0);

        // Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
        ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.VIDEO_STREAM, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
        int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
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
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

        message = "Second message.";

        applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
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
            Thread.sleep(20 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Test method for no SDN policy (send a single file, selected through the fileName variable)
     */
    private static void sendAFile() {
        System.out.println("ControllerClientTestSender: waiting 10 seconds");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSend", 5 * 1000, 1, null);
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
            File file = new File(fileName);
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

    /**
     * Test method for traffic engineering policies (send two files addressing different RAMP services)
     * This is ideal to test the SinglePriorityForwarder
     * <p>
     * Tips:
     * use a file called testFile.txt and place it in the project root directory.
     * to create a file of a preferred size use the following command in linux
     * truncate -s xM testFile.txt
     * where x is the dimension in megabyte of the size.
     * For a file of 55MB the command is
     * truncate -s 55M testFile.txt
     * <p>
     * In this method are needed two files called
     * - testFile.txt for the first receiver
     * - testFile2.txt for the second receiver
     */
    private static void sendTwoFilesToDifferentReceivers() {
        /*
         * Initialize the output file to keep track when each packet of the flow
         * is sent in order to get the total flow latency and the average packet
         * latency.
         */
        File outputFile = new File("flow_latencies_sender.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("timestamp,fileName");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("ControllerClientTestSender: waiting 10 seconds");

        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
         * Discover the first receiver and get its information.
         */
        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendFirst", 60 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceResponse serviceResponse = null;
        assert serviceResponses != null;
        if (serviceResponses.size() > 0)
            serviceResponse = serviceResponses.get(0);

        BoundReceiveSocket responseSocket = null;
        try {
            assert serviceResponse != null;
            responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }

        assert responseSocket != null;
        int responseSocketPort = responseSocket.getLocalPort();

        /*
         * Discover the second receiver and get its information.
         */
        Vector<ServiceResponse> secondServiceResponses = null;
        try {
            secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 60 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceResponse secondServiceResponse = null;
        assert secondServiceResponses != null;
        if (secondServiceResponses.size() > 0)
            secondServiceResponse = secondServiceResponses.get(0);

        /*
         * Send the name of the file to be sent to the first receiver and wait for an ack.
         */
        String firstFileName = "./testFile.txt";
        String message = firstFileName + ";" + responseSocketPort;

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

        /*
         * As soon as we get the ack from the first receiver
         * we can start to send the file.
         */
        assert response != null;
        if (response.equals("ok")) {
            /*
             * Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            File firstFile = new File(firstFileName);
            FileInputStream firstFileInputStream = null;
            try {
                firstFileInputStream = new FileInputStream(firstFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            System.out.println("ControllerClientTestSender: sending the first file to the receiver (nodeId: "
                    + serviceResponse.getServerNodeId() + "), flowId: " + flowId);

            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + firstFileName);

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

        /*
         * Wait 5 seconds before making the second thread start.
         */
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }

        /*
         * Send the name of the file to be sent to the second receiver and wait for an ack.
         */
        String secondFileName = "./testFile2.txt";
        message = secondFileName + ";" + responseSocketPort;

        assert secondServiceResponse != null;
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

        /*
         * As soon as we get the ack from the second receiver
         * we can start to send the file.
         */
        assert response != null;
        if (response.equals("ok")) {
            /*
             * Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{secondServiceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            File secondFile = new File(secondFileName);
            FileInputStream secondFileInputStream = null;
            try {
                secondFileInputStream = new FileInputStream(secondFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            System.out.println("ControllerClientTestSender: sending the second file to the receiver (nodeId: "
                    + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);

            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + secondFileName);

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

        /*
         * Get the final message for the first file.
         */
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

        /*
         * Get the final message for the second file.
         */
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

        assert firstFinalMessage != null;
        assert secondFinalMessage != null;
        if (firstFinalMessage.equals("file_received") && secondFinalMessage.equals("file_received"))
            System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
        else
            System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");

        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies (send three files addressing different RAMP services)
     * This is ideal to test the MultipleFlowsSinglePriorityForwarder
     * <p>
     * Tips:
     * use a file called testFile.txt and place it in the project root directory.
     * to create a file of a preferred size use the following command in linux
     * truncate -s xM testFile.txt
     * where x is the dimension in megabyte of the size.
     * For a file of 55MB the command is
     * truncate -s 55M testFile.txt
     * <p>
     * In this method are needed two files called
     * - testFile.txt for the first receiver
     * - testFile2.txt for the second receiver
     * - testFile3.txt for the third receiver
     */
    private static void sendThreeFilesToDifferentReceivers() {
        /*
         * Initialize the output file to keep track when each packet of the flow
         * is sent in order to get the total flow latency and the average packet
         * latency.
         */
        File outputFile = new File("flow_latencies_sender.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("timestamp,fileName");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("ControllerClientTestSender: waiting 10 seconds");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
         * Discover the first receiver and get its information.
         */
        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(20, "SDNControllerTestSendFirst", 120 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceResponse serviceResponse = null;
        assert serviceResponses != null;
        if (serviceResponses.size() > 0)
            serviceResponse = serviceResponses.get(0);

        BoundReceiveSocket responseSocket = null;
        try {
            assert serviceResponse != null;
            responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }

        /*
         * Discover the second receiver and get its information.
         */
        Vector<ServiceResponse> secondServiceResponses = null;
        try {
            secondServiceResponses = ServiceDiscovery.findServices(20, "SDNControllerTestSendSecond", 120 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceResponse secondServiceResponse = null;
        assert secondServiceResponses != null;
        if (secondServiceResponses.size() > 0)
            secondServiceResponse = secondServiceResponses.get(0);

        /*
         * Discover the third receiver and get its information.
         */
        Vector<ServiceResponse> thirdServiceResponses = null;
        try {
            thirdServiceResponses = ServiceDiscovery.findServices(20, "SDNControllerTestSendThird", 120 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceResponse thirdServiceResponse = null;
        assert thirdServiceResponses != null;
        if (thirdServiceResponses.size() > 0)
            thirdServiceResponse = thirdServiceResponses.get(0);

        /*
         * Send the name of the file to be sent to the first receiver and wait for an ack.
         */
        String firstFileName = "./testFile.txt";
        assert responseSocket != null;
        String message = firstFileName + ";" + responseSocket.getLocalPort();

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

        /*
         * As soon as we get the ack from the first receiver
         * we can start to send the file.
         */
        assert response != null;
        if (response.equals("ok")) {
            /*
             * Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            File firstFile = new File(firstFileName);
            FileInputStream firstFileInputStream = null;
            try {
                firstFileInputStream = new FileInputStream(firstFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            System.out.println("ControllerClientTestSender: sending the first file to the receiver (nodeId: "
                    + serviceResponse.getServerNodeId() + "), flowId: " + flowId);

            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + firstFileName);

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

        /*
         * Wait 3 seconds before making the second thread start.
         */
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }

        /*
         * Send the name of the file to be sent to the second receiver and wait for an ack.
         */
        String secondFileName = "./testFile2.txt";
        message = secondFileName + ";" + responseSocket.getLocalPort();

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

        /*
         * As soon as we get the ack from the second receiver
         * we can start to send the file.
         */
        assert response != null;
        if (response.equals("ok")) {
            /*
             * Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{secondServiceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            File secondFile = new File(secondFileName);
            FileInputStream secondFileInputStream = null;
            try {
                secondFileInputStream = new FileInputStream(secondFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            System.out.println("ControllerClientTestSender: sending the second file to the receiver (nodeId: "
                    + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);

            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + secondFileName);

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

        /*
         * Wait 3 seconds before making the second thread start.
         */
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }

        /*
         * Send the name of the file to be sent to the third receiver and wait for an ack.
         */
        String thirdFileName = "./testFile3.txt";
        message = thirdFileName + ";" + responseSocket.getLocalPort();

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

        /*
         * As soon as we get the ack from the third receiver
         * we can start to send the file.
         */
        assert response != null;
        if (response.equals("ok")) {
            // Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{thirdServiceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            File thirdFile = new File(thirdFileName);
            FileInputStream thirdFileInputStream = null;
            try {
                thirdFileInputStream = new FileInputStream(thirdFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            System.out.println("ControllerClientTestSender: sending the third file to the receiver (nodeId: "
                    + thirdServiceResponse.getServerNodeId() + "), flowId: " + flowId);

            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + thirdFileName);

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

        /*
         * Get the final message for the first file.
         */
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

        /*
         * Get the final message for the second file.
         */
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

        /*
         * Get the final message for the third file.
         */
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

        assert firstFinalMessage != null;
        assert secondFinalMessage != null;
        assert thirdFinalMessage != null;
        if (firstFinalMessage.equals("file_received") && secondFinalMessage.equals("file_received") && thirdFinalMessage.equals("file_received"))
            System.out.println("ControllerClientTestSender: final messages received from the receivers, file transfer completed");
        else
            System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");

        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for rerouting policy (send one series of consecutive packets addressing one RAMP service)
     * This is ideal to test the BestPathForwarder
     *
     * @param protocol            Protocol to be used for the series transfer.
     * @param packetPayloadInByte Payload of each packet.
     * @param packetFrequency     Interval in milliseconds between each packet sending.
     * @param totalTransferTime   Total time in seconds of the series transfer.
     *                            The total number of packets is given by the following formula:
     *                            int numberOfPacketsPerSeries = (1000 * totalTime) / packetFrequency;
     */
    public static void sendOneSeriesOfPacketsToOneReceiver(int protocol, int packetPayloadInByte, int packetFrequency, int totalTransferTime) {
        /*
         * Initialize the output file to keep track when each packet of the flow
         * is sent in order to get the total flow latency and the average packet
         * latency.
         */
        File outputFile = new File("flow_latencies_sender.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("timestamp,flowId,seqNumber");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("ControllerClientTestSender: waiting 30 seconds");
        try {
            Thread.sleep(30 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
         * Discover the first receiver get its information.
         */
        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(20, "SDNControllerTestSendFirst", 60 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceResponse serviceResponse = null;
        assert serviceResponses != null;
        if(serviceResponses.size() > 0) {
            serviceResponse = serviceResponses.get(0);
        }

        BoundReceiveSocket responseSocket = null;
        try {
            assert serviceResponse != null;
            responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }

        assert responseSocket != null;
        int responseSocketPort = responseSocket.getLocalPort();

        String fileName = "first_series";
        String message = fileName + ";" + responseSocketPort;

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

        if (response != null && response.equals("ok")) {
            /*
             * Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features.
             *
             * The first series a packet is at low priority.
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
            int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            System.out.println("ControllerClientTestSender: sending the first series of packets to the receiver (nodeId: "
                    + serviceResponse.getServerNodeId() + "), flowId: " + flowId);
            byte[] payload = new byte[packetPayloadInByte];

            ControllerMessageTest packet = new ControllerMessageTest();
            packet.setPayload(payload);
            long preFor = System.currentTimeMillis();
            int numberOfPacketsPerSeries = (1000 * totalTransferTime) / packetFrequency;
            for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                try {
                    int seqNumber = i + 1;
                    packet.setSeqNumber(seqNumber);
                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("First thread " + seqNumber);
                    finalPrintWriter.println(timestamp + "," + flowId + "," + seqNumber);
                    finalPrintWriter.flush();
                    E2EComm.sendUnicast(
                            serviceResponse.getServerDest(),
                            serviceResponse.getServerNodeId(),
                            serviceResponse.getServerPort(),
                            protocol,
                            false,
                            GenericPacket.UNUSED_FIELD,
                            E2EComm.DEFAULT_BUFFERSIZE,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            flowId,
                            E2EComm.serialize(packet));

                    long sleep = (packetFrequency * seqNumber) - (System.currentTimeMillis() - preFor);
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("ControllerClientTestSender: first series of packets sent to the receiver");
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

        assert firstFinalMessage != null;
        if (firstFinalMessage.equals("series_received")) {
            System.out.println("ControllerClientTestSender: final messages received from the receiver, series transfer completed");
        } else {
            System.out.println("ControllerClientTestSender: wrong final messages received from the receiver");
        }

        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies (send two series of consecutive packets addressing different RAMP services)
     * This is ideal to test the SinglePriorityForwarder
     */
    public static void sendTwoSeriesOfPacketsToDifferentReceivers(int protocol, int packetPayloadInByte, int numberOfPacketsPerSeries) {
        /*
         * Initialize the output file to keep track when each packet of the flow
         * is sent in order to get the total flow latency and the average packet
         * latency.
         */
        File outputFile = new File("flow_latencies_sender.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("timestamp,flowId,seqNumber");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("ControllerClientTestSender: waiting 10 seconds");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendFirst", 5 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assert serviceResponses != null;
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
            secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assert secondServiceResponses != null;
        final ServiceResponse secondServiceResponse = secondServiceResponses.get(0);

        BoundReceiveSocket secondResponseSocket = null;
        try {
            secondResponseSocket = E2EComm.bindPreReceive(secondServiceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        assert secondResponseSocket != null;
        System.out.println("" + secondServiceResponse.getServerPort() + secondResponseSocket.getLocalPort());

        String fileName = "first_series";
        assert responseSocket != null;
        String message = fileName + ";" + responseSocket.getLocalPort();

        System.out.println("ControllerClientTestSender: sending first series of packets name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
        try {
            E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
            System.out.println("//////////////// PRIMA RICHIESTA INVIATA ///////////////////");
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
        System.out.println("//////////////// PRIMO ACK RICEVUTO ///////////////////");
        if (response != null && response.equals("ok")) {
            /*
             * Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features.
             *
             * The first series a packet is at low priority.
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
            int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            System.out.println("ControllerClientTestSender: sending the first series of packets to the receiver (nodeId: "
                    + serviceResponse.getServerNodeId() + "), flowId: " + flowId);
            byte[] payload = new byte[packetPayloadInByte];
            /*
             * This is the thread that send the first series of packet.
             */
            firstThread = new Thread(() -> {
                ControllerMessageTest packet = new ControllerMessageTest();
                packet.setPayload(payload);
                long now = System.currentTimeMillis();
                for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                    try {
                        packet.setSeqNumber(i + 1);
                        LocalDateTime localDateTime = LocalDateTime.now();
                        String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                        System.out.println("First thread " + timestamp);
                        //finalPrintWriter.println(timestamp + "," + flowId + "," + (i + 1));
                        E2EComm.sendUnicast(
                                serviceResponse.getServerDest(),
                                serviceResponse.getServerNodeId(),
                                serviceResponse.getServerPort(),
                                protocol,
                                false,
                                GenericPacket.UNUSED_FIELD,
                                E2EComm.DEFAULT_BUFFERSIZE,
                                GenericPacket.UNUSED_FIELD,
                                GenericPacket.UNUSED_FIELD,
                                GenericPacket.UNUSED_FIELD,
                                flowId,
                                E2EComm.serialize(packet));
                        long sleep = 20 - (System.currentTimeMillis() - now);
                        if (sleep > 0)
                            Thread.sleep(sleep);
                        now = System.currentTimeMillis();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            firstThread.start();
            System.out.println("ControllerClientTestSender: first series of packets sent to the receiver");
        }

        System.out.println("//////////////// QUI ////////////////");

        /*
         * Delay the second series of packet
         */
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

            System.out.println("//////////////// SECONDA RICHIESTA INVIATA ///////////////////");
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

        System.out.println("//////////////// SECONDO ACK RICEVUTO ///////////////////");

        Thread secondThread = null;
        if (response != null && response.equals("ok")) {
            /*
             * Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
             *
             * The second series a packet is at high priority.
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
            int[] destNodeIds = new int[]{secondServiceResponse.getServerNodeId()};
            int[] destPorts = new int[0];
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);
            // int flowId = GenericPacket.UNUSED_FIELD;

            System.out.println("ControllerClientTestSender: sending the second series of packets to the receiver (nodeId: "
                    + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
            byte[] payload = new byte[packetPayloadInByte];
            /*
             * This is the thread that send the second series of packet.
             */
            secondThread = new Thread(() -> {
                ControllerMessageTest packet = new ControllerMessageTest();
                packet.setPayload(payload);
                long now = System.currentTimeMillis();
                for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                    try {
                        packet.setSeqNumber(i + 1);
                        LocalDateTime localDateTime = LocalDateTime.now();
                        String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                        System.out.println("Second thread " + timestamp);
                        //finalPrintWriter.println(timestamp + "," + flowId + "," + (i + 1));
                        E2EComm.sendUnicast(
                                secondServiceResponse.getServerDest(),
                                secondServiceResponse.getServerNodeId(),
                                secondServiceResponse.getServerPort(),
                                protocol,
                                false,
                                GenericPacket.UNUSED_FIELD,
                                E2EComm.DEFAULT_BUFFERSIZE,
                                GenericPacket.UNUSED_FIELD,
                                GenericPacket.UNUSED_FIELD,
                                GenericPacket.UNUSED_FIELD,
                                flowId,
                                E2EComm.serialize(packet));
                        long sleep = 20 - (System.currentTimeMillis() - now);
                        Thread.sleep(sleep);
                        now = System.currentTimeMillis();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
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
        } else {
            System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
        }

        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies (send two series of consecutive packets addressing different RAMP services)
     * This is ideal to test the SinglePriorityForwarder
     */
    public static void sendTwoSeriesOfPacketsToDifferentReceiversNew(int protocol, int packetPayloadInByte, int numberOfPacketsPerSeries) {
        /*
         * Initialize the output file to keep track when each packet of the flow
         * is sent in order to get the total flow latency and the average packet
         * latency.
         */
        File outputFile = new File("flow_latencies_sender.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("timestamp,flowId,seqNumber");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("ControllerClientTestSender: waiting 10 seconds");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendFirst", 5 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assert serviceResponses != null;
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
            secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assert secondServiceResponses != null;
        final ServiceResponse secondServiceResponse = secondServiceResponses.get(0);

        BoundReceiveSocket secondResponseSocket = null;
        try {
            secondResponseSocket = E2EComm.bindPreReceive(secondServiceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        assert secondResponseSocket != null;
        System.out.println("" + secondServiceResponse.getServerPort() + secondResponseSocket.getLocalPort());

        String fileName = "first_series";
        assert responseSocket != null;
        String message = fileName + ";" + responseSocket.getLocalPort();

        System.out.println("ControllerClientTestSender: sending first series of packets name to the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
        try {
            E2EComm.sendUnicast(serviceResponse.getServerDest(), serviceResponse.getServerPort(), serviceResponse.getProtocol(), E2EComm.serialize(message));
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        System.out.println("ControllerClientTestSender: first series of packets name sent to the receiver");

        /*
         * Get the first flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features.
         *
         * The first series a packet is at low priority.
         */
        ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
        int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
        int[] destPorts = new int[0];
        int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);

        /*
         * Get the second flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
         *
         * The second series a packet is at high priority.
         */
        ApplicationRequirements secondApplicationRequirements = new ApplicationRequirements(TrafficType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
        int[] secondDestNodeIds = new int[]{secondServiceResponse.getServerNodeId()};
        int[] secondDestPorts = new int[0];
        int secondFlowId = controllerClient.getFlowId(secondApplicationRequirements, secondDestNodeIds, secondDestPorts);

        System.out.println("ControllerClientTestSender: sending the first series of packets to the receiver (nodeId: "
                + serviceResponse.getServerNodeId() + "), flowId: " + flowId);
        byte[] payload = new byte[packetPayloadInByte];
        /*
         * This is the thread that send the first series of packet.
         */
        Thread firstThread = new Thread(() -> {
            ControllerMessageTest packet = new ControllerMessageTest();
            packet.setPayload(payload);
            packet.setPriority(3);
            long now = System.currentTimeMillis();
            for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                try {
                    packet.setSeqNumber(i + 1);
                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("First thread " + timestamp);
                    finalPrintWriter.println(timestamp + "," + flowId + "," + (i + 1));
                    E2EComm.sendUnicast(
                            serviceResponse.getServerDest(),
                            serviceResponse.getServerNodeId(),
                            serviceResponse.getServerPort(),
                            protocol,
                            false,
                            GenericPacket.UNUSED_FIELD,
                            E2EComm.DEFAULT_BUFFERSIZE,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            flowId,
                            E2EComm.serialize(packet));
                    long sleep = 20 - (System.currentTimeMillis() - now);
                    if (sleep > 0)
                        Thread.sleep(sleep);
                    now = System.currentTimeMillis();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("ControllerClientTestSender: first series of packets sent to the receiver");
        });
        firstThread.start();

        /*
         * Delay the second series of packet
         */
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


        System.out.println("ControllerClientTestSender: sending the second series of packets to the receiver (nodeId: "
                + secondServiceResponse.getServerNodeId() + "), flowId: " + secondFlowId);
        byte[] secondPayload = new byte[packetPayloadInByte];
        /*
         * This is the thread that send the second series of packet.
         */
        Thread secondThread = new Thread(() -> {
            ControllerMessageTest packet = new ControllerMessageTest();
            packet.setPayload(secondPayload);
            packet.setPriority(1);
            long now = System.currentTimeMillis();
            for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                try {
                    packet.setSeqNumber(i + 1);
                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("Second thread " + timestamp);
                    finalPrintWriter.println(timestamp + "," + flowId + "," + (i + 1));
                    E2EComm.sendUnicast(
                            secondServiceResponse.getServerDest(),
                            secondServiceResponse.getServerNodeId(),
                            secondServiceResponse.getServerPort(),
                            protocol,
                            false,
                            GenericPacket.UNUSED_FIELD,
                            E2EComm.DEFAULT_BUFFERSIZE,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            secondFlowId,
                            E2EComm.serialize(packet));
                    long sleep = 20 - (System.currentTimeMillis() - now);
                    Thread.sleep(sleep);
                    now = System.currentTimeMillis();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("ControllerClientTestSender: second series of packets sent to the receiver");
        });
        secondThread.start();

        GenericPacket gp = null;
        /*
         * Get the final message for the first series of packet.
         */
        String firstFinalMessage = null;
        try {
            gp = E2EComm.receive(responseSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (gp instanceof UnicastPacket) {
            UnicastPacket up = (UnicastPacket) gp;
            Object packetPayload = null;
            try {
                packetPayload = E2EComm.deserialize(up.getBytePayload());
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (packetPayload instanceof String)
                firstFinalMessage = (String) packetPayload;
        }

        /*
         * Get the final message for the second series of packet.
         */
        String secondFinalMessage = null;
        gp = null;
        try {
            gp = E2EComm.receive(secondResponseSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (gp instanceof UnicastPacket) {
            UnicastPacket up = (UnicastPacket) gp;
            Object packetPayload = null;
            try {
                packetPayload = E2EComm.deserialize(up.getBytePayload());
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (packetPayload instanceof String)
                secondFinalMessage = (String) packetPayload;
        }

        assert firstFinalMessage != null;
        assert secondFinalMessage != null;
        if (firstFinalMessage.equals("series_received") && secondFinalMessage.equals("series_received")) {
            firstThread.interrupt();
            secondThread.interrupt();
            System.out.println("ControllerClientTestSender: final messages received from the receivers, series transfer completed");
        } else {
            System.out.println("ControllerClientTestSender: wrong final messages received from the receivers");
        }

        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies using UDP protocol (send three series of consecutive packets addressing different RAMP services)
     */
    private static void sendThreeSeriesOfPacketsToDifferentReceivers() {
        System.out.println("ControllerClientTestSender: waiting 10 seconds");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendFirst", 5 * 1000, 1, null);
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

        if (response != null && response.equals("ok")) {
            // Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
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
            secondServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendSecond", 5 * 1000, 1, null);
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
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.AUDIO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{secondServiceResponse.getServerNodeId()};
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
            thirdServiceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSendThird", 5 * 1000, 1, null);
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
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.VIDEO_STREAM, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 400);
            int[] destNodeIds = new int[]{thirdServiceResponse.getServerNodeId()};
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

    /**
     * Test method for Tree-based Multicast policy (send a string to multiple receivers)
     */
    private static void sendMessageToMultipleReceivers() {
        String message = "Hello, world!";

        System.out.println("ControllerClientTestSender: waiting 10 seconds");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSend", 5 * 1000, 2, null);
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
        ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
        int[] destNodeIds = new int[]{firstServiceResponse.getServerNodeId(), secondServiceResponse.getServerNodeId()};
        int[] destPorts = new int[]{firstServiceResponse.getServerPort(), secondServiceResponse.getServerPort()};
        int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);

        System.out.println("ControllerClientTestSender: sending message \""
                + message + "\" to the receivers (first nodeId: " + firstServiceResponse.getServerNodeId() + ", second nodeId: " + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
        try {
            E2EComm.sendUnicast(
                    new String[]{GeneralUtils.getLocalHost()},
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
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * Test method for Tree-based Multicast policy (send two payloads to multiple receivers
     * using separate communication and then repeat adopting the Tree-based Multicast policy)
     *
     * @throws Exception
     */
    private static void sendMultipleMessagesToMultipleReceivers() throws Exception {
        System.out.println("ControllerClientTestSender: waiting 10 seconds");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(5, "SDNControllerTestSend", 5 * 1000, 2, null);
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
                    ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 200);
                    int[] destNodeIds = new int[]{firstServiceResponse.getServerNodeId()};
                    int[] destPorts = new int[]{firstServiceResponse.getServerPort()};
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
                    ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 200);
                    int[] destNodeIds = new int[]{secondServiceResponse.getServerNodeId()};
                    int[] destPorts = new int[]{secondServiceResponse.getServerPort()};
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
            Thread.sleep(20 * 1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }

        // Set a new flowId, or use the GenericPacket.UNUSED_FIELD value to avoid RAMP-SDN features
        ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 20);
        int[] destNodeIds = new int[]{firstServiceResponse.getServerNodeId(), secondServiceResponse.getServerNodeId()};
        int[] destPorts = new int[]{firstServiceResponse.getServerPort(), secondServiceResponse.getServerPort()};
        int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts);

        byte[] messagePayload = new byte[20000000];
        System.out.println("ControllerClientTestSender: sending the third message to the receivers (first nodeId: "
                + firstServiceResponse.getServerNodeId() + ", second nodeId: " + secondServiceResponse.getServerNodeId() + "), flowId: " + flowId);
        try {
            E2EComm.sendUnicast(
                    new String[]{GeneralUtils.getLocalHost()},
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

    /**
     * As first and only argument insert the name of the network interface that will be monitored
     * by the StatsPrinter thread. It should be specified the one used by the ControllerClient.
     * <p>
     * In order to know the name of the network interface you can run the command:
     * - On Linux: ifconfig
     * - On Windows: ipconfig
     *
     * @param args name of the monitored interface
     */
    public static void main(String[] args) {

        String monitoredInterface = args[0];

        ramp = RampEntryPoint.getInstance(true, null);

        /*
         * Wait a few second to allow the node to discover neighbors, otherwise the service cannot be found
         */
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
        /*
         * Force neighbors update to make sure to know them
         */
        ramp.forceNeighborsUpdate();

        System.out.println("ControllerClientTestSender: registering shutdown hook");
        /*
         * Setup signal handling in order to always stop RAMP gracefully
         */
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

        StatsPrinter statsPrinter = new StatsPrinter("output_external.csv", monitoredInterface);
        statsPrinter.start();

        try {
            /*
             * Test method to run, match it with the one in ControllerClientTestReceiver
             */
            sendOneSeriesOfPacketsToOneReceiver(E2EComm.TCP, 1024, 50, 15);
        } catch (Exception e) {
            e.printStackTrace();
        }
        statsPrinter.stopStatsPrinter();

        controllerClient.stopClient();
        ramp.stopRamp();
    }

    /**
     * Utility to log all network traffic on a specific network interface
     */
    public static class StatsPrinter extends Thread {

        private static final int TIME_INTERVAL = 500;

        private String outputFileName;
        private String monitoredInterface;
        private boolean active;

        public StatsPrinter(String outputFileName, String monitoredInterface) {
            this.outputFileName = outputFileName;
            this.monitoredInterface = monitoredInterface;
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
            assert printWriter != null;
            printWriter.println("timestamp,throughput");

            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
            NetworkIF[] networkIFs = hardwareAbstractionLayer.getNetworkIFs();
            NetworkIF transmissionInterface = null;
            /*
             * Select the network interface to monitor
             */
            for (NetworkIF networkIF : networkIFs) {
                String test = networkIF.getName();
                if (networkIF.getName().equals(monitoredInterface)) {
                    transmissionInterface = networkIF;
                }
            }

            long startTransmittedBytes = 0;
            assert transmissionInterface != null;
            transmissionInterface.updateNetworkStats();
            startTransmittedBytes = startTransmittedBytes + transmissionInterface.getBytesSent();

            while (this.active) {
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
