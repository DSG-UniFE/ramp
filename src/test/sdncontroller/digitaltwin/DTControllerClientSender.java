package test.sdncontroller.digitaltwin;


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
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.AbstractDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VibrationDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VideoDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.InfoDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.TrafficType;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;
import it.unibo.deis.lia.ramp.util.rampUtils.RampUtilsInterface;

public class DTControllerClientSender {

    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    private static boolean switchToOsRouting = false;

    private static int destNodeId = -1;

    private static int routeId = -1;

    private static BoundReceiveSocket responseSocket = null;

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
     */

    public static void sleepByNanos(long n) {

        long millis = n / (long)(Math.pow(10, 6) * 1.0);
        int nanos = (int) (n % Math.pow(10, 6));

        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
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
//        printWriter.println("sentTimestamp,flowId,routeId,seqNumber");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        System.out.println("DTControllerClientSender: waiting 30 seconds");
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
        responseSocket = null;
        try {
            assert serviceResponse != null;
            responseSocket = E2EComm.bindPreReceive(serviceResponse.getProtocol());
        } catch (Exception e3) {
            e3.printStackTrace();
        }

        assert responseSocket != null;
        int responseSocketPort = responseSocket.getLocalPort();

        String message = "" + responseSocketPort;

        System.out.println("DTControllerClientSender: starting handshake protocol with the receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
        try {
            E2EComm.sendUnicast(
                    serviceResponse.getServerDest(),
                    serviceResponse.getServerPort(),
                    serviceResponse.getProtocol(),
                    E2EComm.serialize(message));
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        System.out.println("DTControllerClientSender: handshake message sent to the receiver");

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
//                String routeIdFromReceiver = null;
//                GenericPacket gp1 = null;
//                try {
//                    gp1 = E2EComm.receive(responseSocket);
//                    controllerClient.log("ok 1 "+gp1);
//                } catch (Exception e3) {
//                    e3.printStackTrace();
//                }
//                if (gp1 instanceof UnicastPacket) {
//                    UnicastPacket up = (UnicastPacket) gp1;
//                    Object payload = null;
//                    try {
//                        payload = E2EComm.deserialize(up.getBytePayload());
//                        controllerClient.log("ok 2 "+payload);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    if (payload instanceof String) {
//                        routeIdFromReceiver = (String) payload;
//                        routeId = Integer.parseInt(routeIdFromReceiver);
//                        controllerClient.log("ok 3 "+routeId);
//                        if(controllerClient.getRouteIdPriority(routeId)) {
//                            switchToOsRouting = true;
//                            controllerClient.log("ok 4");
//                        }
//                    }
//                }

                List<Integer> availableRouteIds = controllerClient.getAvailableRouteIds(destNodeId);

                if (availableRouteIds.size() == 1 ) {
                    routeId = availableRouteIds.get(0);
                    controllerClient.log("ok  1 availableRouteIds"+availableRouteIds);
                    controllerClient.log("ok  1 routeId"+routeId);

                    controllerClient.log("ok  1 controllerClient.getRouteIdPriority(routeId)"+controllerClient.getRouteIdPriority(routeId));



                    //if(controllerClient.getRouteIdPriority(routeId)) {
                    if(true) {
                        switchToOsRouting = true;
                        controllerClient.log("ok 2");
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        if (response != null && response.equals("ok")) {
            System.out.println("DTControllerClientSender: handshake completed");
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

            int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, PathSelectionMetric.LONGEST_PATH);
            // int flowId = controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, PathSelectionMetric.BREADTH_FIRST);

            long post = System.currentTimeMillis();

            controllerClient.log("DTControllerClientSender: getFlowId protocol completed in " + (post-pre) + "milliseconds");

            long vibrationalDataTypeId = 793107902207408161L;
            long infoDataTypeId = -1631743108532280182L;
            long videoDataTypeId = 7332943357871452826L;

            System.out.println("DTControllerClientSender: sending the series of packets to the receiver (nodeId: "
                    + serviceResponse.getServerNodeId() + "), flowId: " + flowId);

            AbstractDataType packet = null;

            RampUtilsInterface rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));
            int dataTypeProbability = -1;


            long preWhileTime = System.currentTimeMillis();
            /*
             * At the beginning we send 50 packets per second using application level routing.
             * Case A) from 1 to 150 vibrationValue = 50 (90% of the packets are dropped)
             * Case B) from 151 to 300 vibrationValue = 15 (All packets are passing from now on)
             * Case C) from 301 to 450 vibrationValue = 25 (The VibrationDataPlaneRule will select a fastest path)
             * Case D) from 450 on start to send at 150 packets per second.
             */
            float packetsPerSecond = 150;
            //long computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
            long nanosecondsToSleep = (long) Math.ceil(Math.pow(10,9) / packetsPerSecond);
            int packetPerSecondIteration = 0;

            while (!switchToOsRouting && routeId == -1) {
                try {
                    packetPerSecondIteration++;
//                    packetPerSecondIteration = packetPerSecondIteration + 0.1;

                    seqNumber++;
                    int currentSeqNumber = seqNumber;

                    long dataType = 0;

                    dataTypeProbability = rampUtils.nextRandomInt(3); //0,1,2
//                    boolean droppable = (rampUtils.nextRandomInt(2) == 1) ? true : false; //0,1
                    boolean delayable = (rampUtils.nextRandomInt(10) == 1) ? true : false; // 10%

                    boolean droppable = true;
//                    boolean delayable = true;

                    if (dataTypeProbability == 0){
                        packet = new InfoDataType();
                        dataType = infoDataTypeId;
                    }else if (dataTypeProbability == 1){
                        packet = new VideoDataType();
                        dataType = videoDataTypeId;
                    }else if (dataTypeProbability == 2){
                        packet = new VibrationDataType();
                        dataType = vibrationalDataTypeId;
                    }

                    long sendingTime = System.currentTimeMillis() - preWhileTime;

                    long turnOnLow = 650;
                    long turnOnMedium = 1150;
                    long turnOnHigh = 1650;


                    if (sendingTime > 10000 && sendingTime < 10100){
                        packetsPerSecond = turnOnLow;
                        nanosecondsToSleep = (long) Math.ceil(Math.pow(10,9) / packetsPerSecond);
                    }
                    else if (sendingTime > 20000 && sendingTime < 20100){
                        packetsPerSecond = turnOnMedium;
                        nanosecondsToSleep = (long) Math.ceil(Math.pow(10,9) / packetsPerSecond);
                    }
                    else if (sendingTime > 30000 && sendingTime < 30100){
                        packetsPerSecond = turnOnHigh;
                        nanosecondsToSleep = (long) Math.ceil(Math.pow(10,9) / packetsPerSecond);
                    }


//                    if (currentSeqNumber == 501) {
//                        packetsPerSecond = 500;
//                        nanosecondsToSleep = (long) Math.ceil(Math.pow(10,9) / packetsPerSecond);
//                    }
//                    else if (currentSeqNumber == 651) {
//                        packetsPerSecond = 150;
//                        computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
//                    }
//                    else if (currentSeqNumber == 3501) {
//                        packetsPerSecond = 300;
//                        computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
//                    }
//                    else if (currentSeqNumber == 501) {
//                        packetsPerSecond = 200;
//                        computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
//                    }

//                    if (currentSeqNumber % 100 == 0) {
//                        packetsPerSecond += 10;
//                        computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);
//                    }

                    long delay = 0;
                    if (delayable) {
//                        delay = (long) rampUtils.nextRandomInt(1000);
                        delay = 100000;
                    }
                    packet.setDelayable(delay);
                    packet.setDroppable(droppable);
                    packet.setSeqNumber(currentSeqNumber);
                    /*
                     * Payload set to 100bytes. This value simulates
                     * 50 float values of 32bit (4bytes).
                     */
                    packet.setPayloadSize(100);


//                    LocalDateTime localDateTime = LocalDateTime.now();
//                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("Application-level routing sender: sendingTime: " + sendingTime + " packetsPerSecond " + packetsPerSecond);

//                    finalPrintWriter.println(timestamp + "," + flowId + ",," + currentSeqNumber);
//                    finalPrintWriter.flush();

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
                            dataType,
                            E2EComm.serialize(packet)
                    );

                    //long sleep = (computedSleepTime * packetPerSecondIteration) - (System.currentTimeMillis() - preWhileTime);
                    //long sleep = (long) Math.min(1000, Math.ceil(computedSleepTime / Math.exp(Math.sqrt(packetPerSecondIteration))));
                    //long sleep = (long) Math.min(100, Math.ceil(computedSleepTime / Math.exp(packetPerSecondIteration)));
//                    long sleep = (long) Math.max(1000, Math.ceil(computedSleepTime / Math.exp(Math.sqrt(packetPerSecondIteration))))
//                            - (System.currentTimeMillis() - preWhileTime);
//                    long sleep = (long) Math.ceil(1000 / packetsPerSecond * packetPerSecondIteration) - (System.currentTimeMillis() - preWhileTime);
//                    long sleep = (long) (Math.ceil(computedSleepTime / packetPerSecondIteration) - (System.currentTimeMillis() - preWhileTime));
//                    long sleep = (computedSleepTime);
//                    if (sleep > 0) {
//                        Thread.sleep(sleep);
//                    }

                    sleepByNanos(nanosecondsToSleep);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("DTControllerClientSender: switching to OS_Routing");

            controllerClient.log("DTControllerClientSender: switching to OS_Routing, sn= "+seqNumber);

            message = "switchingToOsRouting";
            System.out.println("DTControllerClientSender: sending switch message to receiver (nodeId: " + serviceResponse.getServerNodeId() + ")");
            try {
                packet.setSentTimestamp(System.currentTimeMillis());
                E2EComm.sendUnicast(
                        serviceResponse.getServerDest(),
                        serviceResponse.getServerPort(),
                        serviceResponse.getProtocol(),
                        E2EComm.serialize(message));
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            System.out.println("DTControllerClientSender: switch message sent to the receiver");

            /*
             * Close the osRouteCheckThread.
             */
//            try {
//                osRouteCheckerThread.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

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
                long millisToSleep = (long) Math.ceil(1000 / packetsPerSecond);
                packetPerSecondIteration = 0;

                long preForTime = System.currentTimeMillis();

                for (int i = 0; i < 3000; i++) {
                    packetPerSecondIteration++;

                    dataTypeProbability = rampUtils.nextRandomInt(3); //0,1,2

                    seqNumber++;
                    if (dataTypeProbability == 0){
                        packet = new InfoDataType();

                    }else if (dataTypeProbability == 1){
                        packet = new VideoDataType();

                    }else if (dataTypeProbability == 2){
                        packet = new VibrationDataType();
                    }
                    /*
                     * Payload set to 200bytes. This value simulates
                     * 50 float values of 32bit (4bytes).
                     */
                    packet.setSeqNumber(seqNumber);
                    /*
                     * Payload set to 200bytes. This value simulates
                     * 50 float values of 32bit (4bytes).
                     */
                    packet.setPayloadSize(200);

                    LocalDateTime localDateTime = LocalDateTime.now();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    System.out.println("OS-level routing sender: seqNumber: " + seqNumber);

                    finalPrintWriter.println(timestamp + ",," + routeId + "," + seqNumber);
                    finalPrintWriter.flush();

                    packet.setSentTimestamp(System.currentTimeMillis());

                    objectOutputStream.writeObject(packet);

                    long sleep = (long) ((millisToSleep * packetPerSecondIteration) - (System.currentTimeMillis() - preForTime));
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
                System.out.println("DTControllerClientSender: final message received from the receiver, series transfer completed");
            } else {
                System.out.println("DTControllerClientSender: wrong final message received from the receiver");
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

        System.out.println("DTControllerClientSender: registering shutdown hook");
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
