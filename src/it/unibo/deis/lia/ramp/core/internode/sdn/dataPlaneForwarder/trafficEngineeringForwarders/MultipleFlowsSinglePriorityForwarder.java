package it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.trafficEngineeringForwarders;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClientInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.DataPlaneForwarder;

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
 * @author Dmitrij David Padalino Montenero
 */
public class MultipleFlowsSinglePriorityForwarder implements DataPlaneForwarder {

    private static final int MAX_ATTEMPTS = 8000;

    private static MultipleFlowsSinglePriorityForwarder queuesForwarder = null;

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

    /*
     * No need to make private fields static, since access always happens through the singleton
     */
    private Map<Integer, Integer> highestPriorityFlowIdsSentPackets;

    private Map<Integer, Integer> previousHighestPriorityFlowIdsSentPackets;

    private int previousHighestPriorityTotalFlows;

    private int previousHighestPriorityTotalSentPackets;

    private int lastPacketPriority;

    private Map<NetworkInterface, Long> lastPacketSendStartTimes;

    private Map<NetworkInterface, List<Long>> lastFivePacketsSendStartTimesLists;

    private Map<NetworkInterface, Double> lastPacketSendDurations;

    private Map<String, NetworkInterface> networkInterfaces;

    private Map<NetworkInterface, Long> networkSpeeds;

    /**
     * Data structure for throughput file building
     */
    private Map<Integer, Integer> highestPriorityFlowNumbers;

    public synchronized static MultipleFlowsSinglePriorityForwarder getInstance(DataPlaneForwarder routingForwarder) {
        if (queuesForwarder == null) {
            queuesForwarder = new MultipleFlowsSinglePriorityForwarder();
            queuesForwarder.updateManager = new UpdateManager();
            queuesForwarder.updateManager.start();

            queuesForwarder.highestPriorityFlowIdsSentPackets = new ConcurrentHashMap<>();
            queuesForwarder.previousHighestPriorityFlowIdsSentPackets = new ConcurrentHashMap<>();
            queuesForwarder.previousHighestPriorityTotalFlows = 0;
            queuesForwarder.previousHighestPriorityTotalSentPackets = 0;
            queuesForwarder.lastPacketPriority = -1;
            queuesForwarder.lastPacketSendStartTimes = new ConcurrentHashMap<>();
            queuesForwarder.lastFivePacketsSendStartTimesLists = new ConcurrentHashMap<>();
            queuesForwarder.lastPacketSendDurations = new ConcurrentHashMap<>();
            queuesForwarder.networkInterfaces = new ConcurrentHashMap<>();
            queuesForwarder.networkSpeeds = new ConcurrentHashMap<>();

            queuesForwarder.highestPriorityFlowNumbers = new ConcurrentHashMap<>();
            Dispatcher.getInstance(false).addPacketForwardingListenerBeforeAnother(queuesForwarder, routingForwarder);
            System.out.println("MultipleFlowsSinglePriorityForwarder ENABLED");

            File outputFile = new File("output_internal.csv");
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }
        return queuesForwarder;
    }

    @Override
    public void deactivate() {
        if (queuesForwarder != null) {
            Dispatcher.getInstance(false).removePacketForwardingListener(queuesForwarder);
            queuesForwarder.updateManager.stopUpdateManager();
            queuesForwarder = null;
            System.out.println("MultipleFlowsSinglePriorityForwarder DISABLED");
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

    private synchronized int getCurrentProgressPercentage(NetworkInterface networkInterface) {
        int currentProgressPercentage = 100;
        if (this.lastPacketSendStartTimes.get(networkInterface) != null && this.lastPacketSendDurations.get(networkInterface) != null) {
            long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(networkInterface);
            currentProgressPercentage = (int) ((elapsed * 100) / this.lastPacketSendDurations.get(networkInterface));
        }
        return currentProgressPercentage;
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

    private synchronized int getSendProbability(int flowId) {
        int sendProbability = 100;
        Integer previousSentPackets = this.previousHighestPriorityFlowIdsSentPackets.get(flowId);
        if (previousSentPackets != null && this.highestPriorityFlowIdsSentPackets.keySet().size() > 1) {
            int sentPacketsTarget = this.previousHighestPriorityTotalSentPackets / this.previousHighestPriorityTotalFlows;
            int sentPacketsOffset = previousSentPackets - sentPacketsTarget;
            int offsetPercentage = (sentPacketsOffset * 100) / sentPacketsTarget;
            sendProbability = sendProbability - (offsetPercentage * this.previousHighestPriorityTotalFlows);
        }
        return sendProbability;
    }

    private synchronized void resetHighestPriorityFlowIdsSentPackets() {
        this.previousHighestPriorityFlowIdsSentPackets.clear();
        this.previousHighestPriorityTotalFlows = this.highestPriorityFlowIdsSentPackets.keySet().size();
        this.previousHighestPriorityTotalSentPackets = 0;
        for (Integer flowId : this.highestPriorityFlowIdsSentPackets.keySet()) {
            Integer flowIdSentPackets = this.highestPriorityFlowIdsSentPackets.get(flowId);
            this.previousHighestPriorityFlowIdsSentPackets.put(flowId, flowIdSentPackets);
            this.previousHighestPriorityTotalSentPackets = this.previousHighestPriorityTotalSentPackets + flowIdSentPackets;
        }
        this.highestPriorityFlowIdsSentPackets.clear();
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
             * If the packet is the first to arrive, save the send information
             * and occupy the transmission channel
             */
            if (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null && this.lastPacketPriority == -1) {
                synchronized (this) {
                    /*
                     * If the flow has the highest priority (control messages)
                     */
                    if (flowPriority == 1) {
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
                        this.highestPriorityFlowIdsSentPackets.put(flowId, 1);
                    }
                    this.lastPacketPriority = flowPriority;
                }
                System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " is the first to reach the channel, no changes made to it");
            }
            /*
             * If the flow has the highest priority (control messages), check if the last packet was sent by a flow with the highest priority
             */
            else if (flowPriority == 1) {
                /*
                 * If the last packet was not sent by a flow with the highest priority, save the send information and occupy the transmission channel
                 */
                if (this.lastPacketPriority > 0) {
                    synchronized (this) {
                        // double sendDuration = ((double) up.getBytePayload().length / networkSpeed) * 1000;
                        double sendDuration = getAverageInterPacketTime(nextSendNetworkInterface) * 1.25;
                        if (sendDuration < 25)
                            sendDuration = 25;
                        this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
                        this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
                        List<Long> lastFivePacketsSendStartTimes = this.lastFivePacketsSendStartTimesLists.get(nextSendNetworkInterface);
                        if (lastFivePacketsSendStartTimes == null) {
                            lastFivePacketsSendStartTimes = new ArrayList<>();
                        }
                        lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());

                        if (lastFivePacketsSendStartTimes.size() == 6) {
                            lastFivePacketsSendStartTimes.remove(5);
                        }
                        this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
                        if (this.highestPriorityFlowIdsSentPackets.containsKey(flowId))
                            this.highestPriorityFlowIdsSentPackets.put(flowId, this.highestPriorityFlowIdsSentPackets.get(flowId) + 1);
                        else if (this.previousHighestPriorityFlowIdsSentPackets.containsKey(flowId)) {
                            int sentPackets = (this.previousHighestPriorityFlowIdsSentPackets.get(flowId) / 2) + 1;
                            this.highestPriorityFlowIdsSentPackets.put(flowId, sentPackets);
                        } else
                            this.highestPriorityFlowIdsSentPackets.put(flowId, 1);
                        this.lastPacketPriority = flowPriority;
                    }
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " has the highest priority, no changes made to it");
                }
                /*
                 * If the last packet was sent by a flow with the highest priority, get the send probability
                 */
                else {
                    long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                    int sendProbability = getSendProbability(flowId);
                    int randomNumber = ThreadLocalRandom.current().nextInt(100);
                    /*
                     * If the packet is not allowed to proceed, wait and retry
                     */
                    while (elapsed < this.lastPacketSendDurations.get(nextSendNetworkInterface) && randomNumber > sendProbability) {
                        // long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
                        long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                        if (timeToWait > 4000)
                            timeToWait = 4000;
                        if (timeToWait < 10)
                            timeToWait = 10;
                        System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " has the highest priority but has not been sent, waiting " + timeToWait + " milliseconds and retrying");
                        try {
                            Thread.sleep(timeToWait);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                        sendProbability = getSendProbability(flowId);
                        randomNumber = ThreadLocalRandom.current().nextInt(100);
                    }
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
                        if (this.highestPriorityFlowIdsSentPackets.containsKey(flowId))
                            this.highestPriorityFlowIdsSentPackets.put(flowId, this.highestPriorityFlowIdsSentPackets.get(flowId) + 1);
                        else if (this.previousHighestPriorityFlowIdsSentPackets.containsKey(flowId)) {
                            int sentPackets = (this.previousHighestPriorityFlowIdsSentPackets.get(flowId) / 2) + 1;
                            this.highestPriorityFlowIdsSentPackets.put(flowId, sentPackets);
                        } else
                            this.highestPriorityFlowIdsSentPackets.put(flowId, 1);
                        this.lastPacketPriority = flowPriority;
                    }
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " has the highest priority, no changes made to it");
                }
            }
            /*
             * If the flow hasn't the highest priority or hasn't a valid priority value, check if the transmission channel is empty
             */
            else {
                int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
                int randomNumber = ThreadLocalRandom.current().nextInt(100);
                int attempts = 0;
                /*
                 * If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
                 */
                while (currentProgressPercentage < 100 && attempts < MAX_ATTEMPTS) {
                    long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                    // long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
                    long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                    if (timeToWait > 4000)
                        timeToWait = 4000;
                    if (timeToWait < 10)
                        timeToWait = 10;
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    attempts++;
                    currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
                    randomNumber = ThreadLocalRandom.current().nextInt(100);
                }
                /*
                 * If the maximum number of attempts is reached and the transmission channel is occupied, drop the packet
                 */
                if (attempts == MAX_ATTEMPTS) {
                    up.setDest(null);
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + ", maximum number of attempts made, the packet is being dropped");
                }
                /*
                 * If the maximum number of attempts is not reached, save the send information and occupy the transmission channel
                 */
                else {
                    synchronized (this) {
                        this.lastPacketPriority = flowPriority;
                    }
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " found the transmission channel free, no changes made to the packet");
                }
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
            if (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null && this.lastPacketPriority == -1) {
                synchronized (this) {
                    if (flowPriority == 1) {
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
                        this.highestPriorityFlowIdsSentPackets.put(flowId, 1);
                    }
                    this.lastPacketPriority = flowPriority;
                }
                System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " is the first to reach the channel, no changes made to it");
            }
            /*
             * If the flow has the highest priority, check if the last packet was sent by a flow with the highest priority
             */
            else if (flowPriority == 1) {
                /*
                 * If the last packet was not sent by a flow with the highest priority, save the send information and occupy the transmission channel
                 */
                if (this.lastPacketPriority > 0) {
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
                        if (this.highestPriorityFlowIdsSentPackets.containsKey(flowId))
                            this.highestPriorityFlowIdsSentPackets.put(flowId, this.highestPriorityFlowIdsSentPackets.get(flowId) + 1);
                        else if (this.previousHighestPriorityFlowIdsSentPackets.containsKey(flowId)) {
                            int sentPackets = (this.previousHighestPriorityFlowIdsSentPackets.get(flowId) / 2) + 1;
                            this.highestPriorityFlowIdsSentPackets.put(flowId, sentPackets);
                        } else
                            this.highestPriorityFlowIdsSentPackets.put(flowId, 1);
                        this.lastPacketPriority = flowPriority;
                    }
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " has the highest priority, no changes made to it");
                }
                /*
                 * If the last packet was sent by a flow with the highest priority, get the send probability
                 */
                else {
                    long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                    int sendProbability = getSendProbability(flowId);
                    int randomNumber = ThreadLocalRandom.current().nextInt(100);
                    /*
                     * If the packet is not allowed to proceed, wait and retry
                     */
                    while (elapsed < this.lastPacketSendDurations.get(nextSendNetworkInterface) && randomNumber > sendProbability) {
                        // long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
                        long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                        if (timeToWait > 4000)
                            timeToWait = 4000;
                        if (timeToWait < 10)
                            timeToWait = 10;
                        System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " has the highest priority, but has not been sent, waiting " + timeToWait + " milliseconds and retrying");
                        try {
                            Thread.sleep(timeToWait);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                        sendProbability = getSendProbability(flowId);
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
                        if (this.highestPriorityFlowIdsSentPackets.containsKey(flowId))
                            this.highestPriorityFlowIdsSentPackets.put(uh.getFlowId(), this.highestPriorityFlowIdsSentPackets.get(flowId) + 1);
                        else if (this.previousHighestPriorityFlowIdsSentPackets.containsKey(flowId)) {
                            int sentPackets = (this.previousHighestPriorityFlowIdsSentPackets.get(flowId) / 2) + 1;
                            this.highestPriorityFlowIdsSentPackets.put(flowId, sentPackets);
                        } else
                            this.highestPriorityFlowIdsSentPackets.put(flowId, 1);
                        this.lastPacketPriority = flowPriority;
                    }
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " has the highest priority, no changes made to it");
                }
            }
            /*
             * If the flow hasn't the highest priority or hasn't a valid priority value, check if the transmission channel is empty
             */
            else {
                int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
                int randomNumber = ThreadLocalRandom.current().nextInt(100);
                int attempts = 0;
                /*
                 * If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
                 */
                while (currentProgressPercentage < 100 && attempts < MAX_ATTEMPTS) {
                    long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
                    // long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
                    long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                    if (timeToWait > 4000)
                        timeToWait = 4000;
                    if (timeToWait < 10)
                        timeToWait = 10;
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    attempts++;
                    currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
                    randomNumber = ThreadLocalRandom.current().nextInt(100);
                }
                /*
                 * If the maximum number of attempts is reached and the transmission channel is occupied, drop the packet
                 */
                if (attempts == MAX_ATTEMPTS) {
                    uh.setDest(null);
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + ", maximum number of attempts made, the packet is being dropped");
                }
                /*
                 * If the maximum number of attempts is not reached, save the send information and occupy the transmission channel
                 */
                else {
                    synchronized (this) {
                        this.lastPacketPriority = flowPriority;
                    }
                    System.out.println("MultipleFlowsSinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + flowId + " found the transmission channel free, no changes made to the packet");
                }
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
                printWriter.println("timestamp,controlpriority sentbytes,maxpriority_1_sentbytes,maxpriority_2_sentbytes,lowpriority_sentbytes");
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
            Integer flowNumber = this.highestPriorityFlowNumbers.get(flowId);
            if (flowNumber == null) {
                flowNumber = this.highestPriorityFlowNumbers.size() + 1;
                this.highestPriorityFlowNumbers.put(flowId, flowNumber);
            }
            if (flowNumber == 1)
                printWriter.println(timestamp + ",," + packetLength + ",,");
            else if (flowNumber == 2)
                printWriter.println(timestamp + ",,," + packetLength + ",");
        }
        else {
            printWriter.println(timestamp + ",,,," + packetLength);
        }

        printWriter.close();
    }

    private static class UpdateManager extends Thread {
        /*
         * Interval between each update
         */
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
                if(queuesForwarder != null) {
                    queuesForwarder.resetHighestPriorityFlowIdsSentPackets();
                }
            }
        }
    }
}
