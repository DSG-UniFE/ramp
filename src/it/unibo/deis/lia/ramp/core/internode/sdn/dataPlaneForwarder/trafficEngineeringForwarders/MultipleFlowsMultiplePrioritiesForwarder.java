package it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.trafficEngineeringForwarders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClientInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.DataPlaneForwarder;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Monetenero
 */
public class MultipleFlowsMultiplePrioritiesForwarder implements DataPlaneForwarder {

    private static MultipleFlowsMultiplePrioritiesForwarder trafficShapingForwarder = null;

    private UpdateManager updateManager;

    private ControllerClientInterface controllerClient = null;

    /**
     * Control flow ID value, to be used for control communications between ControllerService and ControllerClients.
     */
    private static final int CONTROL_FLOW_ID = 0;

    /**
     * Control flow ID priority value always has the maximum priority.
     */
    private static final int CONTROL_FLOW_ID_PRIORITY = 0;

    private Map<Integer, Map<Integer, Integer>> prioritiesFlowIdsSentPackets;

    private Map<Integer, Map<Integer, Integer>> previousPrioritiesFlowIdsSentPackets;

    private Map<Integer, Integer> previousPrioritiesTotalFlows;

    private Map<Integer, Integer> previousPrioritiesTotalSentPackets;

    private Map<NetworkInterface, Long> lastPacketSendStartTimes;

    private Map<NetworkInterface, List<Long>> lastFivePacketsSendStartTimesLists;

    private Map<NetworkInterface, Double> lastPacketSendDurations;

    private Map<String, NetworkInterface> networkInterfaces;

    private Map<NetworkInterface, Long> networkSpeeds;

    /**
     * Data structure for throughput file building
     */
    private Map<Integer, Integer> lowPriorityFlowNumbers;

    public synchronized static MultipleFlowsMultiplePrioritiesForwarder getInstance(DataPlaneForwarder routingForwarder) {
        if (trafficShapingForwarder == null) {
            trafficShapingForwarder = new MultipleFlowsMultiplePrioritiesForwarder();
            trafficShapingForwarder.updateManager = new UpdateManager();
            trafficShapingForwarder.updateManager.start();

            trafficShapingForwarder.prioritiesFlowIdsSentPackets = new ConcurrentHashMap<>();
            trafficShapingForwarder.previousPrioritiesFlowIdsSentPackets = new ConcurrentHashMap<>();
            trafficShapingForwarder.previousPrioritiesTotalFlows = new ConcurrentHashMap<>();
            trafficShapingForwarder.previousPrioritiesTotalSentPackets = new ConcurrentHashMap<>();
            trafficShapingForwarder.lastPacketSendStartTimes = new ConcurrentHashMap<>();
            trafficShapingForwarder.lastFivePacketsSendStartTimesLists = new ConcurrentHashMap<>();
            trafficShapingForwarder.lastPacketSendDurations = new ConcurrentHashMap<>();
            trafficShapingForwarder.networkInterfaces = new ConcurrentHashMap<>();
            trafficShapingForwarder.networkSpeeds = new ConcurrentHashMap<>();

            trafficShapingForwarder.lowPriorityFlowNumbers = new ConcurrentHashMap<>();
            Dispatcher.getInstance(false).addPacketForwardingListenerBeforeAnother(trafficShapingForwarder, routingForwarder);
            System.out.println("MultipleFlowsMultiplePrioritiesForwarder ENABLED");

            File outputFile = new File("output_internal.csv");
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }
        return trafficShapingForwarder;
    }

    @Override
    public void deactivate() {
        if (trafficShapingForwarder != null) {
            Dispatcher.getInstance(false).removePacketForwardingListener(trafficShapingForwarder);
            trafficShapingForwarder.updateManager.stopUpdateManager();
            trafficShapingForwarder = null;
            System.out.println("MultipleFlowsMultiplePrioritiesForwarder DISABLED");
        }
    }

    private NetworkInterface getNextSendNetworkInterface(String nextAddress) {
        NetworkInterface networkInterface = this.networkInterfaces.get(nextAddress);
        if (networkInterface == null) {
            try {
                for (String localAddress : Dispatcher.getLocalNetworkAddresses()) {
                    InetAddress localInetAddress = InetAddress.getByName(localAddress);
                    NetworkInterface localNetworkInterface = NetworkInterface.getByInetAddress(localInetAddress);
                    short networkPrefixLength = localNetworkInterface.getInterfaceAddresses().get(0).getNetworkPrefixLength();
                    String completeLocalAddress = localAddress + "/" + networkPrefixLength;
                    SubnetUtils subnetUtils = new SubnetUtils(completeLocalAddress);
                    SubnetInfo subnetInfo = subnetUtils.getInfo();
                    if (subnetInfo.isInRange(nextAddress)) {
                        networkInterface = localNetworkInterface;
                        this.networkInterfaces.put(nextAddress, networkInterface);
                    }
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return networkInterface;
    }

    private long getNetworkSpeed(NetworkInterface networkInterface) {
        Long networkSpeed = this.networkSpeeds.get(networkInterface);
        if (networkSpeed == null) {
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
            NetworkIF[] networkIFs = hardwareAbstractionLayer.getNetworkIFs();
            for (int i = 0; i < networkIFs.length; i++)
                if (networkIFs[i].getName().equals(networkInterface.getName())) {
                    networkSpeed = networkIFs[i].getSpeed();
                    /*
                     * If the Operating System is Linux, convert from Mb/s to b/s
                     */
                    if (SystemInfo.getCurrentPlatformEnum().equals(PlatformEnum.LINUX))
                        networkSpeed = networkSpeed * 1000000;
                    /*
                     * If no valid speed value has been found, assign a default value
                     */
                    if (networkSpeed == 0)
                        networkSpeed = 10000000L;
                    networkSpeed = networkSpeed / 8;
                    networkSpeeds.put(networkInterface, networkSpeed);
                }
        }
        return networkSpeed;
    }

    private synchronized long getAverageInterPacketTime(NetworkInterface networkInterface) {
        long[] interPacketTimes = new long[4];
        List<Long> lastFivePacketsStartTimes = this.lastFivePacketsSendStartTimesLists.get(networkInterface);
        long averageInterPacketTime = 0;
        if (lastFivePacketsStartTimes != null && lastFivePacketsStartTimes.size() == interPacketTimes.length + 1) {
            long totalInterPacketTime = 0;
            for (int i = 0; i < interPacketTimes.length; i++) {
                interPacketTimes[i] = lastFivePacketsStartTimes.get(i) - lastFivePacketsStartTimes.get(i + 1);
                totalInterPacketTime = totalInterPacketTime + interPacketTimes[i];
            }
            averageInterPacketTime = totalInterPacketTime / interPacketTimes.length;
        }
        return averageInterPacketTime;
    }

    private synchronized int getSendProbability(int flowId, int flowPriority) {
        int sendProbability = 100;
        int totalFlows = 0;
        for (Map<Integer, Integer> priorityFlowIdsSentPackets : this.prioritiesFlowIdsSentPackets.values())
            totalFlows = totalFlows + priorityFlowIdsSentPackets.keySet().size();
        if (totalFlows > 1) {
            Map<Integer, Integer> priorityFlowIdsSentPackets = this.prioritiesFlowIdsSentPackets.get(flowPriority);
            Map<Integer, Integer> previousPriorityFlowIdsSentPackets = this.previousPrioritiesFlowIdsSentPackets.get(flowPriority);
            if (previousPriorityFlowIdsSentPackets != null) {
                Integer previousSentPackets = previousPriorityFlowIdsSentPackets.get(flowId);
                if (previousSentPackets != null && priorityFlowIdsSentPackets != null && priorityFlowIdsSentPackets.keySet().size() > 1) {
                    int previousPriorityTotalFlows = this.previousPrioritiesTotalFlows.get(flowPriority);
                    int sentPacketsTarget = this.previousPrioritiesTotalSentPackets.get(flowPriority) / previousPriorityTotalFlows;
                    int sentPacketsOffset = previousSentPackets - sentPacketsTarget;
                    int offsetPercentage = (sentPacketsOffset * 100) / sentPacketsTarget;
                    sendProbability = sendProbability - (offsetPercentage * previousPriorityTotalFlows);
                }
            }
            int higherPriorityTotalFlows = 0;
            for (int i = 0; i < flowPriority; i++) {
                Map<Integer, Integer> higherPriorityFlowIdsSentPackets = this.prioritiesFlowIdsSentPackets.get(i);
                if (higherPriorityFlowIdsSentPackets != null)
                    higherPriorityTotalFlows = higherPriorityTotalFlows + higherPriorityFlowIdsSentPackets.keySet().size();
            }
            if (higherPriorityTotalFlows > 0)
                sendProbability = sendProbability / (flowPriority + 1);
        }
        return sendProbability;
    }

    private synchronized void resetPrioritiesFlowIdsSentPackets() {
        this.previousPrioritiesFlowIdsSentPackets.clear();
        this.previousPrioritiesTotalFlows.clear();
        this.previousPrioritiesTotalSentPackets.clear();
        for (Integer priorityValue : this.prioritiesFlowIdsSentPackets.keySet()) {
            Map<Integer, Integer> priorityFlowIdsSentPackets = this.prioritiesFlowIdsSentPackets.get(priorityValue);
            Map<Integer, Integer> previousPriorityFlowIdsSentPackets = new ConcurrentHashMap<>();
            int previousPriorityTotalSentPackets = 0;
            for (Integer flowId : priorityFlowIdsSentPackets.keySet()) {
                int flowIdSentPackets = priorityFlowIdsSentPackets.get(flowId);
                previousPriorityFlowIdsSentPackets.put(flowId, flowIdSentPackets);
                previousPriorityTotalSentPackets = previousPriorityTotalSentPackets + flowIdSentPackets;
            }
            this.previousPrioritiesFlowIdsSentPackets.put(priorityValue, previousPriorityFlowIdsSentPackets);
            this.previousPrioritiesTotalFlows.put(priorityValue, priorityFlowIdsSentPackets.keySet().size());
            this.previousPrioritiesTotalSentPackets.put(priorityValue, previousPriorityTotalSentPackets);
            priorityFlowIdsSentPackets.clear();
        }
    }

    @Override
    public void receivedUdpUnicastPacket(UnicastPacket up) {
        receivedTcpUnicastPacket(up);
    }

    @Override
    public void receivedUdpBroadcastPacket(BroadcastPacket bp) {

    }

    @Override
    public void receivedTcpUnicastPacket(UnicastPacket up) {
        if (controllerClient == null) {
            controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
        }

        /*
         * Check if the current packet contains a valid flowId and has to be
         * processed according to the SDN paradigm
         */
        int flowId = up.getFlowId();

        if (flowId != GenericPacket.UNUSED_FIELD && flowId != CONTROL_FLOW_ID && up.getDestNodeId() != Dispatcher.getLocalRampId()) {
            int flowPriority = controllerClient.getFlowPriority(flowId);

            NetworkInterface nextSendNetworkInterface = getNextSendNetworkInterface(up.getDest()[up.getCurrentHop()]);
            long networkSpeed = getNetworkSpeed(nextSendNetworkInterface);
            /*
             * If the packet is the first to arrive, save the send information and occupy the transmission channel
             */
            if (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null) {
                synchronized (this) {
                    // double sendDuration = ((double) up.getBytePayload().length / networkSpeed) * 1000;
                    double sendDuration = getAverageInterPacketTime(nextSendNetworkInterface) * 1.25;
                    if (sendDuration < 25)
                        sendDuration = 25;
                    this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
                    this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
                    List<Long> lastFivePacketsSendStartTimes = this.lastFivePacketsSendStartTimesLists.get(nextSendNetworkInterface);
                    if (lastFivePacketsSendStartTimes == null)
                        lastFivePacketsSendStartTimes = new ArrayList<>();
                    lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
                    if (lastFivePacketsSendStartTimes.size() == 6)
                        lastFivePacketsSendStartTimes.remove(5);
                    this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
                    Map<Integer, Integer> priorityFlowIdsSentPackets = new ConcurrentHashMap<>();
                    priorityFlowIdsSentPackets.put(flowId, 1);
                    this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
                }
                System.out.println("MultipleFlowsMultiplePrioritiesForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " is the first to reach the channel, no changes made to it");
            }
            /*
             * If the packet has a priority value, get the elapsed time since the last send start
             * and get the send probability
             */
            else {
                long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                int sendProbability = getSendProbability(flowId, flowPriority);
                int randomNumber = ThreadLocalRandom.current().nextInt(100);
                /*
                 * If the packet is not allowed to proceed, wait and retry
                 */
                while (elapsed < this.lastPacketSendDurations.get(nextSendNetworkInterface) && randomNumber > sendProbability) {
                    elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                    // long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
                    long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                    if (timeToWait > 4000)
                        timeToWait = 4000;
                    if (timeToWait < 10)
                        timeToWait = 10;
                    System.out.println("MultipleFlowsMultiplePrioritiesForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " and priority value " + flowPriority + " has not been sent, waiting " + timeToWait + " milliseconds and retrying");
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    sendProbability = getSendProbability(flowId, flowPriority);
                    randomNumber = ThreadLocalRandom.current().nextInt(100);
                }
                synchronized (this) {
                    double sendDuration = getAverageInterPacketTime(nextSendNetworkInterface) * 1.25;
                    if (sendDuration < 25)
                        sendDuration = 25;
                    this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
                    this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
                    List<Long> lastFivePacketsSendStartTimes = this.lastFivePacketsSendStartTimesLists.get(nextSendNetworkInterface);
                    if (lastFivePacketsSendStartTimes == null)
                        lastFivePacketsSendStartTimes = new ArrayList<>();
                    lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
                    if (lastFivePacketsSendStartTimes.size() == 6)
                        lastFivePacketsSendStartTimes.remove(5);
                    this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
                    Map<Integer, Integer> priorityFlowIdsSentPackets = this.prioritiesFlowIdsSentPackets.get(flowPriority);
                    Map<Integer, Integer> previousPriorityFlowIdsSentPackets = this.previousPrioritiesFlowIdsSentPackets.get(flowPriority);
                    if (priorityFlowIdsSentPackets == null) {
                        priorityFlowIdsSentPackets = new ConcurrentHashMap<>();
                        this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
                    }
                    if (priorityFlowIdsSentPackets.containsKey(flowId))
                        priorityFlowIdsSentPackets.put(flowId, priorityFlowIdsSentPackets.get(flowId) + 1);
                    else if (previousPriorityFlowIdsSentPackets != null && previousPriorityFlowIdsSentPackets.containsKey(flowId)) {
                        int sentPackets = (previousPriorityFlowIdsSentPackets.get(flowId) / 2) + 1;
                        priorityFlowIdsSentPackets.put(flowId, sentPackets);
                    } else
                        priorityFlowIdsSentPackets.put(flowId, 1);
                }
                System.out.println("MultipleFlowsMultiplePrioritiesForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " and priority value " + flowPriority + ", no changes made to it");
            }

            /*
             * Log all network traffic handled with this method
             */
            log(flowId, flowPriority, up.getBytePayload().length);
        }

        if (flowId == CONTROL_FLOW_ID) {
            log(CONTROL_FLOW_ID, CONTROL_FLOW_ID_PRIORITY, up.getBytePayload().length);
        }
    }

    @Override
    public void receivedTcpUnicastHeader(UnicastHeader uh) {

    }

    @Override
    public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
        if (controllerClient == null) {
            controllerClient = ((ControllerClientInterface) ComponentLocator.getComponent(ComponentType.CONTROLLER_CLIENT));
        }

        /*
         * Check if the current packet contains a valid flowId and has to be
         * processed according to the SDN paradigm
         */
        int flowId = uh.getFlowId();

        if (flowId != GenericPacket.UNUSED_FIELD && flowId != CONTROL_FLOW_ID && uh.getDestNodeId() != Dispatcher.getLocalRampId()) {
            int flowPriority = controllerClient.getFlowPriority(flowId);

            NetworkInterface nextSendNetworkInterface = getNextSendNetworkInterface(uh.getDest()[uh.getCurrentHop()]);
            int packetLength = 0;
            if (len <= payload.length)
                packetLength = len;
            else
                packetLength = payload.length;
            long networkSpeed = getNetworkSpeed(nextSendNetworkInterface);
            /*
             * If the packet is the first to arrive, save the send information and occupy the transmission channel
             */
            if (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null) {
                synchronized (this) {
                    double sendDuration = getAverageInterPacketTime(nextSendNetworkInterface) * 1.25;
                    if (sendDuration < 25)
                        sendDuration = 25;
                    this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
                    this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
                    List<Long> lastFivePacketsSendStartTimes = this.lastFivePacketsSendStartTimesLists.get(nextSendNetworkInterface);
                    if (lastFivePacketsSendStartTimes == null)
                        lastFivePacketsSendStartTimes = new ArrayList<>();
                    lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
                    if (lastFivePacketsSendStartTimes.size() == 6)
                        lastFivePacketsSendStartTimes.remove(5);
                    this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
                    Map<Integer, Integer> priorityFlowIdsSentPackets = new ConcurrentHashMap<>();
                    priorityFlowIdsSentPackets.put(flowId, 1);
                    this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
                }
                System.out.println("MultipleFlowsMultiplePrioritiesForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " is the first to reach the channel, no changes made to it");
            }
            /*
             * If the packet has a priority value, get the elapsed time since the last send start and the send probability
             */
            else {
                long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                int sendProbability = getSendProbability(flowId, flowPriority);
                int randomNumber = ThreadLocalRandom.current().nextInt(100);
                /*
                 * If the packet is not allowed to proceed, wait and retry
                 */
                while (elapsed < this.lastPacketSendDurations.get(nextSendNetworkInterface) && randomNumber > sendProbability) {
                    elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);

                    long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                    if (timeToWait > 4000)
                        timeToWait = 4000;
                    if (timeToWait < 20)
                        timeToWait = 20;
                    System.out.println("MultipleFlowsMultiplePrioritiesForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " and priority value " + flowPriority + " has not been sent, waiting " + timeToWait + " milliseconds and retrying");
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    sendProbability = getSendProbability(flowId, flowPriority);
                    randomNumber = ThreadLocalRandom.current().nextInt(100);
                }
                synchronized (this) {
                    // double sendDuration = ((double) packetLength / networkSpeed) * 1000;
                    double sendDuration = getAverageInterPacketTime(nextSendNetworkInterface) * 1.25;
                    if (sendDuration < 25)
                        sendDuration = 25;
                    this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
                    this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
                    List<Long> lastFivePacketsSendStartTimes = this.lastFivePacketsSendStartTimesLists.get(nextSendNetworkInterface);
                    if (lastFivePacketsSendStartTimes == null)
                        lastFivePacketsSendStartTimes = new ArrayList<>();
                    lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
                    if (lastFivePacketsSendStartTimes.size() == 6)
                        lastFivePacketsSendStartTimes.remove(5);
                    this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
                    Map<Integer, Integer> priorityFlowIdsSentPackets = this.prioritiesFlowIdsSentPackets.get(flowPriority);
                    Map<Integer, Integer> previousPriorityFlowIdsSentPackets = this.previousPrioritiesFlowIdsSentPackets.get(flowPriority);
                    if (priorityFlowIdsSentPackets == null) {
                        priorityFlowIdsSentPackets = new ConcurrentHashMap<>();
                        this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
                    }
                    if (priorityFlowIdsSentPackets.containsKey(flowId))
                        priorityFlowIdsSentPackets.put(flowId, priorityFlowIdsSentPackets.get(uh.getFlowId()) + 1);
                    else if (previousPriorityFlowIdsSentPackets != null && previousPriorityFlowIdsSentPackets.containsKey(flowId)) {
                        int sentPackets = (previousPriorityFlowIdsSentPackets.get(flowId) / 2) + 1;
                        priorityFlowIdsSentPackets.put(flowId, sentPackets);
                    } else
                        priorityFlowIdsSentPackets.put(flowId, 1);
                }
                System.out.println("MultipleFlowsMultiplePrioritiesForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " and priority value " + flowPriority + ", no changes made to it");
            }
            /*
             * Log all network traffic handled with this method
             */
            log(flowId, flowPriority, packetLength);
        }

        if (flowId == CONTROL_FLOW_ID) {
            int packetLength = Math.min(len, payload.length);
            log(CONTROL_FLOW_ID,CONTROL_FLOW_ID_PRIORITY, packetLength);
        }
    }

    @Override
    public void receivedTcpBroadcastPacket(BroadcastPacket bp) {

    }

    @Override
    public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {

    }

    @Override
    public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {

    }

    private void log(int flowId, int flowPriority, int packetLength) {
        File outputFile = new File("output_internal.csv");
        PrintWriter printWriter = null;
        if (!outputFile.exists()) {
            try {
                printWriter = new PrintWriter(outputFile);
                printWriter.println("timestamp,controlpriority sentbytes,maxpriority_sentbytes,lowpriority_1_sentbytes,lowpriority_2_sentbytes");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            try {
                printWriter = new PrintWriter(new FileWriter(outputFile, true));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        LocalDateTime localDateTime = LocalDateTime.now();
        String timestamp = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        assert printWriter != null;

        if (flowPriority == 0) {
            printWriter.println(timestamp + "," + packetLength + ",,,");
        }
        else if (flowPriority == 1) {
            printWriter.println(timestamp + ",," + packetLength + ",,");
        }
        else if (flowPriority == 3) {
            Integer flowNumber = this.lowPriorityFlowNumbers.get(flowId);
            if (flowNumber == null) {
                flowNumber = this.lowPriorityFlowNumbers.size() + 1;
                this.lowPriorityFlowNumbers.put(flowId, flowNumber);
            }
            if (flowNumber == 1)
                printWriter.println(timestamp + ",,," + packetLength + ",");
            else if (flowNumber == 2)
                printWriter.println(timestamp + ",,,," + packetLength);
        }

        printWriter.close();
    }

    private static class UpdateManager extends Thread {

        private static final int TIME_INTERVAL = 2 * 1000;

        private boolean active;

        UpdateManager() {
            this.active = true;
        }

        public void stopUpdateManager() {
            this.active = false;
        }

        public void run() {
            while (this.active) {
                try {
                    Thread.sleep(TIME_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(trafficShapingForwarder != null) {
                    trafficShapingForwarder.resetPrioritiesFlowIdsSentPackets();
                }
            }
        }
    }

}
