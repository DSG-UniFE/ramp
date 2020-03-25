package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.DataTypesManagerInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.routingPolicy.RoutingPolicy;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class SDNControllerClient {

    private static final int TCP = E2EComm.TCP;
    private static final int UDP = E2EComm.UDP;
    private BoundReceiveSocket receiveSocketTCP;
    private BoundReceiveSocket receiveSocketUDP;
    private DatagramSocket datagramSocketUDP;
    private ServerSocket serverSocketTCP;
    private BlockingQueue<TestReceiverPacket> rampMessageQueue;
    private BlockingQueue<DatagramPacket> osRoutingMessageQueueUDP;
    private BlockingQueue<TestReceiverPacketOsRouting> osRoutingMessageQueueTCP;
    private Vector<String> receivedMessages;
    private int seqNumber = 0;
    private int testRepetition = 0;
    private Vector<ServiceResponse> availableServices;

    private boolean active = true;
    private boolean trafficGeneratorActive = false;
    private static SDNControllerClient SDNControllerClient = null;
    private static ControllerClient controllerClient = null;
    private static SDNControllerClientJFrame ccjf;

    private DataTypesManagerInterface dataTypesManager;

    public static synchronized SDNControllerClient getInstance() {
        if (SDNControllerClient == null) {
            SDNControllerClient = new SDNControllerClient();
        }
        ccjf.setVisible(true);
        return SDNControllerClient;
    }

    private SDNControllerClient() {
        System.out.println("SDNControllerClient START");
        controllerClient = ControllerClient.getInstance();
        sleep(2);

        dataTypesManager = ((DataTypesManagerInterface) ComponentLocator.getComponent(ComponentType.DATA_TYPES_MANAGER));

        try {
            receiveSocketTCP = E2EComm.bindPreReceive(TCP);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverTCP", receiveSocketTCP.getLocalPort(), TCP);

        try {
            receiveSocketUDP = E2EComm.bindPreReceive(UDP);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverUDP", receiveSocketUDP.getLocalPort(), UDP);

        try {
            this.serverSocketTCP = new ServerSocket(0, 100);
            this.serverSocketTCP.setReuseAddress(true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverServerSocketTCP", serverSocketTCP.getLocalPort(), TCP);

        try {
            this.datagramSocketUDP = new DatagramSocket(0);
            this.datagramSocketUDP.setReuseAddress(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverDatagramSocketUDP", datagramSocketUDP.getLocalPort(), UDP);

        rampMessageQueue = new ArrayBlockingQueue<>(30000);
        osRoutingMessageQueueUDP = new ArrayBlockingQueue<>(30000);
        osRoutingMessageQueueTCP = new ArrayBlockingQueue<>(30000);
        receivedMessages = new Vector<>();

        new BoundReceiveSocketMessageHandler(rampMessageQueue).start();
        new SDNControllerClient.BoundReceiveSocketListener(receiveSocketTCP, "TCP").start();
        new SDNControllerClient.BoundReceiveSocketListener(receiveSocketUDP, "UDP").start();

        new DatagramSocketMessageHandler(osRoutingMessageQueueUDP).start();
        new ServiceSocketMessageHandler(osRoutingMessageQueueTCP).start();
        new ServerSocketListener(serverSocketTCP, "TCP").start();
        new DatagramSocketListener(datagramSocketUDP, "UDP").start();
        ccjf = new SDNControllerClientJFrame(this);
    }

    public static boolean isActive() {
        return (SDNControllerClient != null);
    }

    public void stopClient() {
        System.out.println("SDNControllerClient STOP");
        ServiceManager.getInstance(false).removeService("SDNControllerClientReceiverTCP");
        ServiceManager.getInstance(false).removeService("SDNControllerClientReceiverUDP");
        ServiceManager.getInstance(false).removeService("SDNControllerClientReceiverServerSocketTCP");
        ServiceManager.getInstance(false).removeService("SDNControllerClientReceiverDatagramSocketUDP");
        active = false;
        try {
            receiveSocketTCP.close();
            receiveSocketUDP.close();
            serverSocketTCP.close();
            datagramSocketUDP.close();

            controllerClient.stopClient();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        controllerClient = null;
        SDNControllerClient = null;
        System.out.println("SDNControllerClient FINISHED");
    }

    public Vector<ServiceResponse> findControllerClientReceiver(int protocol, int ttl, int timeout, int serviceAmount, boolean osRoutingMode) throws Exception {
        String serviceName = "";
        if (!osRoutingMode) {
            if (protocol == UDP) {
                serviceName = "SDNControllerClientReceiverUDP";
            } else {
                serviceName = "SDNControllerClientReceiverTCP";
            }
        } else {
            if (protocol == UDP) {
                serviceName = "SDNControllerClientReceiverDatagramSocketUDP";
            } else {
                serviceName = "SDNControllerClientReceiverServerSocketTCP";
            }
        }
        long pre = System.currentTimeMillis();
        Vector<ServiceResponse> services = ServiceDiscovery.findServices(
                ttl,
                serviceName,
                timeout,
                serviceAmount,
                null
        );
        long post = System.currentTimeMillis();
        float elapsed = (post - pre) / (float) 1000;
        System.out.println("SDNControllerClient " + serviceName + " elapsed=" + elapsed + " services=" + services);

        availableServices = services;

        return availableServices;
    }

    public TrafficEngineeringPolicy getTrafficEngineeringPolicy() {
        return controllerClient.getTrafficEngineeringPolicy();
    }

    public RoutingPolicy getRoutingPolicy() {
        return controllerClient.getRoutingPolicy();
    }

    public int getFlowId(ApplicationRequirements applicationRequirements, int[] destNodeIds, int[] destPorts, PathSelectionMetric pathSelectionMetric) {
        return controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, pathSelectionMetric);
    }

    public PathDescriptor computeUnicastPathLocally(int clientNodeId, int destinationNodeId, PathSelectionMetric pathSelectionMetric) {
        return controllerClient.computeUnicastPathLocally(clientNodeId, destinationNodeId, pathSelectionMetric);
    }

    public List<Integer> getAvailableRouteIds(int destinationNodeId) {
        return controllerClient.getAvailableRouteIds(destinationNodeId);
    }

    public int getRouteId(int destNodeId, int destPort, ApplicationRequirements applicationRequirements, PathSelectionMetric pathSelectionMetric) {
        return controllerClient.getRouteId(destNodeId, destPort, applicationRequirements, pathSelectionMetric);
    }

    public ConcurrentHashMap<Integer, PathDescriptor> getDefaultFlowPath() {
        return controllerClient.getDefaultFlowPath();
    }

    public ConcurrentHashMap<Integer, PathDescriptor> getFlowPath() {
        return controllerClient.getFlowPaths();
    }

    public ConcurrentHashMap<Integer, Integer> getFlowPriorities() {
        return controllerClient.getFlowPriorities();
    }

    public Set<String> getDataTypesAvailable() {
        return controllerClient.getDataTypesAvailable();
    }

    public void stopTrafficGenerator() {
        this.trafficGeneratorActive = false;
    }

    private int getNextSeqNumber() {
        seqNumber++;
        return seqNumber;
    }

    private int getNextTestRepetition() {
        testRepetition++;
        return testRepetition;
    }

    private void resetSeqNumber() {
        seqNumber = 0;
    }

    public void sendUnicastMessage(ServiceResponse serviceResponse, String dataType, int payload, int flowId, int repetitions, int packetsPerSecond) {
        new SDNControllerClient.UnicastPacketSender(serviceResponse, dataType, payload, flowId, repetitions, packetsPerSecond).start();
    }

    public void sendMulticastMessage(List<Integer> destinations, String dataType, int payload, int flowId, int protocol, int repetitions) {
        new SDNControllerClient.MulticastPacketSender(destinations, dataType, payload, flowId, protocol, repetitions).start();
    }

    public void sendDatagramSocketMessage(ServiceResponse serviceResponse, int payload, int routeId, int repetitions, int packetsPerSecond) {
        new DatagramSocketPacketSender(serviceResponse, payload, routeId, repetitions, packetsPerSecond).start();
    }

    public void sendServiceSocketMessage(ServiceResponse serviceResponse, int payload, int routeId, int repetitions, int packetsPerSecond) {
        new ServerSocketPacketSender(serviceResponse, payload, routeId, repetitions, packetsPerSecond).start();
    }

    public Vector<String> getReceivedMessages() {
        return receivedMessages;
    }

    public void resetReceivedMessages() {
        receivedMessages = new Vector<>();
    }

    private int getServicePort(int destinationNodeId) {
        int servicePort = -1;
        for (ServiceResponse serviceResponse : availableServices) {
            int serverNodeId = serviceResponse.getServerNodeId();
            if (serverNodeId == destinationNodeId) {
                servicePort = serviceResponse.getServerPort();
                break;
            }
        }
        return servicePort;
    }

    public void getTopologyGraph() {
        controllerClient.getTopologyGraph();
    }

    private class UnicastPacketSender extends Thread {

        private ServiceResponse serviceResponse;
        private String dataType;
        private int payload;
        private int flowId;
        private int repetitions;
        private int packetsPerSecond;

        private UnicastPacketSender(ServiceResponse serviceResponse, String dataType, int payload, int flowId, int repetitions, int packetsPerSecond) {
            this.serviceResponse = serviceResponse;
            this.dataType = dataType;
            this.payload = payload;
            this.flowId = flowId;
            this.repetitions = repetitions;
            this.packetsPerSecond = packetsPerSecond;
        }

        @Override
        public void run() {
            Object packet = null;
            long dataType = GenericPacket.UNUSED_FIELD;
            Class cls = null;
            Class[] paramInt = new Class[1];
            paramInt[0] = Integer.TYPE;
            Method method = null;
            resetSeqNumber();

            if (!this.dataType.equals("Default Message")) {
                try {
                    cls = dataTypesManager.getDataTypeClassObject(this.dataType);
                    packet = cls.getDeclaredConstructor().newInstance();
                    method = cls.getMethod("setPayloadSize", paramInt);
                    method.invoke(packet, this.payload);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
                dataType = dataTypesManager.getDataTypeId(this.dataType);
            } else {
                packet = new SDNControllerMessage();
                /*
                 * The number 138 is the size in byte of the object without payload field set.
                 */
                ((SDNControllerMessage) packet).setPayloadSize(this.payload);
            }

            String protocol;
            int serviceResponseProtocol = serviceResponse.getProtocol();
            if (serviceResponseProtocol == UDP) {
                protocol = "UDP";
            } else {
                protocol = "TCP";
            }

            if (repetitions > 0 && packetsPerSecond == -1) {
                for (int i = 0; i < repetitions; i++) {
                    int seqNumber = getNextSeqNumber();
                    if (!this.dataType.equals("Default Message")) {
                        try {
                            method = cls.getMethod("setSeqNumber", paramInt);
                            method.invoke(packet, seqNumber);
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    } else {
                        ((SDNControllerMessage) packet).setSeqNumber(seqNumber);
                    }

                    System.out.println("SDNControllerClient.UnicastPacketSender: sending packet \""
                            + seqNumber + "\" of type: " + this.dataType + " to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), flowId: " + flowId + ", Protocol: " + protocol);
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
                                dataType,
                                E2EComm.serialize(packet)
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (repetitions == -1 && packetsPerSecond > 0) {
                long computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);

                trafficGeneratorActive = true;

                int packetsSent = 0;

                long preWhile = System.currentTimeMillis();

                int packetTimeoutConnect = 60000 * 4;

                int testRepetition = getNextTestRepetition();

                while (trafficGeneratorActive && packetsSent < (packetsPerSecond * 15)) {
                    int seqNumber = getNextSeqNumber();
                    if (!this.dataType.equals("Default Message")) {
                        try {
                            method = cls.getMethod("setSeqNumber", paramInt);
                            method.invoke(packet, seqNumber);
                            method = cls.getMethod("setTestRepetition", paramInt);
                            method.invoke(packet, testRepetition);
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    } else {
                        ((SDNControllerMessage) packet).setSeqNumber(seqNumber);
                        ((SDNControllerMessage) packet).setTestRepetition(testRepetition);
                    }

                    System.out.println("SDNControllerClient.UnicastPacketSender: sending packet \""
                            + seqNumber + "\" of type: " + this.dataType + " to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), flowId: " + flowId + ", Protocol: " + protocol);
                    try {
                        // TODO Change me according to new protobuffer installation
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
                                packetTimeoutConnect,
                                flowId,
                                dataType,
                                E2EComm.serialize(packet)
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    packetsSent++;
                    long sleepTime = (computedSleepTime * seqNumber) - (System.currentTimeMillis() - preWhile);
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            System.out.println("SDNControllerClient.UnicastPacketSender: unicast message/s sent to the receiver");
        }
    }

    private class MulticastPacketSender extends Thread {

        private List<Integer> destinations;
        private String dataType;
        private int payload;
        private int flowId;
        private int protocol;
        private int repetitions;

        private MulticastPacketSender(List<Integer> destinations, String dataType, int payload, int flowId, int protocol, int repetitions) {
            this.destinations = destinations;
            this.dataType = dataType;
            this.payload = payload;
            this.flowId = flowId;
            this.protocol = protocol;
            this.repetitions = repetitions;
        }

        @Override
        public void run() {
            String stringProtocol;
            if (protocol == UDP) {
                stringProtocol = "UDP";
            } else {
                stringProtocol = "TCP";
            }
            String receivers = "( ";
            int count = 1;
            for (Integer i : destinations) {
                receivers += count + "nodeId: " + i + ", ";
            }
            receivers += ")";

            for (int i = 0; i < this.repetitions; i++) {
                SDNControllerMessage packet = new SDNControllerMessage(getNextSeqNumber(), payload);
                System.out.println("SDNControllerClient.MulticastPacketSender: sending message \""
                        + packet.getSeqNumber() + "\" to the receivers " + receivers + ", flowId: " + flowId + ", Protocol: " + stringProtocol);

                try {
                    E2EComm.sendUnicast(
                            new String[]{GeneralUtils.getLocalHost()},
                            Dispatcher.getLocalRampId(),
                            40000,
                            protocol,
                            false,
                            GenericPacket.UNUSED_FIELD,
                            E2EComm.DEFAULT_BUFFERSIZE,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            GenericPacket.UNUSED_FIELD,
                            flowId,
                            E2EComm.serialize(packet)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("SDNControllerClient.MulticastPacketSender: multicast message sent to the receivers");
        }
    }

    private class DatagramSocketPacketSender extends Thread {

        private ServiceResponse serviceResponse;
        private int payload;
        private int routeId;
        private int repetitions;
        private int packetsPerSecond;
        private DatagramSocket socket;

        private DatagramSocketPacketSender(ServiceResponse serviceResponse, int payload, int routeId, int repetitions, int packetsPerSecond) {
            this.serviceResponse = serviceResponse;
            this.payload = payload;
            this.routeId = routeId;
            this.repetitions = repetitions;
            this.packetsPerSecond = packetsPerSecond;
        }

        @Override
        public void run() {
            InetSocketAddress sourceIpAddress = new InetSocketAddress(controllerClient.getRouteIdSourceIpAddress(this.routeId), 0);

            try {
                this.socket = new DatagramSocket(sourceIpAddress);

            } catch (SocketException e) {
                e.printStackTrace();
            }

            String destinationIP = controllerClient.getRouteIdDestinationIpAddress(this.routeId);
            int port = getServicePort(serviceResponse.getServerNodeId());
            resetSeqNumber();

            try {
                this.socket.connect(InetAddress.getByName(destinationIP), port);
            } catch (UnknownHostException e) {

                e.printStackTrace();
            }

            if (repetitions > 0 && packetsPerSecond == -1) {
                for (int i = 0; i < repetitions; i++) {
                    SDNControllerMessage packet = new SDNControllerMessage();
                    int seqNumber = getNextSeqNumber();
                    packet.setSeqNumber(seqNumber);
                    /*
                     * The number 138 is the size in byte of the object without payload field set.
                     */
                    packet.setPayloadSize(this.payload);
                    System.out.println("SDNControllerClient.DatagramSocketPacketSender: sending Packet \""
                            + packet.getSeqNumber() + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), IP: " + destinationIP + ", Protocol: UDP");

                    byte[] udpBuffer = new byte[GenericPacket.MAX_UDP_PACKET];
                    DatagramPacket dp = null;

                    try {
                        dp = new DatagramPacket(udpBuffer, udpBuffer.length, InetAddress.getByName(destinationIP), port);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    if (dp != null) {
                        try {
                            dp.setData(E2EComm.serialize(packet));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        this.socket.send(dp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else if (repetitions == -1 && packetsPerSecond > 0) {
                long sleepTime = (long) Math.ceil(1000 / packetsPerSecond);

                trafficGeneratorActive = true;

                while (trafficGeneratorActive) {
                    int seqNumber = getNextSeqNumber();
                    SDNControllerMessage packet = new SDNControllerMessage();
                    packet.setSeqNumber(seqNumber);
                    /*
                     * The number 138 is the size in byte of the object without payload field set.
                     */
                    packet.setPayloadSize(this.payload);
                    System.out.println("SDNControllerClient.DatagramSocketPacketSender: sending Packet \""
                            + packet.getSeqNumber() + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), IP: " + destinationIP + ", Protocol: UDP");

                    byte[] udpBuffer = new byte[GenericPacket.MAX_UDP_PACKET];
                    DatagramPacket dp = null;

                    try {
                        dp = new DatagramPacket(udpBuffer, udpBuffer.length, InetAddress.getByName(destinationIP), port);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    if (dp != null) {
                        try {
                            dp.setData(E2EComm.serialize(packet));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        this.socket.send(dp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            socket.close();

            System.out.println("SDNControllerClient.DatagramSocketPacketSender: packet/s sent to the receiver");
        }
    }

    private class ServerSocketPacketSender extends Thread {

        private ServiceResponse serviceResponse;
        private int payload;
        private int routeId;
        private int repetitions;
        private int packetsPerSecond;
        private Socket socket;

        private ServerSocketPacketSender(ServiceResponse serviceResponse, int payload, int routeId, int repetitions, int packetsPerSecond) {
            this.serviceResponse = serviceResponse;
            this.payload = payload;
            this.routeId = routeId;
            this.repetitions = repetitions;
            this.packetsPerSecond = packetsPerSecond;
        }

        @Override
        public void run() {
            InetAddress sourceIpAddress = null;
            try {
                sourceIpAddress = InetAddress.getByName(controllerClient.getRouteIdSourceIpAddress(this.routeId));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

            String destinationIP = controllerClient.getRouteIdDestinationIpAddress(this.routeId);
            int port = getServicePort(serviceResponse.getServerNodeId());

            OutputStream outputStream = null;
            ObjectOutputStream objectOutputStream = null;

            resetSeqNumber();

            if (repetitions > 0 && packetsPerSecond == -1) {
                for (int i = 0; i < repetitions; i++) {
                    try {
                        this.socket = new Socket(InetAddress.getByName(destinationIP), port, sourceIpAddress, 0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        outputStream = this.socket.getOutputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        objectOutputStream = new ObjectOutputStream(outputStream);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    SDNControllerMessage packet = new SDNControllerMessage();
                    int seqNumber = getNextSeqNumber();
                    packet.setSeqNumber(seqNumber);
                    /*
                     * The number 138 is the size in byte of the object without payload field set.
                     */
                    packet.setPayloadSize(this.payload);
                    System.out.println("SDNControllerClient.ServerSocketPacketSender: sending Packet \""
                            + packet.getSeqNumber() + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), IP: " + destinationIP + ", Protocol: TCP");
                    if (objectOutputStream != null) {
                        try {
                            objectOutputStream.writeObject(packet);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else if (repetitions == -1 && packetsPerSecond >= 0) {
                long computedSleepTime = (long) Math.ceil(1000 / packetsPerSecond);

                trafficGeneratorActive = true;

                int packetsSent = 0;
                long preWhile = System.currentTimeMillis();
                int testRepetition = getNextTestRepetition();

                try {
                    this.socket = new Socket(InetAddress.getByName(destinationIP), port, sourceIpAddress, 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    outputStream = this.socket.getOutputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    objectOutputStream = new ObjectOutputStream(outputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                while (trafficGeneratorActive && packetsSent < (packetsPerSecond * 15)) {
                    SDNControllerMessage packet = new SDNControllerMessage();
                    packet.setTestRepetition(testRepetition);
                    /*
                     * The number 138 is the size in byte of the object without payload field set.
                     */
                    packet.setPayloadSize(this.payload);
                    int seqNumber = getNextSeqNumber();
                    packet.setSeqNumber(seqNumber);

                    System.out.println("SDNControllerClient.ServerSocketPacketSender: sending Packet \""
                            + packet.getSeqNumber() + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), IP: " + destinationIP + ", Protocol: TCP");

                    if (objectOutputStream != null) {
                        try {
                            objectOutputStream.writeObject(packet);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    long sleepTime = (computedSleepTime * seqNumber) - (System.currentTimeMillis() - preWhile);
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                try {
                    assert objectOutputStream != null;
                    objectOutputStream.close();
                    assert outputStream != null;
                    outputStream.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("SDNControllerClient.ServerSocketPacketSender: packet/s sent to the receiver");
        }
    }

    private class BoundReceiveSocketListener extends Thread {

        private BoundReceiveSocket socket;
        private String protocol;

        private BoundReceiveSocketListener(BoundReceiveSocket socket, String protocol) {
            this.socket = socket;
            this.protocol = protocol;
        }

        @Override
        public void run() {
            System.out.println("SDNControllerClient: receiving " + protocol + " messages from the sender (port: " + socket.getLocalPort() + ")");

            GenericPacket gp;
            while (active) {
                try {
                    /*
                     * Receive
                     */
                    gp = E2EComm.receive(socket, 30 * 1000);
                    //System.out.println("SDNControllerClient" + protocol + ": new message arrived");
                    rampMessageQueue.put(new TestReceiverPacket(LocalDateTime.now(), gp));
                } catch (SocketTimeoutException ste) {
                    //
                } catch (IOException e) {
                    //
                } catch (Exception e) {
                    //
                }
            }
            try {
                socket.close();
            } catch (IOException e) {
                //
            }
        }
    }

    public class BoundReceiveSocketMessageHandler extends Thread {

        private final BlockingQueue<TestReceiverPacket> messageQueue;

        public BoundReceiveSocketMessageHandler(BlockingQueue<TestReceiverPacket> messageQueue) {
            this.messageQueue = messageQueue;
        }

        @Override
        public void run() {
            while (active) {
                try {
                    TestReceiverPacket currentPacket = this.messageQueue.take();
                    GenericPacket gp = currentPacket.getGenericPacket();
                    int packetSize = E2EComm.objectSizePacket(gp);
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
                            if (payload instanceof SDNControllerMessage) {
                                int seqNumber = ((SDNControllerMessage) payload).getSeqNumber();
                                int testRepetition = ((SDNControllerMessage) payload).getTestRepetition();

                                /*
                                 * The number 138 is the size in byte of the object without payload field set.
                                 */
                                int payloadSize = up.getBytePayload().length;
                                String packetInfo = "BoundReceiveSocketMessageHandler: Default Message: " + seqNumber + ", packetSize: " + packetSize + ", payloadSize " + payloadSize + ", from " + up.getSourceNodeId();
                                receivedMessages.addElement(packetInfo);
                                System.out.println("SDNControllerClient.BoundReceiveSocketMessageHandler message: " + packetInfo);
                            } else if (dataTypesManager.containsDataType(payload.getClass().getSimpleName())) {
                                String dataType = payload.getClass().getSimpleName();
                                Class cls = dataTypesManager.getDataTypeClassObject(dataType);
                                Class noparams[] = {};
                                Method method = cls.getMethod("getSeqNumber", noparams);
                                int seqNumber = (int) method.invoke(payload);
                                method = cls.getMethod("getTestRepetition", noparams);
                                int testRepetition = (int) method.invoke(payload);
                                int payloadSize = up.getBytePayload().length;
                                String packetInfo = "BoundReceiveSocketMessageHandler: " + dataType + ": " + seqNumber + ", payloadSize " + payloadSize + ", from " + up.getSourceNodeId();
                                receivedMessages.addElement(packetInfo);
                                System.out.println("SDNControllerClient.BoundReceiveSocketMessageHandler message: " + packetInfo);
                            } else {
                                /*
                                 * Received payload is not SDNControllerMessage: do nothing...
                                 */
                                System.out.println("SDNControllerClient.BoundReceiveSocketMessageHandler wrong payload: " + payload);
                            }
                        } else {
                            /*
                             * Received packet is not UnicastPacket: do nothing...
                             */
                            System.out.println("SDNControllerClient.BoundReceiveSocketMessageHandler wrong packet: " + gp.getClass().getName());
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

    private class ServerSocketListener extends Thread {

        private ServerSocket socket;
        private String protocol;

        private ServerSocketListener(ServerSocket socket, String protocol) {
            this.socket = socket;
            this.protocol = protocol;
        }

        @Override
        public void run() {
            System.out.println("SDNControllerClient: ServerSocket receiving " + protocol + " messages from the sender (port: " + socket.getLocalPort() + ")");

            while (active) {
                try {
                    /*
                     * Receive
                     */
                    socket.setSoTimeout(10 * 60 * 1000);
                    Socket clientSocket = socket.accept();
                    InputStream inputStream = clientSocket.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    Object payload = null;
                    while ((payload = objectInputStream.readObject()) != null) {
                        if (payload instanceof SDNControllerMessage) {
                            osRoutingMessageQueueTCP.put(new TestReceiverPacketOsRouting(LocalDateTime.now(), (SDNControllerMessage) payload, clientSocket.getRemoteSocketAddress()));
                        }
                    }
                    objectInputStream.close();
                    inputStream.close();
                    clientSocket.close();
                } catch (SocketTimeoutException ste) {
                    //
                } catch (IOException e) {
                    //
                } catch (Exception e) {
                    //
                }
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ServiceSocketMessageHandler extends Thread {

        private final BlockingQueue<TestReceiverPacketOsRouting> messageQueue;

        public ServiceSocketMessageHandler(BlockingQueue<TestReceiverPacketOsRouting> messageQueue) {
            this.messageQueue = messageQueue;
        }

        @Override
        public void run() {
            while (active) {
                try {
                    TestReceiverPacketOsRouting currentPacket = this.messageQueue.take();
                    SDNControllerMessage controllerMessage = currentPacket.getControllerMessage();

                    int seqNumber = controllerMessage.getSeqNumber();
                    int testRepetition = controllerMessage.getTestRepetition();

                    /*
                     * The number 138 is the size in byte of the object without payload field set.
                     */
                    int payloadSize = E2EComm.objectSize(controllerMessage);
                    String packetInfo = "ServiceSocketMessageHandler Packet " + seqNumber + ", via " + ", payloadSize " + payloadSize + ", from " + currentPacket.getSocketAddress().toString();
                    receivedMessages.addElement(packetInfo);
                    System.out.println("SDNControllerClient.ServiceSocketMessageHandler message: " + packetInfo);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class DatagramSocketListener extends Thread {

        private DatagramSocket socket;
        private String protocol;

        private DatagramSocketListener(DatagramSocket socket, String protocol) {
            this.socket = socket;
            this.protocol = protocol;
        }

        @Override
        public void run() {
            System.out.println("SDNControllerClient: DatagramSocket receiving " + protocol + " messages from the sender (port: " + socket.getLocalPort() + ")");

            while (active) {
                try {
                    /*
                     * Receive
                     */
                    byte[] udpBuffer = new byte[GenericPacket.MAX_UDP_PACKET];
                    DatagramPacket dp = new DatagramPacket(udpBuffer, udpBuffer.length);

                    socket.setSoTimeout(5 * 1000);
                    socket.receive(dp);
                    System.out.println("SDNControllerClient" + protocol + ": DatagramSocket new message arrived");
                    osRoutingMessageQueueUDP.put(dp);
                } catch (SocketTimeoutException ste) {

                } catch (IOException e) {

                } catch (Exception e) {

                }
            }
            socket.close();
        }
    }

    public class DatagramSocketMessageHandler extends Thread {
        private final BlockingQueue<DatagramPacket> messageQueue;

        public DatagramSocketMessageHandler(BlockingQueue<DatagramPacket> messageQueue) {
            this.messageQueue = messageQueue;
        }

        @Override
        public void run() {
            while (active) {
                try {
                    DatagramPacket dp = this.messageQueue.take();
                    Object payload = null;
                    try {
                        payload = E2EComm.deserialize(dp.getData());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (payload instanceof SDNControllerMessage) {
                        int seqNumber = ((SDNControllerMessage) payload).getSeqNumber();

                        /*
                         * The number 138 is the size in byte of the object without payload field set.
                         */
                        int payloadSize = E2EComm.objectSize(payload);
                        String packetInfo = "DatagramSocketMessageHandler Packet " + seqNumber + ", via " + ", payloadSize " + payloadSize + ", from " + dp.getSocketAddress().toString();
                        receivedMessages.addElement(packetInfo);
                        System.out.println("SDNControllerClient.DatagramSocketMessageHandler message: " + packetInfo);
                    } else {
                        /*
                         * Received payload is not SDNControllerMessage: do nothing...
                         */
                        System.out.println("SDNControllerClient.DatagramSocketMessageHandler wrong payload: " + payload);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void sleep(int sleepFor) {
        try {
            Thread.sleep(sleepFor * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}