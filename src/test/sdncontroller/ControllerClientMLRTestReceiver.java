package test.sdncontroller;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
import it.unibo.deis.lia.ramp.service.management.ServiceManager;

/**
 * @author Dmitrij David Padalino Montenero
 * <p>
 * Class to test the dynamic Multi Layer Routing solution
 * introduced by RAMP-MultiLane.
 * <p>
 * To be used with
 * @see ControllerClientMLRTestSender
 */
public class ControllerClientMLRTestReceiver {

    private static final int PROTOCOL = E2EComm.TCP;

    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    private static BlockingQueue<ControllerMessageMLRTestReceiver> messageQueue;

    private static String[] senderDest = null;

    private static long switchToOsRoutingLatencyThreshold = 250;

    private static boolean applicationLevelRoutingActive = true;

    private static boolean osLevelRoutingActive = false;

    private static boolean switchToOsRouting = false;

    private static int packetsReceived = 0;

    private static int senderNodeId = -1;

    private static int senderNodePort = -1;

    private static int routeId = -1;

    private static boolean transferCompleted = false;

    /**
     * Test method for RAMP Multi-Lane to show an example where a series of packets
     * is transferred by switching at runtime the routing strategy.
     * The strategies are:
     * 1) slow path using a flow-based routing
     * 2) fast path using a flow-based routing
     * 3) very fast path using OS_ROUTING
     * <p>
     * The switch between 1) and 2) is performed by the VibrationDataPlaneRule
     * The switch between 2) and 3) is performed by the receiver
     *
     * @see ControllerClientMLRTestSender#sendOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathReactiveCalculation()
     */
    public static void receiveOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathReactiveCalculation() {
        /*
         * Initialize the output file to keep track when each packet of the flow
         * is sent in order to get the total flow latency and the average packet
         * latency.
         */
        File outputFile = new File("flow_latencies_receiver.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("receivedTimestamp,flowId,routeId,seqNumber,appLevelSlowPath,appLevelFastPath,osLevelFastPath");

        /*
         * PrintWriter to be passed to receiver threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        /*
         * Initialize the messageQueue of the received messages to be processed
         * by a dedicated thread for log purposes
         */
        ControllerClientMLRTestReceiver.messageQueue = new ArrayBlockingQueue<>(30000);

        ControllerClientMLRTestReceiver.ReceivedMessageHandler receivedMessageHandler = new ControllerClientMLRTestReceiver.ReceivedMessageHandler(ControllerClientMLRTestReceiver.messageQueue, printWriter, false);
        receivedMessageHandler.start();

        /*
         * This is the thread that receives the
         * application level routing packets.
         */
        Thread applicationLevelRoutingReceiverThread = new Thread(() -> {
            /*
             * Register ApplicationLevelRoutingReceiver service.
             */
            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            assert firstServiceSocket != null;
            ServiceManager.getInstance(false).registerService("ApplicationLevelRoutingReceiver", firstServiceSocket.getLocalPort(), PROTOCOL);

            System.out.println("ControllerClientMLRTestReceiver: waiting for handshake message from the sender (port: " + firstServiceSocket.getLocalPort() + ")");

            String message = null;


            GenericPacket gp = null;
            try {
                gp = E2EComm.receive(firstServiceSocket);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            if (gp instanceof UnicastPacket) {
                UnicastPacket up = (UnicastPacket) gp;
                senderDest = E2EComm.ipReverse(up.getSource());
                Object payload = null;
                try {
                    payload = E2EComm.deserialize(up.getBytePayload());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (payload instanceof String) {
                    message = (String) payload;
                }
            }
            assert message != null;
            int senderPort = Integer.parseInt(message);

            /*
             * Store info about the sender to be used
             * in the os level routing thread.
             */
            senderNodePort = senderPort;
            senderNodeId = ((UnicastPacket) gp).getSourceNodeId();

            System.out.println("ControllerClientMLRTestReceiver: handshake message received from the sender, message: " + message + ", port: " + senderPort);

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientMLRTestReceiver: receiving application level routing packets from the sender (port: " + firstServiceSocket.getLocalPort() + ")");

            try {
                while (applicationLevelRoutingActive) {
                    gp = E2EComm.receive(firstServiceSocket);
                    ControllerClientMLRTestReceiver.messageQueue.put(new ControllerMessageMLRTestReceiver(System.currentTimeMillis(), LocalDateTime.now(), gp));

                    packetsReceived++;
                    System.out.println(packetsReceived);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("ControllerClientMLRTestReceiver: all application level routing packets received from the sender");
            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        /*
         * This is the thread that receives the
         * os level routing packets.
         */
        Thread osLevelRoutingReceiverThread = new Thread(() -> {
            try {
                ServerSocket serverSocketTCP = new ServerSocket(0, 100);
                serverSocketTCP.setReuseAddress(true);

                /*
                 * Register OsLevelRoutingReceiver service.
                 */
                ServiceManager.getInstance(false).registerService("OsLevelRoutingReceiver", serverSocketTCP.getLocalPort(), PROTOCOL);

                /*
                 * Wait until osLevelRouting active flag becomes true.
                 */
                while (!osLevelRoutingActive) {
                    /*
                     * Wait
                     */
                    Thread.sleep(10);
                }

                ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);

                long pre = System.currentTimeMillis();
                routeId = controllerClient.getRouteId(senderNodeId, senderNodePort, applicationRequirements, PathSelectionMetric.BREADTH_FIRST);
                long post = System.currentTimeMillis();

                controllerClient.log("ControllerClientMLRTestReceiver: getRouteId protocol completed in " + (post - pre) + "milliseconds");

                if (routeId != GenericPacket.UNUSED_FIELD) {
                    while (!transferCompleted) {
                        Socket clientSocket = serverSocketTCP.accept();
                        InputStream inputStream = clientSocket.getInputStream();
                        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                        Object payload;
                        while ((payload = objectInputStream.readObject()) != null) {
                            if (payload instanceof VibrationDataType) {
                                /*
                                 * We keep track only of VibrationDataType packets.
                                 * The "Transfer completed" message is not tracked.
                                 */
                                packetsReceived++;
                                System.out.println(packetsReceived);
                            }
                            ControllerClientMLRTestReceiver.messageQueue.put(new ControllerMessageMLRTestReceiver(LocalDateTime.now(), payload));
                        }
                        objectInputStream.close();
                        inputStream.close();
                        clientSocket.close();
                    }
                }

                System.out.println("ControllerClientMLRTestReceiver: all os level routing packets received from the sender");
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        applicationLevelRoutingReceiverThread.start();
        osLevelRoutingReceiverThread.start();

        try {
            applicationLevelRoutingReceiverThread.join();
            osLevelRoutingReceiverThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String finalMessage = "series_received";
        try {
            E2EComm.sendUnicast(senderDest, senderNodePort, PROTOCOL, E2EComm.serialize(finalMessage));
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("ApplicationLevelRoutingReceiver");
        ServiceManager.getInstance(false).removeService("OsLevelRoutingReceiver");

        receivedMessageHandler.stopMessageHandler();
        finalPrintWriter.close();
        printWriter.close();
    }

    public static void receiveOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathProactiveCalculation() {
        /*
         * Initialize the output file to keep track when each packet of the flow
         * is sent in order to get the total flow latency and the average packet
         * latency.
         */
        File outputFile = new File("flow_latencies_receiver.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("receivedTimestamp,flowId,routeId,seqNumber,appLevelSlowPath,appLevelFastPath,osLevelFastPath");

        /*
         * PrintWriter to be passed to receiver threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        /*
         * Initialize the messageQueue of the received messages to be processed
         * by a dedicated thread for log purposes
         */
        ControllerClientMLRTestReceiver.messageQueue = new ArrayBlockingQueue<>(30000);

        ControllerClientMLRTestReceiver.ReceivedMessageHandler receivedMessageHandler = new ControllerClientMLRTestReceiver.ReceivedMessageHandler(ControllerClientMLRTestReceiver.messageQueue, printWriter, true);
        receivedMessageHandler.start();

        /*
         * This is the thread that receives the
         * application level routing packets.
         */
        Thread applicationLevelRoutingReceiverThread = new Thread(() -> {
            /*
             * Register ApplicationLevelRoutingReceiver service.
             */
            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            assert firstServiceSocket != null;
            ServiceManager.getInstance(false).registerService("ApplicationLevelRoutingReceiver", firstServiceSocket.getLocalPort(), PROTOCOL);

            System.out.println("ControllerClientMLRTestReceiver: waiting for handshake message from the sender (port: " + firstServiceSocket.getLocalPort() + ")");

            String message = null;


            GenericPacket gp = null;
            try {
                gp = E2EComm.receive(firstServiceSocket);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            if (gp instanceof UnicastPacket) {
                UnicastPacket up = (UnicastPacket) gp;
                senderDest = E2EComm.ipReverse(up.getSource());
                Object payload = null;
                try {
                    payload = E2EComm.deserialize(up.getBytePayload());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (payload instanceof String) {
                    message = (String) payload;
                }
            }
            assert message != null;
            int senderPort = Integer.parseInt(message);


            /*
             * Store info about the sender to be used
             * in the os level routing thread.
             */
            senderNodePort = senderPort;
            senderNodeId = ((UnicastPacket) gp).getSourceNodeId();

            System.out.println("---------------------------------->  ");
            System.out.println("ControllerClientMLRTestReceiver: handshake message received from the sender, message: " + message + ", port: " + senderPort);
            System.out.println("---------------------------------->  ");

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientMLRTestReceiver: receiving application level routing packets from the sender (port: " + firstServiceSocket.getLocalPort() + ")");

            try {
                while (applicationLevelRoutingActive) {
                    System.out.println("----------------------------------> prima di gp");
                    gp = E2EComm.receive(firstServiceSocket);
                    System.out.println("----------------------------------> dopo di gp");
                    ControllerClientMLRTestReceiver.messageQueue.put(new ControllerMessageMLRTestReceiver(System.currentTimeMillis(), LocalDateTime.now(), gp));

                    packetsReceived++;
                    System.out.println(packetsReceived);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("ControllerClientMLRTestReceiver: all application level routing packets received from the sender");
            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        /*
         * This is the thread that receives the
         * os level routing packets.
         */
        Thread osLevelRoutingReceiverThread = new Thread(() -> {
            try {
                ServerSocket serverSocketTCP = new ServerSocket(0, 100);
                serverSocketTCP.setReuseAddress(true);

                /*
                 * Register OsLevelRoutingReceiver service.
                 */
                ServiceManager.getInstance(false).registerService("OsLevelRoutingReceiver", serverSocketTCP.getLocalPort(), PROTOCOL);

                /*
                 * Wait until osLevelRouting active flag becomes true.
                 */
                while (!osLevelRoutingActive) {
                    /*
                     * Wait
                     */
                    Thread.sleep(10);
                }

                ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);

                long pre = System.currentTimeMillis();
                routeId = controllerClient.getRouteId(senderNodeId, senderNodePort, applicationRequirements, PathSelectionMetric.BREADTH_FIRST);
                long post = System.currentTimeMillis();

                controllerClient.log("ControllerClientMLRTestReceiver: getRouteId protocol completed in " + (post - pre) + "milliseconds");

                if (routeId != GenericPacket.UNUSED_FIELD) {
                    while (!transferCompleted) {
                        Socket clientSocket = serverSocketTCP.accept();
                        InputStream inputStream = clientSocket.getInputStream();
                        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                        Object payload;
                        while ((payload = objectInputStream.readObject()) != null) {
                            if (payload instanceof VibrationDataType) {
                                /*
                                 * We keep track only of VibrationDataType packets.
                                 * The "Transfer completed" message is not tracked.
                                 */
                                packetsReceived++;
                                System.out.println(packetsReceived);
                            }
                            ControllerClientMLRTestReceiver.messageQueue.put(new ControllerMessageMLRTestReceiver(LocalDateTime.now(), payload));
                        }
                        objectInputStream.close();
                        inputStream.close();
                        clientSocket.close();
                    }
                }

                System.out.println("ControllerClientMLRTestReceiver: all os level routing packets received from the sender");
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        applicationLevelRoutingReceiverThread.start();
        osLevelRoutingReceiverThread.start();

        try {
            applicationLevelRoutingReceiverThread.join();
            osLevelRoutingReceiverThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String finalMessage = "series_received";
        try {
            E2EComm.sendUnicast(senderDest, senderNodePort, PROTOCOL, E2EComm.serialize(finalMessage));
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("ApplicationLevelRoutingReceiver");
        ServiceManager.getInstance(false).removeService("OsLevelRoutingReceiver");

        receivedMessageHandler.stopMessageHandler();
        finalPrintWriter.close();
        printWriter.close();
    }

    public static class ReceivedMessageHandler extends Thread {

        private final BlockingQueue<ControllerMessageMLRTestReceiver> messageQueue;

        private PrintWriter printWriter;

        private boolean active;

        private boolean useProactiveRouting;

        public ReceivedMessageHandler(BlockingQueue<ControllerMessageMLRTestReceiver> messageQueue, PrintWriter printWriter, boolean useProactiveRouting) {
            this.messageQueue = messageQueue;
            this.printWriter = printWriter;
            this.active = true;
            this.useProactiveRouting = useProactiveRouting;
        }

        public void stopMessageHandler() {
            this.active = false;
        }

        @Override
        public void run() {
            while (active) {
                try {
                    ControllerMessageMLRTestReceiver currentPacket = this.messageQueue.take();
                    LocalDateTime localDateTime = currentPacket.getReceivedTime();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    GenericPacket gp = currentPacket.getGenericPacket();
                    Object object = currentPacket.getObject();

                    if (gp != null && object == null) {
                        /*
                         * This is an application level routing packet.
                         */
                        try {
                            /*
                             * Check packet type
                             */
                            if (gp instanceof UnicastPacket) {
                                /*
                                 * Check payload
                                 */
                                UnicastPacket up = (UnicastPacket) gp;
                                Object payload = E2EComm.deserialize(up.getBytePayload());
                                if (payload instanceof VibrationDataType) {
                                    VibrationDataType currentMessage = (VibrationDataType) payload;

                                    /*
                                     * Check latency in order to switch to Os Routing
                                     */
                                    long receivedTimeMilliseconds = currentPacket.getReceivedTimeMilliseconds();
                                    long latency = receivedTimeMilliseconds - currentMessage.getSentTimestamp();


                                    if (latency > switchToOsRoutingLatencyThreshold) {
                                        if (!useProactiveRouting) {
                                            if (!osLevelRoutingActive) {
                                                /*
                                                 * When we receive the first packet traversing the
                                                 * fast app level routing fast fast we start to
                                                 * compute the os level routing path.
                                                 *
                                                 * This flag unlocks the osLevelRoutingReceiverThread.
                                                 */
                                                osLevelRoutingActive = true;
                                            }
                                        } else {
                                            if (!switchToOsRouting && routeId != -1) {
                                                switchToOsRouting = true;
                                                /*
                                                 * Inform the controller that sender must use the
                                                 * OSLevelRouting.
                                                 */
                                                long pre = System.currentTimeMillis();
                                                int result = controllerClient.sendOsRoutingUpdatePriorityRequest(routeId, true);
                                                long post = System.currentTimeMillis();
                                                controllerClient.log("ControllerClientMLRTestReceiver: os routing update priority protocol completed in " + (post - pre) + "milliseconds");
                                            }
                                        }
                                    }

                                    int seqNumber = currentMessage.getSeqNumber();
                                    int flowId = up.getFlowId();
                                    int pathLen = up.getDest().length;

                                    if (pathLen == 3) {
                                        /*
                                         * appLevelSlowPath
                                         */
                                        printWriter.println(timestamp + "," + flowId + ",," + seqNumber + "," + Arrays.toString(up.getDest()) + ",,");
                                    } else if (pathLen == 2) {
                                        if (useProactiveRouting && !osLevelRoutingActive) {
                                            /*
                                             * We start to compute os level routing path when we switch from
                                             * the app level routing slow path to the fast one.
                                             *
                                             * This flag unlocks the osLevelRoutingReceiverThread.
                                             */
                                            osLevelRoutingActive = true;
                                        }
                                        /*
                                         * appLevelFastPath
                                         */
                                        printWriter.println(timestamp + "," + flowId + ",," + seqNumber + ",," + Arrays.toString(up.getDest()) + ",");
                                    }

                                    printWriter.flush();
                                } else if (payload instanceof String) {
                                    /*
                                     * This is the final message sent by the sender
                                     * informing that from now on it will send the remaining
                                     * packets using os level routing.
                                     */
                                    String message = (String) payload;
                                    if (message.equals("switchingToOsRouting")) {
                                        /*
                                         * This flag will shut down the
                                         * applicationLevelRoutingReceiverThread.
                                         */
                                        applicationLevelRoutingActive = false;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (gp == null && object != null) {
                        /*
                         * This is an os level routing packet.
                         */
                        if (object instanceof VibrationDataType) {
                            VibrationDataType vibrationDataType = (VibrationDataType) object;
                            int seqNumber = vibrationDataType.getSeqNumber();
                            /*
                             * osLevelFastPath
                             */
                            printWriter.println(timestamp + ",," + routeId + "," + seqNumber + ",,,[192.168.3.101, 192.168.3.103]");
                            printWriter.flush();
                        } else if (object instanceof String) {
                            String message = (String) object;
                            if (message.equals("Transfer completed")) {
                                /*
                                 * This flag will shut down the
                                 * osLevelRoutingReceiverThread.
                                 */
                                transferCompleted = true;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {

        ramp = RampEntryPoint.getInstance(true, null);

        /*
         * Wait a few second to allow the node to discover neighbors
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

        System.out.println("ControllerClientMLRTestReceiver: registering shutdown hook");
        /*
         * Setup signal handling in order to always stop RAMP gracefully
         */
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ramp != null && controllerClient != null) {
                        System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
                        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
                        ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");
                        controllerClient.stopClient();
                        ramp.stopRamp();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }));

        controllerClient = ControllerClient.getInstance();

        /*
         * Test method to run, match it with the one in ControllerClientMLRTestSender
         */
        receiveOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathProactiveCalculation();

        controllerClient.stopClient();
        ramp.stopRamp();
    }
}
