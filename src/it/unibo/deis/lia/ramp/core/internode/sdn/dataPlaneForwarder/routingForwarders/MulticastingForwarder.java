package it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.routingForwarders;

import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClientInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.DataPlaneForwarder;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.MulticastPathDescriptor;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class MulticastingForwarder implements DataPlaneForwarder {

    private static MulticastingForwarder multicastingForwarder = null;

    /**
     * Control flow ID value, to be used for control communications between ControllerService and ControllerClients.
     */
    private static final int CONTROL_FLOW_ID = 0;

    private ControllerClientInterface controllerClient = null;

    /**
     * Data structure for throughput file building
     */
    private Map<Integer, Integer> highestPriorityFlowNumbers;

    public synchronized static MulticastingForwarder getInstance() {
        if (multicastingForwarder == null) {
            multicastingForwarder = new MulticastingForwarder();

            multicastingForwarder.highestPriorityFlowNumbers = new ConcurrentHashMap<>();
            Dispatcher.getInstance(false).addPacketForwardingListener(multicastingForwarder);
            System.out.println("MulticastingForwarder ENABLED");
        }
        return multicastingForwarder;
    }

    @Override
    public void deactivate() {
        if (multicastingForwarder != null) {
            Dispatcher.getInstance(false).removePacketForwardingListener(multicastingForwarder);
            multicastingForwarder = null;
            System.out.println("MulticastingForwarder DISABLED");
        }
    }

    @Override
    public void receivedUdpUnicastPacket(UnicastPacket up) {
        int flowId = up.getFlowId();
        if (flowId != GenericPacket.UNUSED_FIELD && flowId != CONTROL_FLOW_ID && up.getDestNodeId() == Dispatcher.getLocalRampId() && up.getDestPort() == 40000) {
            if(controllerClient == null) {
                controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
            }

            List<PathDescriptor> nextHops = controllerClient.getFlowMulticastNextHops(flowId);
            if (nextHops != null) {
                String[] currentDest = new String[up.getDest().length];
                for (int i = 0; i < up.getDest().length; i++)
                    currentDest[i] = up.getDest()[i];

                for (PathDescriptor pathDescriptor : nextHops) {
                    UnicastPacket duplicatePacket = null;
                    MulticastPathDescriptor multicastPathDescriptor = (MulticastPathDescriptor) pathDescriptor;
                    if (multicastPathDescriptor.getPathNodeIds().get(0) == Dispatcher.getLocalRampId()) {
                        System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " is directed to this node, duplicating it and setting the destination port");
                        try {
                            duplicatePacket = new UnicastPacket(
                                    up.getDest(),
                                    multicastPathDescriptor.getDestPort(),
                                    up.getDestNodeId(),
                                    up.getSourceNodeId(),
                                    up.isAck(),
                                    up.getSourcePortAck(),
                                    up.getCurrentHop(),
                                    up.getBufferSize(),
                                    up.getRetry(),
                                    up.getTimeWait(),
                                    up.getExpiry(),
                                    up.getConnectTimeout(),
                                    flowId,
                                    up.getBytePayload()
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        String[] duplicatePacketDest = new String[currentDest.length+1];
                        for (int i = 0; i < currentDest.length; i++)
                            duplicatePacketDest[i] = currentDest[i];
                        duplicatePacketDest[currentDest.length] = multicastPathDescriptor.getPath()[0];
                        int duplicatePacketDestNodeId = multicastPathDescriptor.getPathNodeIds().get(0);
                        System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " has to be forwarded to node " + duplicatePacketDestNodeId + ", duplicating and sending it");
                        try {
                            duplicatePacket = new UnicastPacket(
                                    duplicatePacketDest,
                                    up.getDestPort(),
                                    duplicatePacketDestNodeId,
                                    up.getSourceNodeId(),
                                    up.isAck(),
                                    up.getSourcePortAck(),
                                    up.getCurrentHop(),
                                    up.getBufferSize(),
                                    up.getRetry(),
                                    up.getTimeWait(),
                                    up.getExpiry(),
                                    up.getConnectTimeout(),
                                    flowId,
                                    up.getBytePayload()
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        E2EComm.sendUnicast(E2EComm.UDP, duplicatePacket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                up.setDest(null);
                up.setRetry((byte) 0);
            }
        }
    }

    @Override
    public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
        // TODO Auto-generated method stub

    }

    @Override
    public void receivedTcpUnicastPacket(UnicastPacket up) {
        int flowId = up.getFlowId();
        if (flowId != GenericPacket.UNUSED_FIELD && flowId != CONTROL_FLOW_ID && up.getDestNodeId() == Dispatcher.getLocalRampId() && up.getDestPort() == 40000) {
            if(controllerClient == null) {
                controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
            }

            List<PathDescriptor> nextHops = controllerClient.getFlowMulticastNextHops(flowId);
            if (nextHops != null) {
                String[] currentDest = new String[up.getDest().length];
                for (int i = 0; i < up.getDest().length; i++)
                    currentDest[i] = up.getDest()[i];

                for (PathDescriptor pathDescriptor : nextHops) {
                    UnicastPacket duplicatePacket = null;
                    MulticastPathDescriptor multicastPathDescriptor = (MulticastPathDescriptor) pathDescriptor;
                    System.out.println(multicastPathDescriptor.getPath()[0] + " " + multicastPathDescriptor.getPathNodeIds().get(0));
                    if (multicastPathDescriptor.getPathNodeIds().get(0) == Dispatcher.getLocalRampId()) {
                        System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " is directed to this node, duplicating it and setting the destination port");
                        try {
                            duplicatePacket = new UnicastPacket(
                                    up.getDest(),
                                    multicastPathDescriptor.getDestPort(),
                                    up.getDestNodeId(),
                                    up.getSourceNodeId(),
                                    up.isAck(),
                                    up.getSourcePortAck(),
                                    up.getCurrentHop(),
                                    up.getBufferSize(),
                                    up.getRetry(),
                                    up.getTimeWait(),
                                    up.getExpiry(),
                                    up.getConnectTimeout(),
                                    up.getFlowId(),
                                    up.getBytePayload()
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        String[] duplicatePacketDest = new String[currentDest.length+1];
                        for (int i = 0; i < currentDest.length; i++)
                            duplicatePacketDest[i] = currentDest[i];
                        duplicatePacketDest[currentDest.length] = multicastPathDescriptor.getPath()[0];
                        int duplicatePacketDestNodeId = multicastPathDescriptor.getPathNodeIds().get(0);
                        System.out.println("MulticastingForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " has to be forwarded to node " + duplicatePacketDestNodeId + ", duplicating and sending it");
                        try {
                            duplicatePacket = new UnicastPacket(
                                    duplicatePacketDest,
                                    up.getDestPort(),
                                    duplicatePacketDestNodeId,
                                    up.getSourceNodeId(),
                                    up.isAck(),
                                    up.getSourcePortAck(),
                                    up.getCurrentHop(),
                                    up.getBufferSize(),
                                    up.getRetry(),
                                    up.getTimeWait(),
                                    up.getExpiry(),
                                    up.getConnectTimeout(),
                                    flowId,
                                    up.getBytePayload()
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        E2EComm.sendUnicast(E2EComm.TCP, duplicatePacket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                up.setDest(null);
                up.setRetry((byte) 0);
            }
        }
        if (up.getFlowId() != GenericPacket.UNUSED_FIELD && up.getDestNodeId() != Dispatcher.getLocalRampId()) {
            File outputFile = new File("output_internal.csv");
            // if (flowPriority == 0)
            // 	outputFile = new File("output_internal_maxpriority.csv");
            // else
            // 	outputFile = new File("output_internal_lowpriority.csv");
            PrintWriter printWriter = null;
            if (!outputFile.exists()) {
                try {
                    printWriter = new PrintWriter(outputFile);
                    printWriter.println("timestamp,firstflow_sentbytes,secondflow_sentbytes,thirdflow_sentbytes");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    printWriter = new PrintWriter(new FileWriter(outputFile, true));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            LocalDateTime localDateTime = LocalDateTime.now();
            String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            int packetLength = E2EComm.objectSizePacket(up);
            Integer flowNumber = this.highestPriorityFlowNumbers.get(flowId);
            if (flowNumber == null) {
                flowNumber = this.highestPriorityFlowNumbers.size() + 1;
                this.highestPriorityFlowNumbers.put(flowId, flowNumber);
            }
            if (flowNumber == 1)
                printWriter.println(timestamp + "," + packetLength + ",,");
            else if (flowNumber == 2)
                printWriter.println(timestamp + ",," + packetLength + ",");
            else if (flowNumber == 3)
                printWriter.println(timestamp + ",,," + packetLength);
            printWriter.close();
        }
    }

    @Override
    public void receivedTcpUnicastHeader(UnicastHeader uh) {
        // TODO Auto-generated method stub

    }

    @Override
    public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
        // TODO Auto-generated method stub

    }

    @Override
    public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
        // TODO Auto-generated method stub

    }

    @Override
    public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
        // TODO Auto-generated method stub

    }
}
