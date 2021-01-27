package test.sdncontroller;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Vector;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VibrationDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.TrafficType;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * Class to test the dynamic Multi Layer Routing solution
 * introduced by RAMP-MultiLane.
 *
 * To be used with
 * @see ControllerClientMLRTestReceiver
 */
public class ControllerClientMLRTestSender {

    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    private static boolean switchToOsRouting = false;

    private static int destNodeId = -1;

    private static int routeId = -1;

    /**
     * Test method for RAMP Multi-Lane to show an example where a series of packets
     * is transferred by switching at runtime the routing strategy.
     * The strategies are:
     * 1) slow path using a flow-based routing
     * 2) fast path using a flow-based routing
     * 3) very fast path using OS_ROUTING
     *
     * The switch between 1) and 2) is performed by the VibrationDataPlaneRule
     * The switch between 2) and 3) is performed by the receiver
     *
     * @see ControllerClientMLRTestReceiver#receiveOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathReactiveCalculation()
     */
    public static void sendOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathReactiveCalculation() {
        int seqNumber = 0;

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
        printWriter.println("sentTimestamp,flowId,routeId,seqNumber");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("ControllerClientMLRTestSender: waiting 30 seconds");
        try {
            Thread.sleep(30 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
         * Discover the receiver service responsible to handle
         * the application-level routing communication.
         */
        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(20, "ApplicationLevelRoutingReceiver", 60 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Retrieve the service response.
         */
        ServiceResponse serviceResponse = null;
        assert serviceResponses != null;
        if (serviceResponses.size() > 0) {
            serviceResponse = serviceResponses.get(0);
        }

        /*
         * Discover the receiver service responsible to handle
         * the os-level routing communication.
         */
        Vector<ServiceResponse> secondServiceResponses = null;
        try {
            secondServiceResponses = ServiceDiscovery.findServices(20, "OsLevelRoutingReceiver", 60 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceResponse secondServiceResponse = null;
        assert secondServiceResponses != null;
        if (secondServiceResponses.size() > 0)
            secondServiceResponse = secondServiceResponses.get(0);

        /*
         * Open the socket to establish if the
         * application-level routing receiver is ready to
         * receive packets.
         *
         * This socket will be also used at the end
         * to receive the final message stating
         * that MLR transfer has been completed
         * and correctly received.
         */
        BoundReceiveSocket responseSocket = null;
        try {
            assert serviceResponse != null;
            responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }

        assert responseSocket != null;
        int responseSocketPort = responseSocket.getLocalPort();

        String message = "" + responseSocketPort;

        System.out.println("ControllerClientMLRTestSender: starting handshake protocol with the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
        try {
            E2EComm.sendUnicast(
                    serviceResponse.getServerDest(),
                    serviceResponse.getServerPort(),
                    serviceResponse.getProtocol(),
                    E2EComm.serialize(message));
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        System.out.println("ControllerClientMLRTestSender: handshake message sent to the receiver");

        /*
         * Listen for message from the receiver
         * to complete the handshake protocol.
         */
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
         * Thread in charge to check if an os level
         * routing path is available toward the destination.
         * If so it enables the code block responsible
         * to send the os routing packets.
         */
        Thread osRouteCheckerThread = new Thread(() -> {
            while (!switchToOsRouting) {
                /*
                 * Remember that the getRouteId function called by the receiver
                 * creates an os level bidirectional path that is pushed to this node.
                 * When this thread calls
                 * controllerClient.getAvailableRouteIds(destNodeId)
                 * it checks the existence of a os level route towards the receiver.
                 */
                List<Integer> availableRouteIds = controllerClient.getAvailableRouteIds(destNodeId);
                if (availableRouteIds.size() == 1) {
                    routeId = availableRouteIds.get(0);
                    switchToOsRouting = true;
                }
            }
        });

        if (response != null && response.equals("ok")) {
            /*
             * Fill the info needed by the
             * osRouteCheckerThread and start it.
             */
            destNodeId = ((UnicastPacket) gp).getSourceNodeId();
            osRouteCheckerThread.start();

            /*
             * Asking a new flowId to be associated
             * to the longest path currently available
             * in the topology.
             */
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
            int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
            int[] destPorts = new int[0];

            long pre = System.currentTimeMillis();
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, PathSelectionMetric.LONGEST_PATH);
            long post = System.currentTimeMillis();

            controllerClient.log("ControllerClientMLRTestSender: getFlowId protocol completed in " + (post-pre) + "milliseconds");

            long vibrationDataTypeId = 793107902207408161L;

            System.out.println("ControllerClientMLRTestSender: sending the series of packets to the receiver (nodeId: "
                    + serviceResponse.getServerNodeId() + "), flowId: " + flowId);

            VibrationDataType packet = new VibrationDataType();
            packet.setVibrationValue(5);
            /*
             * Payload set to 200bytes. This value simulates
             * 50 float values of 32bit (4bytes).
             */
            packet.setPayloadSize(200);

            long preWhileTime = System.currentTimeMillis();
            /*
             * At the beginning we send 50 packets per second using application level routing.
             * Case A) from 1 to 150 vibrationValue = 50 (90% of the packets are dropped)
             * Case B) from 151 to 300 vibrationValue = 15 (All packets are passing from now on)
             * Case C) from 301 to 450 vibrationValue = 25 (The VibrationDataPlaneRule will select a fastest path)
             * Case D) from 450 on start to send at 150 packets per second.
             */
            float packetsPerSecond = 50;
            long computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
            int packetPerSecondIteration = 0;

            while (!switchToOsRouting) {
                try {
                    packetPerSecondIteration++;
                    seqNumber++;
                    int currentSeqNumber = seqNumber;
                    packet.setSeqNumber(currentSeqNumber);

                    if (currentSeqNumber == 151) {
                        packet.setVibrationValue(15);
                    } else if (currentSeqNumber == 301) {
                        packet.setVibrationValue(25);
                    } else if (currentSeqNumber == 451) {
                        preWhileTime = System.currentTimeMillis();
                        packetsPerSecond = 150;
                        packetPerSecondIteration = 1;
                        computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
                    }

                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("Application-level routing sender: seqNumber: " + currentSeqNumber);

                    finalPrintWriter.println(timestamp + "," + flowId + ",," + currentSeqNumber);
                    finalPrintWriter.flush();

                    packet.setSentTimestamp(System.currentTimeMillis());

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
                            vibrationDataTypeId,
                            E2EComm.serialize(packet));

                    long sleep = (computedSleepTime * packetPerSecondIteration) - (System.currentTimeMillis() - preWhileTime);
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("ControllerClientMLRTestSender: switching to OS_Routing");

            message = "switchingToOsRouting";
            System.out.println("ControllerClientMLRTestSender: sending switch message to receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
            try {
                E2EComm.sendUnicast(
                        serviceResponse.getServerDest(),
                        serviceResponse.getServerPort(),
                        serviceResponse.getProtocol(),
                        E2EComm.serialize(message));
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            System.out.println("ControllerClientMLRTestSender: switch message sent to the receiver");

            /*
             * Close the osRouteCheckThread.
             */
            try {
                osRouteCheckerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            /*
             * Start sending the remaining packets using the os routing
             * as soon as the os level route is available.
             *
             * Case E) As soon as the os level route is available
             */
            try {
                InetAddress sourceIpAddress = InetAddress.getByName(controllerClient.getRouteIdSourceIpAddress(routeId));
                String destinationIP = controllerClient.getRouteIdDestinationIpAddress(routeId);
                assert secondServiceResponse != null;
                int port = secondServiceResponse.getServerPort();

                Socket socket = new Socket(InetAddress.getByName(destinationIP), port, sourceIpAddress, 0);
                OutputStream outputStream = socket.getOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);

                packetsPerSecond = 250;
                computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
                packetPerSecondIteration = 0;

                long preForTime = System.currentTimeMillis();

                for (int i = 0; i < 750; i++) {
                    packetPerSecondIteration++;
                    seqNumber++;
                    packet = new VibrationDataType();
                    packet.setVibrationValue(50);
                    /*
                     * Payload set to 200bytes. This value simulates
                     * 50 float values of 32bit (4bytes).
                     */
                    packet.setPayloadSize(200);
                    packet.setSeqNumber(seqNumber);

                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("OS-level routing sender: seqNumber: " + seqNumber);

                    finalPrintWriter.println(timestamp + ",," + routeId + "," + seqNumber);
                    finalPrintWriter.flush();

                    packet.setSentTimestamp(System.currentTimeMillis());

                    objectOutputStream.writeObject(packet);

                    long sleep = (computedSleepTime * packetPerSecondIteration) - (System.currentTimeMillis() - preForTime);
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                }

                message = "Transfer completed";
                objectOutputStream.writeObject(message);

                objectOutputStream.close();
                outputStream.close();
                socket.close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

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

            assert finalMessage != null;
            if (finalMessage.equals("series_received")) {
                System.out.println("ControllerClientMLRTestSender: final message received from the receiver, series transfer completed");
            } else {
                System.out.println("ControllerClientMLRTestSender: wrong final message received from the receiver");
            }
        }

        finalPrintWriter.close();
        printWriter.close();
    }

    public static void sendOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathProactiveCalculation() {
        int seqNumber = 0;

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
        printWriter.println("sentTimestamp,flowId,routeId,seqNumber");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("ControllerClientMLRTestSender: waiting 30 seconds");
        try {
            Thread.sleep(30 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
         * Discover the receiver service responsible to handle
         * the application-level routing communication.
         */
        Vector<ServiceResponse> serviceResponses = null;
        try {
            serviceResponses = ServiceDiscovery.findServices(20, "ApplicationLevelRoutingReceiver", 60 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Retrieve the service response.
         */
        ServiceResponse serviceResponse = null;
        assert serviceResponses != null;
        if (serviceResponses.size() > 0) {
            serviceResponse = serviceResponses.get(0);
        }

        /*
         * Discover the receiver service responsible to handle
         * the os-level routing communication.
         */
        Vector<ServiceResponse> secondServiceResponses = null;
        try {
            secondServiceResponses = ServiceDiscovery.findServices(20, "OsLevelRoutingReceiver", 60 * 1000, 1, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceResponse secondServiceResponse = null;
        assert secondServiceResponses != null;
        if (secondServiceResponses.size() > 0)
            secondServiceResponse = secondServiceResponses.get(0);

        /*
         * Open the socket to establish if the
         * application-level routing receiver is ready to
         * receive packets.
         *
         * This socket will be also used at the end
         * to receive the final message stating
         * that MLR transfer has been completed
         * and correctly received.
         */
        BoundReceiveSocket responseSocket = null;
        try {
            assert serviceResponse != null;
            responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }

        assert responseSocket != null;
        int responseSocketPort = responseSocket.getLocalPort();

        String message = "" + responseSocketPort;

        System.out.println("ControllerClientMLRTestSender: starting handshake protocol with the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
        try {
            E2EComm.sendUnicast(
                    serviceResponse.getServerDest(),
                    serviceResponse.getServerPort(),
                    serviceResponse.getProtocol(),
                    E2EComm.serialize(message));
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        System.out.println("ControllerClientMLRTestSender: handshake message sent to the receiver");

        /*
         * Listen for message from the receiver
         * to complete the handshake protocol.
         */
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
         * Thread in charge to check if an os level
         * routing path is available toward the destination.
         * If so it enables the code block responsible
         * to send the os routing packets.
         */
        Thread osRouteCheckerThread = new Thread(() -> {
            while (!switchToOsRouting) {
                /*
                 * Remember that the getRouteId function called by the receiver
                 * creates an os level bidirectional path that is pushed to this node.
                 * When this thread calls
                 * controllerClient.getAvailableRouteIds(destNodeId)
                 * it checks the existence of a os level route towards the receiver.
                 */
                List<Integer> availableRouteIds = controllerClient.getAvailableRouteIds(destNodeId);

                if (availableRouteIds.size() == 1 ) {
                    routeId = availableRouteIds.get(0);
                    if(controllerClient.getRouteIdPriority(routeId)) {
                        switchToOsRouting = true;
                    }
                }
            }
        });

        if (response != null && response.equals("ok")) {
            /*
             * Fill the info needed by the
             * osRouteCheckerThread and start it.
             */
            destNodeId = ((UnicastPacket) gp).getSourceNodeId();
            osRouteCheckerThread.start();

            /*
             * Asking a new flowId to be associated
             * to the longest path currently available
             * in the topology.
             */
            //ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
            ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.DEFAULT, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
            int[] destNodeIds = new int[]{serviceResponse.getServerNodeId()};
            int[] destPorts = new int[0];

            long pre = System.currentTimeMillis();
            //int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, PathSelectionMetric.LONGEST_PATH);
            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, PathSelectionMetric.BREADTH_FIRST);
            long post = System.currentTimeMillis();

            controllerClient.log("ControllerClientMLRTestSender: getFlowId protocol completed in " + (post-pre) + "milliseconds");

            long vibrationDataTypeId = 793107902207408161L;

            System.out.println("ControllerClientMLRTestSender: sending the series of packets to the receiver (nodeId: "
                    + serviceResponse.getServerNodeId() + "), flowId: " + flowId);

            VibrationDataType packet = new VibrationDataType();
            packet.setVibrationValue(5);
            /*
             * Payload set to 200bytes. This value simulates
             * 50 float values of 32bit (4bytes).
             */
            packet.setPayloadSize(200);

            long preWhileTime = System.currentTimeMillis();
            /*
             * At the beginning we send 50 packets per second using application level routing.
             * Case A) from 1 to 150 vibrationValue = 50 (90% of the packets are dropped)
             * Case B) from 151 to 300 vibrationValue = 15 (All packets are passing from now on)
             * Case C) from 301 to 450 vibrationValue = 25 (The VibrationDataPlaneRule will select a fastest path)
             * Case D) from 450 on start to send at 150 packets per second.
             */
            float packetsPerSecond = 50;
            long computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
            int packetPerSecondIteration = 0;

            while (!switchToOsRouting) {
                try {
                    packetPerSecondIteration++;
                    seqNumber++;
                    int currentSeqNumber = seqNumber;
                    packet.setSeqNumber(currentSeqNumber);

                    if (currentSeqNumber == 151) {
                        packet.setVibrationValue(15);
                    } else if (currentSeqNumber == 301) {
                        packet.setVibrationValue(25);
                    } else if (currentSeqNumber == 451) {
                        preWhileTime = System.currentTimeMillis();
                        packetsPerSecond = 150;
                        packetPerSecondIteration = 1;
                        computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
                    }

                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("Application-level routing sender: seqNumber: " + currentSeqNumber);

                    finalPrintWriter.println(timestamp + "," + flowId + ",," + currentSeqNumber);
                    finalPrintWriter.flush();

                    packet.setSentTimestamp(System.currentTimeMillis());

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
                            vibrationDataTypeId,
                            E2EComm.serialize(packet));

                    long sleep = (computedSleepTime * packetPerSecondIteration) - (System.currentTimeMillis() - preWhileTime);
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("ControllerClientMLRTestSender: switching to OS_Routing");

            message = "switchingToOsRouting";
            System.out.println("ControllerClientMLRTestSender: sending switch message to receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
            try {
                E2EComm.sendUnicast(
                        serviceResponse.getServerDest(),
                        serviceResponse.getServerPort(),
                        serviceResponse.getProtocol(),
                        E2EComm.serialize(message));
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            System.out.println("ControllerClientMLRTestSender: switch message sent to the receiver");

            /*
             * Close the osRouteCheckThread.
             */
            try {
                osRouteCheckerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            /*
             * Start sending the remaining packets using the os routing
             * as soon as the os level route is available.
             *
             * Case E) As soon as the os level route is available
             */
            try {
                InetAddress sourceIpAddress = InetAddress.getByName(controllerClient.getRouteIdSourceIpAddress(routeId));
                String destinationIP = controllerClient.getRouteIdDestinationIpAddress(routeId);
                assert secondServiceResponse != null;
                int port = secondServiceResponse.getServerPort();

                Socket socket = new Socket(InetAddress.getByName(destinationIP), port, sourceIpAddress, 0);
                OutputStream outputStream = socket.getOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);

                packetsPerSecond = 250;
                computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
                packetPerSecondIteration = 0;

                long preForTime = System.currentTimeMillis();

                for (int i = 0; i < 750; i++) {
                    packetPerSecondIteration++;
                    seqNumber++;
                    packet = new VibrationDataType();
                    packet.setVibrationValue(50);
                    /*
                     * Payload set to 200bytes. This value simulates
                     * 50 float values of 32bit (4bytes).
                     */
                    packet.setPayloadSize(200);
                    packet.setSeqNumber(seqNumber);

                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("OS-level routing sender: seqNumber: " + seqNumber);

                    finalPrintWriter.println(timestamp + ",," + routeId + "," + seqNumber);
                    finalPrintWriter.flush();

                    packet.setSentTimestamp(System.currentTimeMillis());

                    objectOutputStream.writeObject(packet);

                    long sleep = (computedSleepTime * packetPerSecondIteration) - (System.currentTimeMillis() - preForTime);
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                }

                message = "Transfer completed";
                objectOutputStream.writeObject(message);

                objectOutputStream.close();
                outputStream.close();
                socket.close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

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

            assert finalMessage != null;
            if (finalMessage.equals("series_received")) {
                System.out.println("ControllerClientMLRTestSender: final message received from the receiver, series transfer completed");
            } else {
                System.out.println("ControllerClientMLRTestSender: wrong final message received from the receiver");
            }
        }

        finalPrintWriter.close();
        printWriter.close();
    }

    public static void main(String[] args) {
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

        System.out.println("ControllerClientMLRTestSender: registering shutdown hook");
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

        try {
            /*
             * Test method to run, match it with the one in ControllerClientMLRTestReceiver
             */
            sendOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathProactiveCalculation();
        } catch (Exception e) {
            e.printStackTrace();
        }

        controllerClient.stopClient();
        ramp.stopRamp();
    }
}
