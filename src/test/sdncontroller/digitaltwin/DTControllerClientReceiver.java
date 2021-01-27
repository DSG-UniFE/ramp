package test.sdncontroller.digitaltwin;

import java.io.*;
import java.lang.reflect.Method;
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
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManagerInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.TrafficType;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;

import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.AbstractDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VibrationDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.InfoDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VideoDataType;


import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;
import it.unibo.deis.lia.ramp.util.rampUtils.RampUtilsInterface;
import test.sdncontroller.ControllerMessageMLRTestReceiver;

import static it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage.trafficCongestionLevel.*;
import static test.sdncontroller.digitaltwin.DTConstants.*;

public class DTControllerClientReceiver {
    private static final int PROTOCOL = E2EComm.TCP;

    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    private static BlockingQueue<ControllerMessageMLRTestReceiver> messageQueue;

    private static String[] senderDest = null;

    private static boolean applicationLevelRoutingActive = true;

    private static boolean osLevelRoutingActive = false;

    private static boolean switchToOsRouting = false;

    private static int packetsReceived = 0;

    private static int senderNodeId = -1;

    private static int senderNodePort = -1;

    private static int routeId = -1;

    private static boolean lowCongestionRuleDetected = false;
    private static boolean mediumCongestionRuleDetected= false;
    private static boolean highCongestionRuleDetected= false;

    private static boolean transferCompleted = false;

    private static RampUtilsInterface rampUtils;
    private static DataTypesManagerInterface dataTypesManager;

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
     */

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
        /*
         * PrintWriter to be passed to receiver threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        /*
         * Initialize the messageQueue of the received messages to be processed
         * by a dedicated thread for log purposes
         */
        //DTControllerClientReceiver.messageQueue = new ArrayBlockingQueue<>(30000);
        DTControllerClientReceiver.messageQueue = new ArrayBlockingQueue<>(1000000);

        DTControllerClientReceiver.ReceivedMessageHandler receivedMessageHandler = new DTControllerClientReceiver.ReceivedMessageHandler(DTControllerClientReceiver.messageQueue, finalPrintWriter, true);
        receivedMessageHandler.start();


        /*
         * This is the thread that checks latencies
         *
         */
        Thread checkLatencies = new Thread(() -> {
            boolean lowCongestionRuleRequired = false;
            boolean mediumCongestionRuleRequired = false;
            boolean highCongestionRuleRequired = false;
            try {
                while(!(lowCongestionRuleDetected && mediumCongestionRuleDetected && highCongestionRuleDetected)){

                    if(lowCongestionRuleDetected && !lowCongestionRuleRequired){
                        controllerClient.notifyControllerAboutTrafficState(LOW_CONGESTION);
                        lowCongestionRuleRequired = true;
                        finalPrintWriter.println("-------------------------------> LOW_CONGESTION = true;");
                        System.out.println("-------------------------------> LOW_CONGESTION = true;");
                        System.out.println("-------------------------------> LOW_CONGESTION = true;");
                        System.out.println("-------------------------------> LOW_CONGESTION = true;");
                        System.out.println("-------------------------------> LOW_CONGESTION = true;");
                    }

                    if(mediumCongestionRuleDetected && !mediumCongestionRuleRequired){
                        controllerClient.notifyControllerAboutTrafficState(MEDIUM_CONGESTION);
                        mediumCongestionRuleRequired = true;
                        finalPrintWriter.println("-------------------------------> MEDIUM_CONGESTION = true;");
                        System.out.println("-------------------------------> MEDIUM_CONGESTION = true;");
                        System.out.println("-------------------------------> MEDIUM_CONGESTION = true;");
                        System.out.println("-------------------------------> MEDIUM_CONGESTION = true;");
                        System.out.println("-------------------------------> MEDIUM_CONGESTION = true;");
                    }

                    if(highCongestionRuleDetected && !highCongestionRuleRequired){
                        highCongestionRuleRequired = true;
                        osLevelRoutingActive = true;
                        controllerClient.notifyControllerAboutTrafficState(HIGH_CONGESTION);
                        finalPrintWriter.println("-------------------------------> HIGH_CONGESTION (OS) = true;");
                        System.out.println("-------------------------------> HIGH_CONGESTION = true;");
                        System.out.println("-------------------------------> HIGH_CONGESTION = true;");
                        System.out.println("-------------------------------> HIGH_CONGESTION = true;");
                        System.out.println("-------------------------------> HIGH_CONGESTION = true;");
                    }

//                    try {
//                        Thread.sleep(5);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }

                System.out.println("DTControllerClientReceiver: checkLatencies done");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

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
            long lastLatency = 0;
            int latencyTenCheck = 0;
            try {
                while (applicationLevelRoutingActive) {
                    gp = E2EComm.receive(firstServiceSocket);
                    long receivedTimeMilliseconds = System.currentTimeMillis();
                    boolean delayed = false;

                    /*
                     * Check latency in order to switch to Os Routing
                     */

                    if (gp instanceof UnicastPacket) {
                        UnicastPacket up = (UnicastPacket) gp;
                        Object payload = null;

                        try{
                            payload = E2EComm.deserialize(up.getBytePayload());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        assert payload != null;
                        String dataType = payload.getClass().getSimpleName();
                        Class cls = dataTypesManager.getDataTypeClassObject(dataType);


                        //invoking method getDelayable
                        Method method = cls.getMethod("getDelayable");
                        method.setAccessible(true);
                        long maxDelay = (long) method.invoke(payload);

                        if (payload instanceof AbstractDataType) {
                            AbstractDataType currentAPPMessage = (AbstractDataType) payload;
                            long sentTimeStamp = currentAPPMessage.getSentTimestamp();
                            long latency = receivedTimeMilliseconds - sentTimeStamp;

                            delayed = (maxDelay > 0) ? true : false;

                            boolean shouldConsider = (
                                    maxDelay == 0
                                            && latency > lowNetworkTrafficCongestionThreshold
                                            && Math.abs(latency - lastLatency) < 1000    // ----> avoid outliers
                            );
//                            lastLatency = latency;

                            if (shouldConsider) {

                                if(!lowCongestionRuleDetected){
                                    //require low congestion rule application
//                                    controllerClient.notifyControllerAboutTrafficState(LOW_CONGESTION);
//                                    finalPrintWriter.println("-------------------------------> LOW_CONGESTION = true;");
                                    latencyTenCheck++;
                                    if (latencyTenCheck == 20){
//                                    if (true){
                                        lowCongestionRuleDetected = true;
                                        latencyTenCheck= 0;
                                    }
                                }else{
                                    if (latency > mediumNetworkTrafficCongestionThreshold) {
                                        if(!mediumCongestionRuleDetected){
                                            //require low congestion rule application
//                                            controllerClient.notifyControllerAboutTrafficState(MEDIUM_CONGESTION);
//                                            finalPrintWriter.println("-------------------------------> MEDIUM_CONGESTION = true;");
                                            latencyTenCheck++;
                                            if (latencyTenCheck == 20){
                                                mediumCongestionRuleDetected = true;
                                                latencyTenCheck= 0;
                                            }
                                        }else{
                                            if (!osLevelRoutingActive && latency > highNetworkTrafficCongestionThreshold) {
                                                /*
                                                 * This flag unlocks the osLevelRoutingReceiverThread.
                                                 */
//                                                finalPrintWriter.println("-------------------------------> osLevelRoutingActive = true;");
                                                latencyTenCheck++;
                                                if (latencyTenCheck == 20){
                                                    highCongestionRuleDetected = true;
                                                    latencyTenCheck= 0;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DTControllerClientReceiver.messageQueue.put(
                            new ControllerMessageMLRTestReceiver(receivedTimeMilliseconds, LocalDateTime.now(), gp, delayed)
                    );

                    packetsReceived++;
                    System.out.println("Application level pkt: "+packetsReceived);





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


                //Thread.sleep(4000);
                System.out.println("----------------------------------->");
                System.out.println("----------------------------------->");
                System.out.println("----------------------------------->");
                System.out.println("----------------------------------->");
                System.out.println("----------------------------------->");
                System.out.println("----------------------------------->OS routing");
                System.out.println("----------------------------------->");



                ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.FILE_TRANSFER, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);
                //ApplicationRequirements applicationRequirements = new ApplicationRequirements(TrafficType.DEFAULT, GenericPacket.UNUSED_FIELD, GenericPacket.UNUSED_FIELD, 0, 36000);

                long pre = System.currentTimeMillis();
                routeId = controllerClient.getRouteId(senderNodeId, senderNodePort, applicationRequirements, PathSelectionMetric.BREADTH_FIRST);
                long post = System.currentTimeMillis();

                controllerClient.log("DTControllerClientReceiver: getRouteId protocol completed in " + (post - pre) + "milliseconds");

                if (routeId != GenericPacket.UNUSED_FIELD) {
                    controllerClient.log("DTControllerClientReceiver: routeId is not unused field: "+routeId);
                    System.out.println("DTControllerClientransfer is not completedtReceiver: partially ok");

                    while (!transferCompleted) {
                        controllerClient.log("DTControllerClientReceiver: transfer is not completed");
                        Socket clientSocket = serverSocketTCP.accept();
                        InputStream inputStream = clientSocket.getInputStream();
                        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                        Object payload;
                        while ((payload = objectInputStream.readObject()) != null) {
                            controllerClient.log("DTControllerClientReceiver: received 1");
                            //if (payload instanceof VibrationDataType) {
                            if (payload instanceof AbstractDataType) {
                                /*
                                 * We keep track only of VibrationDataType packets.
                                 * The "Transfer completed" message is not tracked.
                                 */
                                packetsReceived++;
                                System.out.println(packetsReceived);
                                controllerClient.log("DTControllerClientReceiver: received 2, packetsReceived: "+packetsReceived);
                            }
                            System.out.println("DTControllerClientReceiver: receiving pkt: "+packetsReceived);
                            DTControllerClientReceiver.messageQueue.put(new ControllerMessageMLRTestReceiver(System.currentTimeMillis(), LocalDateTime.now(), payload, false));
                        }
                        objectInputStream.close();
                        inputStream.close();
                        clientSocket.close();
                    }
                }else{
                    System.out.println("DTControllerClientReceiver: very bad");
                    controllerClient.log("DTControllerClientReceiver: routeId : "+routeId);
                }

                System.out.println("DTControllerClientReceiver: all os level routing packets received from the sender");
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        checkLatencies.start();
        applicationLevelRoutingReceiverThread.start();
        osLevelRoutingReceiverThread.start();

        try {
            checkLatencies.join();
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

        private boolean firstPacket;
        private long startingTime;

        private boolean useProactiveRouting;

        public ReceivedMessageHandler(BlockingQueue<ControllerMessageMLRTestReceiver> messageQueue, PrintWriter printWriter, boolean useProactiveRouting) {
            this.messageQueue = messageQueue;
            this.printWriter = printWriter;
            this.active = true;
            this.useProactiveRouting = useProactiveRouting;
            this.firstPacket = true;
            this.startingTime = 0;

        }

        public void stopMessageHandler() {
            this.active = false;
        }

        @Override
        public void run() {
            printWriter.println(
                    "SimulationTimeStamp," +
                    "Received time, " +
                    "Sent time, " +
                    "FlowID, " +
                    "RouteID, " +
                    "SeqNumber, " +
                    "Dest, " +
                    "level, " +
                    "PktType, " +
                    "All latencies, " +
                    "Info Lat, " +
                    "Video Lat, " +
                    "Vibr Lat")
            ;
            while (active) {
                try {
                    ControllerMessageMLRTestReceiver currentPacket = this.messageQueue.take();
//                    LocalDateTime localDateTime = currentPacket.getReceivedTime();
//                    String receivedTimeStamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    GenericPacket gp = currentPacket.getGenericPacket();
                    Object object = currentPacket.getObject();

                    boolean hasBeenDelayed = currentPacket.isDelayed();

                    long receivedTimeMilliseconds = currentPacket.getReceivedTimeMilliseconds();
//                    if(firstPacket){
////                        this.startingTime = currentPacket.getReceivedTimeMilliseconds();     ------> based on received time
//                        firstPacket = false;
//                    }


                    String receivedTimeStamp = ""+(receivedTimeMilliseconds-this.startingTime);


                    if (gp != null && object == null) {
                        /*
                         * This is an application level routing packet.
                         */
                        if (gp instanceof UnicastPacket) {

                            /*
                             * Check payload
                             */
                            UnicastPacket up = (UnicastPacket) gp;
                            Object payload = null;
                            try{
                                payload = E2EComm.deserialize(up.getBytePayload());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }


                            if (payload instanceof AbstractDataType) {
                                //and applicationLevelRoutingActive
                                AbstractDataType currentAPPMessage = (AbstractDataType) payload;
                                int seqNumber = currentAPPMessage.getSeqNumber();
                                long sentTimeStamp = currentAPPMessage.getSentTimestamp();
                                long latency = receivedTimeMilliseconds - sentTimeStamp;
                                int flowId = up.getFlowId();
                                String destArrayStringNoCommas = Arrays.toString(up.getDest()).replace(",","-");

                                if(firstPacket){
                                    this.startingTime = sentTimeStamp;   //  -----> based on sent time
                                    firstPacket = false;
                                }

                                long simulationTimeStamp = sentTimeStamp - this.startingTime; //  -----> based on sent time


                                if (payload instanceof InfoDataType) {
                                    printWriter.println(
                                            simulationTimeStamp + "," +
                                            receivedTimeStamp + "," +
                                            sentTimeStamp + "," +
                                            flowId + "," +
                                            routeId + "," +
                                            seqNumber + "," +
                                            destArrayStringNoCommas + "," +
                                            "application" + "," +
                                            "info" + "," +
                                            latency + "," +     // all
                                            latency + "," +          //info
                                            "" + "," +          //video
                                            "" + "," +               //vibrational
                                            ((!hasBeenDelayed) ? "x" : "")     //delayed or not
                                    );
                                }else if (payload instanceof VideoDataType) {
                                    printWriter.println(
                                            simulationTimeStamp + "," +
                                            receivedTimeStamp + "," +
                                            sentTimeStamp + "," +
                                            flowId + "," +
                                            routeId + "," +
                                            seqNumber + "," +
                                            destArrayStringNoCommas + "," +
                                            "application" + "," +
                                            "video" + "," +
                                            latency + "," +     // all
                                            "" + "," +          //info
                                            latency + "," +          //video
                                            "" + "," +               //vibrational
                                            ((!hasBeenDelayed) ? "x" : "")     //delayed or not
                                    );

                                } else if (payload instanceof VibrationDataType) {
                                    printWriter.println(
                                            simulationTimeStamp + "," +
                                            receivedTimeStamp + "," +
                                            sentTimeStamp + "," +
                                            flowId + "," +
                                            routeId + "," +
                                            seqNumber + "," +
                                            destArrayStringNoCommas + "," +
                                            "application" + "," +
                                            "vibrational" + "," +
                                            latency + "," +     // all
                                            "" + "," +          //info
                                            "" + "," +          //video
                                            latency + "," +              //vibrational
                                            ((!hasBeenDelayed) ? "x" : "")     //delayed or not
                                    );

                                }
                                printWriter.flush();

                            }else if (payload instanceof String) {
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
                    } else if (gp == null && object != null) {
                        /*
                         * This is an os level routing packet.
                         */
                        if (object instanceof AbstractDataType) {
                            AbstractDataType currentOSMessage = (AbstractDataType) object;
                            int seqNumber = currentOSMessage.getSeqNumber();
                            long sentTimeStamp = currentOSMessage.getSentTimestamp();
                            long latency = receivedTimeMilliseconds - sentTimeStamp;

                            if(firstPacket){
                                this.startingTime = sentTimeStamp;   //  -----> based on sent time
                                firstPacket = false;
                            }

                            long simulationTimeStamp = sentTimeStamp - this.startingTime; //  -----> based on sent time

                            if (object instanceof InfoDataType) {
                                printWriter.println(
                                        simulationTimeStamp + "," +
                                        receivedTimeStamp + "," +
                                        sentTimeStamp + "," +
                                        "" + "," +          //flowID
                                        routeId + "," +     //routeID
                                        seqNumber + "," +   //seqNum
                                        "" + "," +          //dest
                                        "os" + "," +        //level
                                        "info" + "," +      //pkttype
                                        latency + "," +     // all
                                        latency + "," +          //info
                                        "" + "," +          //video
                                        ""             //vibrational
                                );
                            }else if (object instanceof VideoDataType) {
                                printWriter.println(
                                        simulationTimeStamp + "," +
                                        receivedTimeStamp + "," +
                                        sentTimeStamp + "," +
                                        "" + "," +
                                        routeId + "," +
                                        seqNumber + "," +
                                        "" + "," +
                                        "os" + "," +
                                        "video" + "," +
                                        latency + "," +     // all
                                        "" + "," +          //info
                                        latency + "," +          //video
                                        ""             //vibrational
                                );

                            } else if (object instanceof VibrationDataType) {
                                printWriter.println(
                                        simulationTimeStamp + "," +
                                        receivedTimeStamp + "," +
                                        sentTimeStamp + "," +
                                        "" + "," +
                                        routeId + "," +
                                        seqNumber + "," +
                                        "" + "," +
                                        "os" + "," +
                                        "vibrational" + "," +
                                        latency + "," +     // all
                                        "" + "," +          //info
                                        "" + "," +          //video
                                        latency             //vibrational
                                );

                            }
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
                } catch (Exception e) {
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

        System.out.println("DTControllerClientReceiver: registering shutdown hook");
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
        rampUtils = ((RampUtilsInterface) ComponentLocator.getComponent(ComponentType.RAMP_UTILS));
        dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));

        /*
         * Test method to run, match it with the one in ControllerClientMLRTestSender
         */
        receiveOneSeriesOfPacketsUsingDynamicMultiLayerRoutingOSRoutingPathProactiveCalculation();

        controllerClient.stopClient();
        ramp.stopRamp();
    }
}
