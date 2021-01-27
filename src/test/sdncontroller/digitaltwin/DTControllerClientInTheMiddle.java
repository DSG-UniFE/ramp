package test.sdncontroller.digitaltwin;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VibrationDataType;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerMessage.trafficCongestionLevel;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import test.sdncontroller.ControllerMessageMLRTestReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static test.sdncontroller.digitaltwin.DTConstants.*;

public class DTControllerClientInTheMiddle {
    private static final int PROTOCOL = E2EComm.TCP;

    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    private static int packetsReceived = 0;

    private static boolean sniffingIsActive = true;

    private static boolean notifiedLowCongestion = false;
    private static boolean notifiedMediumCongestion = false;

    public static void monitorTrafficStateAndNotifyController(){

        File outputFile = new File("flow_latencies_in_the_middle.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("receivedTimestamp,flowId,routeId,seqNumber,appLevelSlowPath,appLevelFastPath,osLevelFastPath");

        /*
         * Initialize the messageQueue of the received messages to be processed
         * by a dedicated thread for log purposes
         */
//        DTControllerClientInTheMiddle.messageQueue = new ArrayBlockingQueue<>(30000);
//
//        DTControllerClientInTheMiddle.ReceivedMessageHandler receivedMessageHandler = new DTControllerClientInTheMiddle.ReceivedMessageHandler(DTControllerClientInTheMiddle.messageQueue, printWriter, false);
//        receivedMessageHandler.start();

        /*
         * This is the thread that receives the
         * application level routing packets.
         */
        PrintWriter finalPrintWriter = printWriter;
        Thread applicationLevelRoutingReceiverThread = new Thread(() -> {

            System.out.println("----------------------------------->");
            System.out.println("-----------------------------------------------> matte thread is started");
            System.out.println("----------------------------------->");

            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }

            GenericPacket gp = null;
//            try {
//                gp = E2EComm.receive(firstServiceSocket);
//                packetsReceived++; //forse
//            } catch (Exception e1) {
//                e1.printStackTrace();
//            }

            try {
                //while (applicationLevelRoutingActive) {
                while (sniffingIsActive) {
                    gp = E2EComm.receive(firstServiceSocket);
                    packetsReceived++;
                    System.out.println(packetsReceived);

                    ControllerMessageMLRTestReceiver currentPacket = new ControllerMessageMLRTestReceiver(System.currentTimeMillis(), LocalDateTime.now(), gp);
                    LocalDateTime localDateTime = currentPacket.getReceivedTime();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
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


                                    if (latency > lowNetworkTrafficCongestionThreshold) {
                                        if(!notifiedLowCongestion){
                                            controllerClient.notifyControllerAboutTrafficState(trafficCongestionLevel.LOW_CONGESTION);
                                            notifiedLowCongestion = true;
                                        }
                                        if(latency > mediumNetworkTrafficCongestionThreshold){
                                            if(!notifiedMediumCongestion){
                                                controllerClient.notifyControllerAboutTrafficState(trafficCongestionLevel.MEDIUM_CONGESTION);
                                                notifiedMediumCongestion = true;
                                                sniffingIsActive = false;
                                            }
                                        }
//                                    if (true) {
                                        // controllerClient.notifyControllerAboutTrafficState(trafficCongestionLevel.HIGH_CONGESTION);
                                    }

                                    finalPrintWriter.flush();
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
//                                        applicationLevelRoutingActive = false;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }





                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("DTControllerClientInTheMiddle: all application level routing packets received from the sender");

            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        //receivedMessageHandler.stopMessageHandler();
        applicationLevelRoutingReceiverThread.start();
    }

//    public static class ReceivedMessageHandler extends Thread {
//
//        private final BlockingQueue<ControllerMessageMLRTestReceiver> messageQueue;
//
//        private PrintWriter printWriter;
//
//        private boolean active;
//
//        private boolean useProactiveRouting;
//
//        public ReceivedMessageHandler(BlockingQueue<ControllerMessageMLRTestReceiver> messageQueue, PrintWriter printWriter, boolean useProactiveRouting) {
//            this.messageQueue = messageQueue;
//            this.printWriter = printWriter;
//            this.active = true;
//            this.useProactiveRouting = useProactiveRouting;
//        }
//
//        public void stopMessageHandler() {
//            this.active = false;
//        }
//
//        @Override
//        public void run() {
//            while (active) {
//                try {
//                    ControllerMessageMLRTestReceiver currentPacket = this.messageQueue.take();
//                    LocalDateTime localDateTime = currentPacket.getReceivedTime();
//                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
//                    GenericPacket gp = currentPacket.getGenericPacket();
//                    Object object = currentPacket.getObject();
//
//                    if (gp != null && object == null) {
//                        /*
//                         * This is an application level routing packet.
//                         */
//                        try {
//                            /*
//                             * Check packet type
//                             */
//                            if (gp instanceof UnicastPacket) {
//                                /*
//                                 * Check payload
//                                 */
//                                UnicastPacket up = (UnicastPacket) gp;
//                                Object payload = E2EComm.deserialize(up.getBytePayload());
//                                if (payload instanceof VibrationDataType) {
//                                    VibrationDataType currentMessage = (VibrationDataType) payload;
//
//                                    /*
//                                     * Check latency in order to switch to Os Routing
//                                     */
//                                    long receivedTimeMilliseconds = currentPacket.getReceivedTimeMilliseconds();
//                                    long latency = receivedTimeMilliseconds - currentMessage.getSentTimestamp();
//
//
//                                    if (latency > 100) {
//
//                                    }
//
//                                    printWriter.flush();
//                                } else if (payload instanceof String) {
//                                    /*
//                                     * This is the final message sent by the sender
//                                     * informing that from now on it will send the remaining
//                                     * packets using os level routing.
//                                     */
//                                    String message = (String) payload;
//                                    if (message.equals("switchingToOsRouting")) {
//                                        /*
//                                         * This flag will shut down the
//                                         * applicationLevelRoutingReceiverThread.
//                                         */
////                                        applicationLevelRoutingActive = false;
//                                    }
//                                }
//                            }
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    } else if (gp == null && object != null) {
//                        /*
//                         * This is an os level routing packet.
//                         */
//                        if (object instanceof VibrationDataType) {
//                            VibrationDataType vibrationDataType = (VibrationDataType) object;
//                            int seqNumber = vibrationDataType.getSeqNumber();
//                            /*
//                             * osLevelFastPath
//                             */
////                            printWriter.println(timestamp + ",," + routeId + "," + seqNumber + ",,,[192.168.3.101, 192.168.3.103]");
//                            printWriter.flush();
//                        } else if (object instanceof String) {
//                            String message = (String) object;
//                            if (message.equals("Transfer completed")) {
//                                /*
//                                 * This flag will shut down the
//                                 * osLevelRoutingReceiverThread.
//                                 */
////                                transferCompleted = true;
//                            }
//                        }
//                    }
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    public static void main(String[] args) {

        ramp = RampEntryPoint.getInstance(true, null);

        // Wait a few second to allow the node to discover neighbors
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
        // Force neighbors update to make sure to know them
        ramp.forceNeighborsUpdate();

        System.out.println("ControllerClientTest: registering shutdown hook");
        // Setup signal handling in order to always stop RAMP gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ramp != null && controllerClient != null) {
                        System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
//                        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
//                        ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");
                        controllerClient.stopClient();
                        ramp.stopRamp();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }));

        controllerClient = ControllerClient.getInstance();

        monitorTrafficStateAndNotifyController();

//        controllerClient.stopClient();
//        ramp.stopRamp();
    }
}
