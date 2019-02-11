package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.*;
import it.unibo.deis.lia.ramp.service.management.ServiceDiscovery;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;
import it.unibo.deis.lia.ramp.util.GeneralUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class SDNControllerClient /*extends Thread*/ {

    private static final int TCP = E2EComm.TCP;
    public static int TCP_PORT = 3400;
    private static final int UDP = E2EComm.UDP;
    public static int UDP_PORT = 3401;
    private BoundReceiveSocket serviceSocketTCP;
    private BoundReceiveSocket serviceSocketUDP;
    private Vector<String> receivedMessages;
    private int seqNumber = 0;

    private boolean active = true;
    private static SDNControllerClient SDNControllerClient = null;
    private static ControllerClient controllerClient = null;
    private static SDNControllerClientJFrame ccjf;

    public static synchronized SDNControllerClient getInstance() {
        if (SDNControllerClient == null) {
            SDNControllerClient = new SDNControllerClient();
            //SDNControllerClient.start();
        }
        ccjf.setVisible(true);
        return SDNControllerClient;
    }

    private SDNControllerClient() {
        System.out.println("SDNControllerClient START");
        controllerClient = ControllerClient.getInstance();
        sleep(2);

        try {
            serviceSocketTCP = E2EComm.bindPreReceive(TCP_PORT, TCP);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverTCP", serviceSocketTCP.getLocalPort(), TCP);

        try {
            serviceSocketUDP = E2EComm.bindPreReceive(UDP_PORT, UDP);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ServiceManager.getInstance(false).registerService("SDNControllerClientReceiverUDP", serviceSocketUDP.getLocalPort(), UDP);
        receivedMessages = new Vector<String>();
        new SDNControllerClient.SocketListener(serviceSocketTCP, "TCP").start();
        new SDNControllerClient.SocketListener(serviceSocketUDP, "UDP").start();
        ccjf = new SDNControllerClientJFrame(this);
    }

    public static boolean isActive() {
        return (SDNControllerClient != null);
    }

    public void stopClient() {
        System.out.println("SDNControllerClient FINISHED");
        ServiceManager.getInstance(false).removeService("SDNControllerClientReceiverTCP");
        ServiceManager.getInstance(false).removeService("SDNControllerClientReceiverUDP");
        active = false;
        try {
            serviceSocketTCP.close();
            serviceSocketUDP.close();
            controllerClient.stopClient();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        controllerClient = null;
        SDNControllerClient = null;
    }

    public Vector<ServiceResponse> findControllerClientReceiver(int protocol, int ttl, int timeout, int serviceAmount) throws Exception {
        String serviceName = "";
        if (protocol == UDP) {
            serviceName = "SDNControllerClientReceiverUDP";
        } else {
            serviceName = "SDNControllerClientReceiverTCP";
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

        return services;
    }

    public FlowPolicy getFlowPolicy() {
        return controllerClient.getFlowPolicy();
    }

    public int getFlowId(ApplicationRequirements applicationRequirements, int[] destNodeIds, int[] destPorts, TopologyGraphSelector.PathSelectionMetric pathSelectionMetric) {
        return controllerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, pathSelectionMetric);
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

    public int getNextSeqNumber() {
        seqNumber++;
        return seqNumber;
    }

    public void sendUnicastMessage(ServiceResponse serviceResponse, int payload, int flowId, int repetitions) {
        new SDNControllerClient.UnicastPacketSender(serviceResponse, payload, flowId, repetitions).start();
    }

    public void sendMulticastMessage(List<Integer> destinations, int payload, int flowId, int protocol, int repetitions) {
        new SDNControllerClient.MulticastPacketSender(destinations, payload, flowId, protocol, repetitions).start();
    }

    public Vector<String> getReceivedMessages() {
        return receivedMessages;
    }

    public void resetReceivedMessages() {
        receivedMessages = new Vector<String>();
    }

//    @Override
//    public void run() {
//        try {
//            System.out.println("SDNControllerClient START");
//            System.out.println("SDNControllerClient: receiving TCP messages from the sender (port: " + serviceSocketTCP.getLocalPort() + ")");
//
//            GenericPacket gp = null;
//            while (active) {
//                try {
//                    // receive
//                    gp = E2EComm.receive(serviceSocketTCP, 5 * 1000);
//                    System.out.println("SDNControllerClient: new message arrived");
//                    new SDNControllerClient.MessageHandler(gp).start();
//                } catch (SocketTimeoutException ste) {
//                    //
//                }
//            }
//            serviceSocketTCP.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        System.out.println("SDNControllerClient FINISHED");
//    }

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

            for(int i=0; i<repetitions; i++) {
                SDNControllerMessage packet = new SDNControllerMessage(getNextSeqNumber(), payload);
                System.out.println("SDNControllerClient: sending packet \""
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

            System.out.println("ControllerClientTestSender: unicast message/s sent to the receivers");
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

            for(int i=0; i<this.repetitions; i++) {
                SDNControllerMessage packet = new SDNControllerMessage(getNextSeqNumber(), payload);
                System.out.println("ControllerClientTestSender: sending message \""
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
            System.out.println("ControllerClientTestSender: multicast message sent to the receivers");
        }
    }

    private class SocketListener extends Thread {

        private BoundReceiveSocket socket;
        private String protocol;

        private SocketListener(BoundReceiveSocket socket, String protocol) {
            this.socket = socket;
            this.protocol = protocol;
        }

        @Override
        public void run() {
            System.out.println("SDNControllerClient: receiving " + protocol + " messages from the sender (port: " + socket.getLocalPort() + ")");

            GenericPacket gp = null;
            while (active) {
                try {
                    // receive
                    gp = E2EComm.receive(socket, 5 * 1000);
                    System.out.println("SDNControllerClient" + protocol + ": new message arrived");
                    new SDNControllerClient.MessageHandler(gp).start();
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

    private class MessageHandler extends Thread {
        private GenericPacket gp;

        private MessageHandler(GenericPacket gp) {
            this.gp = gp;
        }

        @Override
        public void run() {
            try {
                // check packet type
                if (gp instanceof UnicastPacket) {
                    // check payload
                    UnicastPacket up = (UnicastPacket) gp;
                    Object payload = E2EComm.deserialize(up.getBytePayload());
                    if (payload instanceof SDNControllerMessage) {
                        int seqNumber = ((SDNControllerMessage) payload).getSeqNumber();
                        int payloadSize = ((SDNControllerMessage) payload).getPayloadSize();
                        String packetInfo = "Packet " + seqNumber + ", via " + ", payloadSize " + payloadSize + ", from " + up.getSourceNodeId();
                        receivedMessages.addElement(packetInfo);
                        System.out.println("SDNControllerClient.MessageHandler message: " + packetInfo);
                    } else {
                        // received payload is not String: do nothing...
                        System.out.println("SDNControllerClient.MessageHandler wrong payload: " + payload);
                    }
                } else {
                    // received packet is not UnicastPacket: do nothing...
                    System.out.println("SDNControllerClient.MessageHandler wrong packet: " + gp.getClass().getName());
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
