package it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.trafficEngineeringForwarders;

import it.unibo.deis.lia.ramp.core.internode.sdn.controllerClient.ControllerClientInterface;
import it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder.DataPlaneForwarder;
import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentLocator;
import it.unibo.deis.lia.ramp.util.componentLocator.ComponentType;
import org.apache.commons.net.util.SubnetUtils;
import oshi.PlatformEnum;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.io.*;
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

import org.apache.commons.net.util.SubnetUtils.SubnetInfo;

/**
 * @author Alessandro Dolci
 * @author Dmitrij David Padalino Montenero
 */
public class SinglePriorityForwarder implements DataPlaneForwarder {

    private static final int MAX_ATTEMPTS = 8000;

    private static SinglePriorityForwarder singleFlowForwarder = null;

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
    private Map<NetworkInterface, Long> lastPacketSendStartTimes;

    private Map<NetworkInterface, List<Long>> lastFivePacketsSendStartTimesLists;

    private Map<NetworkInterface, Double> lastPacketSendDurations;

    private Map<String, NetworkInterface> networkInterfaces;

    private Map<NetworkInterface, Long> networkSpeeds;

    /**
     * Data structure for throughput file building
     */
    private Map<Integer, Integer> lowPriorityFlowNumbers;

    public synchronized static SinglePriorityForwarder getInstance(DataPlaneForwarder routingForwarder) {
        if (singleFlowForwarder == null) {
            singleFlowForwarder = new SinglePriorityForwarder();

            singleFlowForwarder.lastPacketSendStartTimes = new ConcurrentHashMap<>();
            singleFlowForwarder.lastFivePacketsSendStartTimesLists = new ConcurrentHashMap<>();
            singleFlowForwarder.lastPacketSendDurations = new ConcurrentHashMap<>();
            singleFlowForwarder.networkInterfaces = new ConcurrentHashMap<>();
            singleFlowForwarder.networkSpeeds = new ConcurrentHashMap<>();

            singleFlowForwarder.lowPriorityFlowNumbers = new ConcurrentHashMap<>();
            Dispatcher.getInstance(false).addPacketForwardingListenerBeforeAnother(singleFlowForwarder, routingForwarder);

            System.out.println("SinglePriorityForwarder ENABLED");

            File outputFile = new File("output_internal.csv");
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }
        return singleFlowForwarder;
    }

    @Override
    public synchronized void deactivate() {
        if (singleFlowForwarder != null) {
            Dispatcher.getInstance(false).removePacketForwardingListener(singleFlowForwarder);
            singleFlowForwarder = null;
            System.out.println("SinglePriorityForwarder DISABLED");
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
                    // networkSpeed = networkSpeed / 10;
                    networkSpeeds.put(networkInterface, networkSpeed);
                }
        }
        return networkSpeed;
    }

    private synchronized int getCurrentProgressPercentage(NetworkInterface networkInterface) {
        long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(networkInterface);
        int currentProgressPercentage = (int) ((elapsed * 100) / this.lastPacketSendDurations.get(networkInterface));
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
             * If the flow has the highest priority or is the first to arrive,
             * save the send information and occupy the transmission channel.
             */
            if (flowPriority == 1 || (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null)) {
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
                }
                System.out.println("SinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " has the highest priority, no changes made to it");
            } else {
                /*
                 * If the flow hasn't the highest priority or hasn't a valid priority value,
                 * check if the transmission channel is empty
                 */
                int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
                int randomNumber = ThreadLocalRandom.current().nextInt(100);
                int attempts = 0;
                /*
                 * If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
                 */
                while (currentProgressPercentage < 100 && attempts < MAX_ATTEMPTS) {
                    long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                    if (timeToWait > 4000)
                        timeToWait = 4000;
                    if (timeToWait < 10)
                        timeToWait = 10;
                    System.out.println("SinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
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
                    System.out.println("SinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + ", maximum number of attempts made, the packet is being dropped");
                } else {
                    /*
                     * If the maximum number of attempts is not reached, save the send information and occupy the transmission channel
                     */
                    System.out.println("SinglePriorityForwarder: packet " + up.getPacketId() + " with flowId " + flowId + " found the transmission channel free, no changes made to the packet");
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
         * Check if the current packet contains a valid flowId and has to be processed according to the SDN paradigm
         */
        int flowId = uh.getFlowId();

        if (uh.getFlowId() != GenericPacket.UNUSED_FIELD && flowId != CONTROL_FLOW_ID && uh.getDestNodeId() != Dispatcher.getLocalRampId()) {
            int flowPriority = controllerClient.getFlowPriority(flowId);

            NetworkInterface nextSendNetworkInterface = getNextSendNetworkInterface(uh.getDest()[uh.getCurrentHop()]);
            int packetLength = 0;
            if (len <= payload.length)
                packetLength = len;
            else
                packetLength = payload.length;
            long networkSpeed = getNetworkSpeed(nextSendNetworkInterface);
            System.out.println("packet length: " + packetLength + ", network speed: " + networkSpeed);
            /*
             * If the flow has the highest priority or is the first to arrive,
             * save the send information and occupy the transmission channel
             */
            if (flowPriority == 1 || (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null)) {
                synchronized (this) {
                    double sendDuration = getAverageInterPacketTime(nextSendNetworkInterface) * 1.25;
                    if (sendDuration < 25)
                        sendDuration = 25;
                    this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
                    this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
                    List<Long> lastFivePacketsSendStartTimes = this.lastFivePacketsSendStartTimesLists.get(nextSendNetworkInterface);
                    if (lastFivePacketsSendStartTimes == null)
                        lastFivePacketsSendStartTimes = new ArrayList<Long>();
                    lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
                    if (lastFivePacketsSendStartTimes.size() == 6)
                        lastFivePacketsSendStartTimes.remove(5);
                    this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
                    System.out.println("start time: " + this.lastPacketSendStartTimes.get(nextSendNetworkInterface) + ", duration: " + this.lastPacketSendDurations.get(nextSendNetworkInterface));
                }
                System.out.println("SinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " has the highest priority, no changes made to it");
            }
            /*
             * If the flow hasn't the highest priority or hasn't a valid priority value, check if the transmission channel is empty
             */
            else {
                int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
                int randomNumber = ThreadLocalRandom.current().nextInt(100);
                System.out.println("current progress: " + currentProgressPercentage + ", number: " + randomNumber);
                int attempts = 0;
                /*
                 * If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
                 */
                while (currentProgressPercentage < 100 && attempts < MAX_ATTEMPTS) {
                    long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
                    if (timeToWait > 4000)
                        timeToWait = 4000;
                    if (timeToWait < 10)
                        timeToWait = 10;
                    System.out.println("SinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    attempts++;
                    currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
                    randomNumber = ThreadLocalRandom.current().nextInt(100);
                    System.out.println("current progress: " + currentProgressPercentage + ", number: " + randomNumber);
                }
                /*
                 * If the maximum number of attempts is reached and the transmission channel is occupied, drop the packet
                 */
                if (attempts == MAX_ATTEMPTS) {
                    uh.setDest(null);
                    System.out.println("SinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + ", maximum number of attempts made, the packet is being dropped");
                }
                /*
                 * If the maximum number of attempts is not reached, save the send information and occupy the transmission channel
                 */
                else {
                    System.out.println("SinglePriorityForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " found the transmission channel free, no changes made to the packet");
                }
            }
            /*
             * Log all network traffic handled with this method
             */
            log(flowId, flowPriority, packetLength);
        }

        if (flowId == CONTROL_FLOW_ID) {
            int packetLength = Math.min(len, payload.length);
            log(CONTROL_FLOW_ID, CONTROL_FLOW_ID_PRIORITY, packetLength);
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
                printWriter.println("timestamp,controlpriority sentbytes,maxpriority_sentbytes,lowpriority_1_sentbytes,lowpriority_2_sentbytes,lowpriority_3_sentbytes");
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
            printWriter.println(timestamp + "," + packetLength + ",,,,");
        }
        else if (flowPriority == 1) {
            printWriter.println(timestamp + ",," + packetLength + ",,,");
        }
        else {
            Integer flowNumber = this.lowPriorityFlowNumbers.get(flowId);
            if (flowNumber == null) {
                flowNumber = this.lowPriorityFlowNumbers.size() + 1;
                this.lowPriorityFlowNumbers.put(flowId, flowNumber);
            }
            if (flowNumber == 1)
                printWriter.println(timestamp + ",,," + packetLength + ",,");
            else if (flowNumber == 2)
                printWriter.println(timestamp + ",,,," + packetLength + ",");
            else if (flowNumber == 3)
                printWriter.println(timestamp + ",,,,," + packetLength);
        }

        printWriter.close();
    }
}
