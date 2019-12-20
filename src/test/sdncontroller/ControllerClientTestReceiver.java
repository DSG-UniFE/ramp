package test.sdncontroller;

import java.io.*;
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
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 * <p>
 * This test must be executed on the node that is responsible to
 * receive messages from a node that executes the ControllerClientTestSender
 * test class. In order to use this class test properly you need to select
 * the complementaty method used by the sender node.
 * <p>
 * Example:
 * ControllerClientTestSender uses the "sendTwoSeriesOfPacketsToDifferentReceivers" method
 * ControllerClientTestReceiver uses the "receiveTwoSeriesOfPacketsInDifferentThreads" method
 */
public class ControllerClientTestReceiver {

    private static final int PROTOCOL = E2EComm.TCP;

    private static ControllerClient controllerClient;
    private static RampEntryPoint ramp;

    private static BlockingQueue<ControllerMessageTestReceiver> messageQueue;

    /**
     * Test method for Best Path policy (receive two consecutive strings)
     */
    private static void receiveTwoMessages() {
        BoundReceiveSocket serviceSocket = null;
        try {
            serviceSocket = E2EComm.bindPreReceive(PROTOCOL);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceManager.getInstance(false).registerService("SDNControllerTestSend", serviceSocket.getLocalPort(), PROTOCOL);

        System.out.println("ControllerClientTestReceiver: receiving message from the sender (port: " + serviceSocket.getLocalPort() + ")");
        String receivedMessage = null;
        int senderId = -1;
        GenericPacket gp = null;
        try {
            gp = E2EComm.receive(serviceSocket, 120 * 1000);
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
            if (payload instanceof String) {
                receivedMessage = (String) payload;
                senderId = up.getSourceNodeId();
            }
        }
        if (receivedMessage != null)
            System.out.println("ControllerClientTestReceiver: message \"" + receivedMessage + "\" received from the sender (nodeId: " + senderId + ")");
        else
            System.out.println("ControllerClientTestReceiver: no messages received from the sender");

        System.out.println("ControllerClientTestReceiver: receiving second message from the sender (port: " + serviceSocket.getLocalPort() + ")");
        receivedMessage = null;
        senderId = -1;
        gp = null;
        try {
            gp = E2EComm.receive(serviceSocket, 120 * 1000);
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
            if (payload instanceof String) {
                receivedMessage = (String) payload;
                senderId = up.getSourceNodeId();
            }
        }
        if (receivedMessage != null)
            System.out.println("ControllerClientTestReceiver: message \"" + receivedMessage + "\" received from the sender (nodeId: " + senderId + ")");
        else
            System.out.println("ControllerClientTestReceiver: no messages received from the sender");

        ServiceManager.getInstance(false).removeService("SDNControllerTestSend");
    }

    /**
     * Test method for no SDN policy (send a single file, selected through the fileName variable)
     */
    private static void receiveFile() {
        BoundReceiveSocket serviceSocket = null;
        try {
            serviceSocket = E2EComm.bindPreReceive(PROTOCOL);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceManager.getInstance(false).registerService("SDNControllerTestSend", serviceSocket.getLocalPort(), PROTOCOL);

        System.out.println("ControllerClientTestReceiver: receiving the file name from the sender (port: " + serviceSocket.getLocalPort() + ")");
        String message = null;
        String fileName = null;
        String[] senderDest = null;
        int senderPort = -1;
        GenericPacket gp = null;
        try {
            gp = E2EComm.receive(serviceSocket);
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
        fileName = message.substring(0, message.indexOf(";"));
        senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
        System.out.println("ControllerClientTestReceiver: file name received from the sender, message: " + message + ", port: " + senderPort);

        String response = "ok";
        try {
            E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        System.out.println("ControllerClientTestReceiver: receiving the file from the sender (port: " + serviceSocket.getLocalPort() + ")");
        // File file = new File("./ramp_controllerclienttest.jar");
        File file = new File(fileName);
        FileOutputStream fileOutputStream = null;
        long totalTime = 0;
        try {
            fileOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        totalTime = System.currentTimeMillis();
        try {
            E2EComm.receive(serviceSocket, fileOutputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        totalTime = (System.currentTimeMillis() - totalTime) / 1000;
        System.out.println("ControllerClientTestReceiver: file received from the sender, name " + fileName + ", size " + file.length() / 1000 + ", total time " + totalTime);
        try {
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            serviceSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String finalMessage = "file_received";
        try {
            E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Test method for traffic engineering policies (send two files addressing different RAMP services)
     * This is ideal to test the SinglePriorityForwarder
     */
    private static void receiveTwoFilesInDifferentThreads() {
        /*
         * Initialize the output file to keep track when each file is received
         * in order to get the total flow latency.
         *
         * To get consistent results the clocks of all the nodes
         * must be synchronized using for example NTP.
         */
        File outputFile = new File("flow_latencies_receiver.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("timestamp,filename");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        Thread firstThread = new Thread(() -> {
            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            assert firstServiceSocket != null;
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendFirst", firstServiceSocket.getLocalPort(), PROTOCOL);

            System.out.println("ControllerClientTestReceiver: receiving the first file name from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: first file name received from the sender, message: " + message + ", port: " + senderPort);

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientTestReceiver: receiving the first file from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
            File file = new File(fileName);
            FileOutputStream fileOutputStream = null;
            long totalTime = 0;
            try {
                fileOutputStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            totalTime = System.currentTimeMillis();
            try {
                E2EComm.receive(firstServiceSocket, fileOutputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }

            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + fileName);

            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: first file received from the sender, name " + fileName + ", size " + file.length() / 1000 + ", total time " + totalTime);
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "file_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread secondThread = new Thread(() -> {
            BoundReceiveSocket secondServiceSocket = null;
            try {
                secondServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendSecond", secondServiceSocket.getLocalPort(), PROTOCOL);

            System.out.println("ControllerClientTestReceiver: receiving the second file name from the sender (port: " + secondServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
            GenericPacket gp = null;
            try {
                gp = E2EComm.receive(secondServiceSocket);
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: second file name received from the sender");

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientTestReceiver: receiving the second file from the sender (port: " + secondServiceSocket.getLocalPort() + ")");

            File file = new File(fileName);
            FileOutputStream fileOutputStream = null;
            long totalTime = 0;
            try {
                fileOutputStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            totalTime = System.currentTimeMillis();
            try {
                E2EComm.receive(secondServiceSocket, fileOutputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + fileName);

            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: second file received from the sender, name " + fileName + ", size " + file.length() / 1000 + ", total time " + totalTime);
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                secondServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "file_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        firstThread.start();
        secondThread.start();

        try {
            firstThread.join();
            secondThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
        ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");

        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies (send three files addressing different RAMP services)
     * This is ideal to test the MultipleFlowsSinglePriorityForwarder
     */
    private static void receiveThreeFilesInDifferentThreads() {
        /*
         * Initialize the output file to keep track when each file is received
         * in order to get the total flow latency.
         *
         * To get consistent results the clocks of all the nodes
         * must be synchronized using for example NTP.
         */
        File outputFile = new File("flow_latencies_receiver.csv");
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        assert printWriter != null;
        printWriter.println("timestamp,filename");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        Thread firstThread = new Thread(() -> {
            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendFirst", firstServiceSocket.getLocalPort(), PROTOCOL);

            System.out.println("ControllerClientTestReceiver: receiving the first file name from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: first file name received from the sender, message: " + message + ", port: " + senderPort);

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientTestReceiver: receiving the first file from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
            File file = new File(fileName);
            FileOutputStream fileOutputStream = null;
            long totalTime = 0;
            try {
                fileOutputStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            totalTime = System.currentTimeMillis();
            try {
                E2EComm.receive(firstServiceSocket, fileOutputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + fileName);

            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: first file received from the sender, name " + fileName + ", size " + file.length() / 1000 + ", total time " + totalTime);
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "file_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread secondThread = new Thread(() -> {
            BoundReceiveSocket secondServiceSocket = null;
            try {
                secondServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendSecond", secondServiceSocket.getLocalPort(), PROTOCOL);

            System.out.println("ControllerClientTestReceiver: receiving the second file name from the sender (port: " + secondServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
            GenericPacket gp = null;
            try {
                gp = E2EComm.receive(secondServiceSocket);
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: second file name received from the sender");

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientTestReceiver: receiving the second file from the sender (port: " + secondServiceSocket.getLocalPort() + ")");

            File file = new File(fileName);
            FileOutputStream fileOutputStream = null;
            long totalTime = 0;
            try {
                fileOutputStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            totalTime = System.currentTimeMillis();
            try {
                E2EComm.receive(secondServiceSocket, fileOutputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + fileName);

            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: second file received from the sender, name " + fileName + ", size " + file.length() / 1000 + ", total time " + totalTime);
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                secondServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "file_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread thirdThread = new Thread(() -> {
            BoundReceiveSocket thirdServiceSocket = null;
            try {
                thirdServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendThird", thirdServiceSocket.getLocalPort(), PROTOCOL);

            System.out.println("ControllerClientTestReceiver: receiving the third file name from the sender (port: " + thirdServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
            GenericPacket gp = null;
            try {
                gp = E2EComm.receive(thirdServiceSocket);
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: third file name received from the sender");

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientTestReceiver: receiving the third file from the sender (port: " + thirdServiceSocket.getLocalPort() + ")");

            File file = new File(fileName);
            FileOutputStream fileOutputStream = null;
            long totalTime = 0;
            try {
                fileOutputStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            totalTime = System.currentTimeMillis();
            try {
                E2EComm.receive(thirdServiceSocket, fileOutputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            finalPrintWriter.println(timestamp + "," + fileName);

            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: third file received from the sender, name " + fileName + ", size " + file.length() / 1000 + ", total time " + totalTime);
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                thirdServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "file_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        firstThread.start();
        secondThread.start();
        thirdThread.start();
        try {
            firstThread.join();
            secondThread.join();
            thirdThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
        ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");
        ServiceManager.getInstance(false).removeService("SDNControllerTestSendThird");

        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for rerouting policy using the specified protocol (receive one series of consecutive packets addressing a RAMP service)
     * This is ideal to test the BestPathForwarder
     *
     * @param protocol            Protocol to be used for the series transfer. Must math the sender method
     * @param packetFrequency     Interval in milliseconds between each packet sending. Must math the sender method
     * @param totalTransferTime   Total time in seconds of the series transfer. Must math the sender method
     *                            The total number of packets is given by the following formula:
     *                            int numberOfPacketsPerSeries = (1000 * totalTime) / packetFrequency;
     */
    public static void receiveOneSeriesOfPacketsInOneThread(int protocol, int packetFrequency, int totalTransferTime) {
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
        printWriter.println("timestamp,flowId,seqNumber,path");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        /*
         * Initialize the messageQueue of the received messages to be processed
         * by a dedicated thread for log purposes
         */
        ControllerClientTestReceiver.messageQueue = new ArrayBlockingQueue<>(30000);

        ReceivedMessageHandler receivedMessageHandler = new ReceivedMessageHandler(ControllerClientTestReceiver.messageQueue, printWriter);
        receivedMessageHandler.start();

        /*
         * This is the thread that receives the first series of packet.
         */
        Thread firstThread = new Thread(() -> {
            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(protocol);
            } catch (Exception e) {
                e.printStackTrace();
            }
            assert firstServiceSocket != null;
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendFirst", firstServiceSocket.getLocalPort(), protocol);

            System.out.println("ControllerClientTestReceiver: receiving the first series of packets name from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: first series of packets name received from the sender, message: " + message + ", port: " + senderPort);

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("ControllerClientTestReceiver: receiving the first series of packets from the sender (port: " + firstServiceSocket.getLocalPort() + ")");

            int numberOfPacketsPerSeries = (1000 * totalTransferTime) / packetFrequency;
            long totalTime = System.currentTimeMillis();
            try {
                for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                    gp = E2EComm.receive(firstServiceSocket);
                    ControllerClientTestReceiver.messageQueue.put(new ControllerMessageTestReceiver(LocalDateTime.now(), gp));
                    System.out.println(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: first series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "series_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        firstThread.start();

        try {
            firstThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");

        receivedMessageHandler.stopMessageHandler();
        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies using UDP protocol (receive two series of consecutive packets addressing different RAMP services)
     * This is ideal to test the SinglePriorityForwarder
     */
    public static void receiveTwoSeriesOfPacketsInDifferentThreads(int protocol, int numberOfPacketsPerSeries) {
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
        printWriter.println("timestamp,flowId,seqNumber");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        /*
         * Initialize the messageQueue of the received messages to be processed
         * by a dedicated thread for log purposes
         */
        ControllerClientTestReceiver.messageQueue = new ArrayBlockingQueue<>(30000);

        ReceivedMessageHandler receivedMessageHandler = new ReceivedMessageHandler(ControllerClientTestReceiver.messageQueue, printWriter);
        receivedMessageHandler.start();

        /*
         * This is the thread that receives the first series of packet.
         */
        Thread firstThread = new Thread(() -> {
            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(protocol);
            } catch (Exception e) {
                e.printStackTrace();
            }
            assert firstServiceSocket != null;
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendFirst", firstServiceSocket.getLocalPort(), protocol);

            System.out.println("ControllerClientTestReceiver: receiving the first series of packets name from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: first series of packets name received from the sender, message: " + message + ", port: " + senderPort);

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("//////////////// PRIMO ACK INVIATO ///////////////////");

            System.out.println("ControllerClientTestReceiver: receiving the first series of packets from the sender (port: " + firstServiceSocket.getLocalPort() + ")");

//			try {
//				firstServiceSocket = E2EComm.bindPreReceive(firstServiceSocket.getLocalPort(), protocol);
//			} catch (Exception e1) {
//				e1.printStackTrace();
//			}
            long totalTime = System.currentTimeMillis();
            try {
                for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                    gp = E2EComm.receive(firstServiceSocket, 0);
                    ControllerClientTestReceiver.messageQueue.put(new ControllerMessageTestReceiver(LocalDateTime.now(), gp));
                    System.out.println(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: first series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "series_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        /*
         * This is the thread that receives the second series of packet.
         */
        Thread secondThread = new Thread(() -> {
            BoundReceiveSocket secondServiceSocket = null;
            try {
                secondServiceSocket = E2EComm.bindPreReceive(protocol);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendSecond", secondServiceSocket.getLocalPort(), protocol);

            System.out.println("ControllerClientTestReceiver: receiving the second series of packets name from the sender (port: " + secondServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
            GenericPacket gp = null;
            try {
                gp = E2EComm.receive(secondServiceSocket);
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: second series of packets name received from the sender");

            String response = "ok";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(response));
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            System.out.println("//////////////// SECONDO ACK INVIATO ///////////////////");

            System.out.println("ControllerClientTestReceiver: receiving the second series of packets from the sender (port: " + secondServiceSocket.getLocalPort() + ")");

//			try {
//				secondServiceSocket = E2EComm.bindPreReceive(secondServiceSocket.getLocalPort(), protocol);
//			} catch (Exception e1) {
//				e1.printStackTrace();
//			}
            long totalTime = System.currentTimeMillis();
            try {
                for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                    gp = E2EComm.receive(secondServiceSocket, 0);
                    ControllerClientTestReceiver.messageQueue.put(new ControllerMessageTestReceiver(LocalDateTime.now(), gp));
                    System.out.println(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: second series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
            try {
                secondServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "series_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        firstThread.start();
        secondThread.start();

        try {
            firstThread.join();
            secondThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
        ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");

        receivedMessageHandler.stopMessageHandler();
        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies using UDP protocol (receive two series of consecutive packets addressing different RAMP services)
     * This is ideal to test the SinglePriorityForwarder
     */
    public static void receiveTwoSeriesOfPacketsInDifferentThreadsNew(int protocol, int numberOfPacketsPerSeries) {
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
        printWriter.println("timestamp,flowId,seqNumber,priority");

        /*
         * PrintWriter to be passed to sender threads
         */
        final PrintWriter finalPrintWriter = printWriter;

        /*
         * Initialize the messageQueue of the received messages to be processed
         * by a dedicated thread for log purposes
         */
        ControllerClientTestReceiver.messageQueue = new ArrayBlockingQueue<>(30000);

        ReceivedMessageHandler receivedMessageHandler = new ReceivedMessageHandler(ControllerClientTestReceiver.messageQueue, printWriter);
        receivedMessageHandler.start();

        /*
         * This is the thread that receives the first series of packet.
         */
        Thread firstThread = new Thread(() -> {
            BoundReceiveSocket firstServiceSocket = null;
            try {
                firstServiceSocket = E2EComm.bindPreReceive(protocol);
            } catch (Exception e) {
                e.printStackTrace();
            }
            assert firstServiceSocket != null;
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendFirst", firstServiceSocket.getLocalPort(), protocol);

            System.out.println("ControllerClientTestReceiver: receiving the first series of packets name from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: first series of packets name received from the sender, message: " + message + ", port: " + senderPort);

            System.out.println("ControllerClientTestReceiver: receiving the first series of packets from the sender (port: " + firstServiceSocket.getLocalPort() + ")");

            long totalTime = System.currentTimeMillis();
            try {
                for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                    gp = E2EComm.receive(firstServiceSocket, 0);
                    ControllerClientTestReceiver.messageQueue.put(new ControllerMessageTestReceiver(LocalDateTime.now(), gp));
                    System.out.println(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: first series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
            try {
                firstServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "series_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        /*
         * This is the thread that receives the second series of packet.
         */
        Thread secondThread = new Thread(() -> {
            BoundReceiveSocket secondServiceSocket = null;
            try {
                secondServiceSocket = E2EComm.bindPreReceive(protocol);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ServiceManager.getInstance(false).registerService("SDNControllerTestSendSecond", secondServiceSocket.getLocalPort(), protocol);

            System.out.println("ControllerClientTestReceiver: receiving the second series of packets name from the sender (port: " + secondServiceSocket.getLocalPort() + ")");
            String message = null;
            String fileName = null;
            String[] senderDest = null;
            int senderPort = -1;
            GenericPacket gp = null;
            try {
                gp = E2EComm.receive(secondServiceSocket);
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
            fileName = message.substring(0, message.indexOf(";"));
            senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
            System.out.println("ControllerClientTestReceiver: second series of packets name received from the sender");

            System.out.println("ControllerClientTestReceiver: receiving the second series of packets from the sender (port: " + secondServiceSocket.getLocalPort() + ")");

            long totalTime = System.currentTimeMillis();
            try {
                for (int i = 0; i < numberOfPacketsPerSeries; i++) {
                    gp = E2EComm.receive(secondServiceSocket, 0);
                    ControllerClientTestReceiver.messageQueue.put(new ControllerMessageTestReceiver(LocalDateTime.now(), gp));
                    System.out.println(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            totalTime = (System.currentTimeMillis() - totalTime) / 1000;
            System.out.println("ControllerClientTestReceiver: second series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
            try {
                secondServiceSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String finalMessage = "series_received";
            try {
                E2EComm.sendUnicast(senderDest, senderPort, protocol, E2EComm.serialize(finalMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        firstThread.start();
        secondThread.start();

        try {
            firstThread.join();
            secondThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
        ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");

        receivedMessageHandler.stopMessageHandler();
        finalPrintWriter.close();
        printWriter.close();
    }

    /**
     * Test method for traffic engineering policies using UDP protocol (receive three series of consecutive packets addressing different RAMP services)
     */
    private static void receiveThreeSeriesOfFilesInDifferentThreads() {
        Thread firstThread = new Thread() {
            public void run() {
                BoundReceiveSocket firstServiceSocket = null;
                try {
                    firstServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                ServiceManager.getInstance(false).registerService("SDNControllerTestSendFirst", firstServiceSocket.getLocalPort(), PROTOCOL);

                System.out.println("ControllerClientTestReceiver: receiving the first series of packets name from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
                String message = null;
                String fileName = null;
                String[] senderDest = null;
                int senderPort = -1;
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
                fileName = message.substring(0, message.indexOf(";"));
                senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
                System.out.println("ControllerClientTestReceiver: first series of packets name received from the sender, message: " + message + ", port: " + senderPort);

                String response = "ok";
                try {
                    E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

                System.out.println("ControllerClientTestReceiver: receiving the first series of packets from the sender (port: " + firstServiceSocket.getLocalPort() + ")");
                long totalTime = System.currentTimeMillis();
                try {
                    for (int i = 0; i < 5000; i++)
                        E2EComm.receive(firstServiceSocket);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                totalTime = (System.currentTimeMillis() - totalTime) / 1000;
                System.out.println("ControllerClientTestReceiver: first series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
                try {
                    firstServiceSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String finalMessage = "series_received";
                try {
                    E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Thread secondThread = new Thread() {
            public void run() {
                BoundReceiveSocket secondServiceSocket = null;
                try {
                    secondServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                ServiceManager.getInstance(false).registerService("SDNControllerTestSendSecond", secondServiceSocket.getLocalPort(), PROTOCOL);

                System.out.println("ControllerClientTestReceiver: receiving the second series of packets name from the sender (port: " + secondServiceSocket.getLocalPort() + ")");
                String message = null;
                String fileName = null;
                String[] senderDest = null;
                int senderPort = -1;
                GenericPacket gp = null;
                try {
                    gp = E2EComm.receive(secondServiceSocket);
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
                fileName = message.substring(0, message.indexOf(";"));
                senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
                System.out.println("ControllerClientTestReceiver: second series of packets name received from the sender");

                String response = "ok";
                try {
                    E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

                System.out.println("ControllerClientTestReceiver: receiving the second series of packets from the sender (port: " + secondServiceSocket.getLocalPort() + ")");
                long totalTime = System.currentTimeMillis();
                try {
                    E2EComm.receive(secondServiceSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                totalTime = (System.currentTimeMillis() - totalTime) / 1000;
                System.out.println("ControllerClientTestReceiver: second series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
                try {
                    secondServiceSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String finalMessage = "series_received";
                try {
                    E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Thread thirdThread = new Thread() {
            public void run() {
                BoundReceiveSocket thirdServiceSocket = null;
                try {
                    thirdServiceSocket = E2EComm.bindPreReceive(PROTOCOL);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                ServiceManager.getInstance(false).registerService("SDNControllerTestSendThird", thirdServiceSocket.getLocalPort(), PROTOCOL);

                System.out.println("ControllerClientTestReceiver: receiving the third series of packets name from the sender (port: " + thirdServiceSocket.getLocalPort() + ")");
                String message = null;
                String fileName = null;
                String[] senderDest = null;
                int senderPort = -1;
                GenericPacket gp = null;
                try {
                    gp = E2EComm.receive(thirdServiceSocket);
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
                fileName = message.substring(0, message.indexOf(";"));
                senderPort = Integer.parseInt(message.substring(message.indexOf(";") + 1, message.length()));
                System.out.println("ControllerClientTestReceiver: third series of packets name received from the sender");

                String response = "ok";
                try {
                    E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

                System.out.println("ControllerClientTestReceiver: receiving the third series of packets from the sender (port: " + thirdServiceSocket.getLocalPort() + ")");
                long totalTime = System.currentTimeMillis();
                try {
                    E2EComm.receive(thirdServiceSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                totalTime = (System.currentTimeMillis() - totalTime) / 1000;
                System.out.println("ControllerClientTestReceiver: third series of packets received from the sender, name " + fileName + ", size " + 5000 * 10 + ", total time " + totalTime);
                try {
                    thirdServiceSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String finalMessage = "series_received";
                try {
                    E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        firstThread.start();
        secondThread.start();
        thirdThread.start();
        try {
            firstThread.join();
            secondThread.join();
            thirdThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
        ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");
        ServiceManager.getInstance(false).removeService("SDNControllerTestSendThird");
    }

    /**
     * Test method for Tree-based Multicast policy (receive a string; to be run on all the receivers)
     */
    private static void receiveMessage() {
        BoundReceiveSocket serviceSocket = null;
        try {
            serviceSocket = E2EComm.bindPreReceive(PROTOCOL);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceManager.getInstance(false).registerService("SDNControllerTestSend", serviceSocket.getLocalPort(), PROTOCOL);

        System.out.println("ControllerClientTestReceiver: receiving message from the sender (port: " + serviceSocket.getLocalPort() + ")");
        String receivedMessage = null;
        int senderId = -1;
        GenericPacket gp = null;
        try {
            gp = E2EComm.receive(serviceSocket, 120 * 1000);
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
            if (payload instanceof String) {
                receivedMessage = (String) payload;
                senderId = up.getSourceNodeId();
            }
        }
        if (receivedMessage != null)
            System.out.println("ControllerClientTestReceiver: message \"" + receivedMessage + "\" received from the sender (nodeId: " + senderId + ")");
        else
            System.out.println("ControllerClientTestReceiver: no messages received from the sender");

        ServiceManager.getInstance(false).removeService("SDNControllerTestSend");
    }

    /**
     * Test method for Tree-based Multicast policy (receive two payloads, the second one is sent using the Tree-based Multicast policy)
     */
    private static void receiveMultipleMessages() {
        BoundReceiveSocket serviceSocket = null;
        try {
            serviceSocket = E2EComm.bindPreReceive(PROTOCOL);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServiceManager.getInstance(false).registerService("SDNControllerTestSend", serviceSocket.getLocalPort(), PROTOCOL);

        System.out.println("ControllerClientTestReceiver: receiving the first message name from the sender (port: " + serviceSocket.getLocalPort() + ")");
        String message = null;
        String[] senderDest = null;
        int senderPort = -1;
        GenericPacket gp = null;
        try {
            gp = E2EComm.receive(serviceSocket);
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
        senderPort = Integer.parseInt(message);
        System.out.println("ControllerClientTestReceiver: first message name received from the sender, message: " + message + ", port: " + senderPort);

        String response = "ok";
        try {
            E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(response));
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        System.out.println("ControllerClientTestReceiver: receiving the first message from the sender (port: " + serviceSocket.getLocalPort() + ")");
        GenericPacket messagePacket = null;
        long totalTime = System.currentTimeMillis();
        try {
            messagePacket = E2EComm.receive(serviceSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }
        totalTime = (System.currentTimeMillis() - totalTime) / 1000;
        System.out.println("ControllerClientTestReceiver: first message received from the sender, size 20000, total time " + totalTime);

        String finalMessage = "message_received";
//		try {
//			E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}

        System.out.println("ControllerClientTestReceiver: receiving the second message from the sender (port: " + serviceSocket.getLocalPort() + ")");
        totalTime = System.currentTimeMillis();
        try {
            messagePacket = E2EComm.receive(serviceSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }
        totalTime = (System.currentTimeMillis() - totalTime) / 1000;
        System.out.println("ControllerClientTestReceiver: second message received from the sender, size 20000, total time " + totalTime);
        try {
            serviceSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            E2EComm.sendUnicast(senderDest, senderPort, PROTOCOL, E2EComm.serialize(finalMessage));
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).removeService("SDNControllerTestSend");
    }

    public static class ReceivedMessageHandler extends Thread {

        private final BlockingQueue<ControllerMessageTestReceiver> messageQueue;

        private PrintWriter printWriter;

        private boolean active;

        public ReceivedMessageHandler(BlockingQueue<ControllerMessageTestReceiver> messageQueue, PrintWriter printWriter) {
            this.messageQueue = messageQueue;
            this.printWriter = printWriter;
            this.active = true;
        }

        public void stopMessageHandler() {
            this.active = false;
        }

        @Override
        public void run() {
            while (active) {
                try {
                    ControllerMessageTestReceiver currentPacket = this.messageQueue.take();
                    GenericPacket gp = currentPacket.getGenericPacket();
                    LocalDateTime localDateTime = currentPacket.getReceivedTime();
                    String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
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
                            if (payload instanceof ControllerMessageTest) {
                                ControllerMessageTest currentMessage = (ControllerMessageTest) payload;
                                int seqNumber = currentMessage.getSeqNumber();
                                int flowId = up.getFlowId();
                                printWriter.println(timestamp + "," + flowId + "," + seqNumber + "," + Arrays.toString(up.getDest()));
                                printWriter.flush();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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

        System.out.println("ControllerClientTestReceiver: registering shutdown hook");
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
         * Test method to run, match it with the one in ControllerClientTestSender
         */
        receiveOneSeriesOfPacketsInOneThread(E2EComm.TCP, 50, 15);

        controllerClient.stopClient();
        ramp.stopRamp();
    }
}
