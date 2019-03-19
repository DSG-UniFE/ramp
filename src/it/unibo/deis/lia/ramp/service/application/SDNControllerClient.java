package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Vector;
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
    private Vector<String> receivedMessages;
    private int seqNumber = 0;
    private Vector<ServiceResponse> availableServices;

    private boolean active = true;
    private static SDNControllerClient SDNControllerClient = null;
    private static ControllerClient controllerClient = null;
    private static SDNControllerClientJFrame ccjf;

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
            this.serverSocketTCP = new ServerSocket();
            this.serverSocketTCP.setReuseAddress(true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverServerSocketTCP", serverSocketTCP.getLocalPort(), TCP);

        try {
            this.datagramSocketUDP = new DatagramSocket();
            this.datagramSocketUDP.setReuseAddress(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverDatagramSocketUDP", datagramSocketUDP.getLocalPort(), UDP);

        receivedMessages = new Vector<>();
        new SDNControllerClient.BoundReceiveSocketListener(receiveSocketTCP, "TCP").start();
        new SDNControllerClient.BoundReceiveSocketListener(receiveSocketUDP, "UDP").start();
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

    public Vector<ServiceResponse> findControllerClientReceiver(int protocol, int ttl, int timeout, int serviceAmount) throws Exception {
        String serviceName = "";
        if (getTrafficEngineeringPolicy() != TrafficEngineeringPolicy.OS_ROUTING) {
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

    public int getFlowId(ApplicationRequirements applicationRequirements, int[] destNodeIds, int[] destPorts, PathSelectionMetric pathSelectionMetric) {
        return controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, pathSelectionMetric);
    }

    public int getRouteId(int destNodeId, int destPort, ApplicationRequirements applicationRequirements, PathSelectionMetric pathSelectionMetric) {
        return controllerClient.getRouteId(destNodeId, destPort, applicationRequirements, pathSelectionMetric);
    }

    public ConcurrentHashMap<Integer, PathDescriptor> getDefaultFlowPath() {
        return controllerClient.getDefaultFlowPath();
    }

    public ConcurrentHashMap<Integer, PathDescriptor> getFlowPath() {
        return controllerClient.getFlowPath();
    }

    public ConcurrentHashMap<Integer, Integer> getFlowPriorities() {
        return controllerClient.getFlowPriorities();
    }

    private int getNextSeqNumber() {
        seqNumber++;
        return seqNumber;
    }

    public void sendUnicastMessage(ServiceResponse serviceResponse, int payload, int flowId, int repetitions) {
        new SDNControllerClient.UnicastPacketSender(serviceResponse, payload, flowId, repetitions).start();
    }

    public void sendMulticastMessage(List<Integer> destinations, int payload, int flowId, int protocol, int repetitions) {
        new SDNControllerClient.MulticastPacketSender(destinations, payload, flowId, protocol, repetitions).start();
    }

    public void sendDatagramSocketMessage(ServiceResponse serviceResponse, int payload, int routeId, int repetitions) {
        new DatagramSocketPacketSender(serviceResponse, payload, routeId, repetitions).start();
    }

    public void sendServiceSocketMessage(ServiceResponse serviceResponse, int payload, int routeId, int repetitions) {
        new ServerSocketPacketSender(serviceResponse, payload, routeId, repetitions).start();
    }

    public Vector<String> getReceivedMessages() {
        return receivedMessages;
    }

    public void resetReceivedMessages() {
        receivedMessages = new Vector<>();
    }

    private int getServicePortByIpAddress(String serviceIp) {
        int servicePort = -1;
        for(ServiceResponse serviceResponse : availableServices) {
            int getServerDestLen = serviceResponse.getServerDest().length;
            String destinationIP = serviceResponse.getServerDest()[getServerDestLen - 1];
            if(destinationIP.equals(serviceIp)) {
                servicePort = serviceResponse.getServerPort();
                break;
            }
        }
        return servicePort;
    }

    private class UnicastPacketSender extends Thread {

        private ServiceResponse serviceResponse;
        private int payload;
        private int flowId;
        private int repetitions;

        private UnicastPacketSender(ServiceResponse serviceResponse, int payload, int flowId, int repetitions) {
            this.serviceResponse = serviceResponse;
            this.payload = payload;
            this.flowId = flowId;
            this.repetitions = repetitions;
        }

        @Override
        public void run() {
            String protocol;
            int serviceResponseProtocol = serviceResponse.getProtocol();
            if (serviceResponseProtocol == UDP) {
                protocol = "UDP";
            } else {
                protocol = "TCP";
            }

            for (int i = 0; i < repetitions; i++) {
                SDNControllerMessage packet = new SDNControllerMessage(getNextSeqNumber(), payload);
                System.out.println("SDNControllerClient.UnicastPacketSender: sending packet \""
                        + packet.getSeqNumber() + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), flowId: " + flowId + ", Protocol: " + protocol);
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
                            E2EComm.serialize(packet)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println("SDNControllerClient.UnicastPacketSender: unicast message/s sent to the receiver");
        }
    }

    private class MulticastPacketSender extends Thread {

        private List<Integer> destinations;
        private int payload;
        private int flowId;
        private int protocol;
        private int repetitions;

        private MulticastPacketSender(List<Integer> destinations, int payload, int flowId, int protocol, int repetitions) {
            this.destinations = destinations;
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
        private DatagramSocket socket;

        private DatagramSocketPacketSender(ServiceResponse serviceResponse, int payload, int routeId, int repetitions) {
            this.serviceResponse = serviceResponse;
            this.payload = payload;
            this.routeId = routeId;
            this.repetitions = repetitions;
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
            int port = getServicePortByIpAddress(destinationIP);

            try {
                this.socket.connect(InetAddress.getByName(destinationIP), port);
            } catch (UnknownHostException e) {

                e.printStackTrace();
            }
            /*
             * TODO Check me and after remove me.
             */
            System.out.println("DMIIIITRIJJJJ: host address " + socket.getLocalAddress().getHostAddress());

            for (int i = 0; i < repetitions; i++) {
                SDNControllerMessage packet = new SDNControllerMessage(getNextSeqNumber(), payload);
                System.out.println("SDNControllerClient.DatagramSocketPacketSender: sending Packet \""
                        + packet.getSeqNumber() + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), IP: " + destinationIP + ", Protocol: TCP");

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
            socket.close();

            System.out.println("SDNControllerClient.DatagramSocketPacketSender: packet/s sent to the receiver");
        }
    }

    private class ServerSocketPacketSender extends Thread {

        private ServiceResponse serviceResponse;
        private int payload;
        private int routeId;
        private int repetitions;
        private Socket socket;

        private ServerSocketPacketSender(ServiceResponse serviceResponse, int payload, int routeId, int repetitions) {
            this.serviceResponse = serviceResponse;
            this.payload = payload;
            this.routeId = routeId;
            this.repetitions = repetitions;
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
            int port = getServicePortByIpAddress(destinationIP);

            try {
                this.socket = new Socket(InetAddress.getByName(destinationIP), port, sourceIpAddress, 0);
            } catch (IOException e) {
                e.printStackTrace();
            }

            OutputStream outputStream = null;
            ObjectOutputStream objectOutputStream = null;
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


            for (int i = 0; i < repetitions; i++) {
                SDNControllerMessage packet = new SDNControllerMessage(getNextSeqNumber(), payload);
                System.out.println("SDNControllerClient.ServerSocketPacketSender: sending Packet \""
                        + packet.getSeqNumber() + "\" to the receiver (nodeId: " + serviceResponse.getServerNodeId() + "), IP: " + destinationIP + ", Protocol: TCP");
                if (objectOutputStream != null) {
                    try {
                        objectOutputStream.writeObject(packet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
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
                    gp = E2EComm.receive(socket, 5 * 1000);
                    System.out.println("SDNControllerClient" + protocol + ": new message arrived");
                    new SDNControllerClient.BoundReceiveSocketMessageHandler(gp).start();
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

    private class BoundReceiveSocketMessageHandler extends Thread {
        private GenericPacket gp;

        private BoundReceiveSocketMessageHandler(GenericPacket gp) {
            this.gp = gp;
        }

        @Override
        public void run() {
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
                        int payloadSize = ((SDNControllerMessage) payload).getPayloadSize();
                        String packetInfo = "BoundReceiveSocketMessageHandler Packet " + seqNumber + ", via " + ", payloadSize " + payloadSize + ", from " + up.getSourceNodeId();
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
                    socket.setSoTimeout(5 * 1000);
                    Socket clientSocket = socket.accept();
                    System.out.println("SDNControllerClient" + protocol + ": ServerSocket new message arrived");
                    new ServiceSocketMessageHandler(clientSocket).start();
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

    private class ServiceSocketMessageHandler extends Thread {
        private Socket s;

        private ServiceSocketMessageHandler(Socket s) {
            this.s = s;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = s.getInputStream();
                ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                Object payload = objectInputStream.readObject();
                if (payload instanceof SDNControllerMessage) {
                    int seqNumber = ((SDNControllerMessage) payload).getSeqNumber();
                    int payloadSize = ((SDNControllerMessage) payload).getPayloadSize();
                    String packetInfo = "ServiceSocketMessageHandler Packet " + seqNumber + ", via " + ", payloadSize " + payloadSize + ", from " + s.getRemoteSocketAddress().toString();
                    receivedMessages.addElement(packetInfo);
                    System.out.println("SDNControllerClient.ServiceSocketMessageHandler message: " + packetInfo);
                } else {
                    /*
                     * Received payload is not SDNControllerMessage: do nothing...
                     */
                    System.out.println("SDNControllerClient.ServiceSocketMessageHandler wrong payload: " + payload);
                }

                s.close();
            } catch (Exception e) {
                e.printStackTrace();
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


            byte[] udpBuffer = new byte[GenericPacket.MAX_UDP_PACKET];
            DatagramPacket dp = new DatagramPacket(udpBuffer, udpBuffer.length);

            while (active) {
                try {
                    /*
                     * Receive
                     */
                    socket.setSoTimeout(5 * 1000);
                    socket.receive(dp);
                    System.out.println("SDNControllerClient" + protocol + ": DatagramSocket new message arrived");
                    new DatagramSocketMessageHandler(dp).start();
                } catch (SocketTimeoutException ste) {
                    //
                } catch (IOException e) {
                    //
                } catch (Exception e) {
                    //
                }
            }
            socket.close();
        }
    }

    private class DatagramSocketMessageHandler extends Thread {
        private DatagramPacket dp;

        private DatagramSocketMessageHandler(DatagramPacket dp) {
            this.dp = dp;
        }

        @Override
        public void run() {
            try {
                Object payload = E2EComm.deserialize(dp.getData());
                if (payload instanceof SDNControllerMessage) {
                    int seqNumber = ((SDNControllerMessage) payload).getSeqNumber();
                    int payloadSize = ((SDNControllerMessage) payload).getPayloadSize();
                    String packetInfo = "DatagramSocketMessageHandler Packet " + seqNumber + ", via " + ", payloadSize " + payloadSize + ", from " + dp.getSocketAddress().toString();
                    receivedMessages.addElement(packetInfo);
                    System.out.println("SDNControllerClient.DatagramSocketMessageHandler message: " + packetInfo);
                } else {
                    /*
                     * Received payload is not SDNControllerMessage: do nothing...
                     */
                    System.out.println("SDNControllerClient.DatagramSocketMessageHandler wrong payload: " + payload);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
