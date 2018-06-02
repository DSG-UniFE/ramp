package it.unibo.deis.lia.ramp.core.internode;

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
 *
 * @author Alessandro Dolci
 *
 */
public class MultipleFlowsMultiplePrioritiesForwarder implements DataPlaneForwarder {
	
	private static MultipleFlowsMultiplePrioritiesForwarder trafficShapingForwarder = null;
	private UpdateManager updateManager;
	
	private Map<Integer, Map<Integer, Integer>> prioritiesFlowIdsSentPackets;
	private Map<Integer, Map<Integer, Integer>> previousPrioritiesFlowIdsSentPackets;
	private Map<Integer, Integer> previousPrioritiesTotalFlows;
	private Map<Integer, Integer> previousPrioritiesTotalSentPackets;
	private Map<NetworkInterface, Long> lastPacketSendStartTimes;
	private Map<NetworkInterface, List<Long>> lastFivePacketsSendStartTimesLists;
	private Map<NetworkInterface, Double> lastPacketSendDurations;
	private Map<String, NetworkInterface> networkInterfaces;
	private Map<NetworkInterface, Long> networkSpeeds;
	
	// Data structure for throughput file building
	private Map<Integer, Integer> highestPriorityFlowNumbers;

	public synchronized static MultipleFlowsMultiplePrioritiesForwarder getInstance() {
		if (trafficShapingForwarder == null) {
			trafficShapingForwarder = new MultipleFlowsMultiplePrioritiesForwarder();
			trafficShapingForwarder.updateManager = new UpdateManager();
			trafficShapingForwarder.updateManager.start();
			
			trafficShapingForwarder.prioritiesFlowIdsSentPackets = new ConcurrentHashMap<Integer, Map<Integer, Integer>>();
			trafficShapingForwarder.previousPrioritiesFlowIdsSentPackets = new ConcurrentHashMap<Integer, Map<Integer, Integer>>();
			trafficShapingForwarder.previousPrioritiesTotalFlows = new ConcurrentHashMap<Integer, Integer>();
			trafficShapingForwarder.previousPrioritiesTotalSentPackets = new ConcurrentHashMap<Integer, Integer>();
			trafficShapingForwarder.lastPacketSendStartTimes = new ConcurrentHashMap<NetworkInterface, Long>();
			trafficShapingForwarder.lastFivePacketsSendStartTimesLists = new ConcurrentHashMap<NetworkInterface, List<Long>>();
			trafficShapingForwarder.lastPacketSendDurations = new ConcurrentHashMap<NetworkInterface, Double>();
			trafficShapingForwarder.networkInterfaces = new ConcurrentHashMap<String, NetworkInterface>();
			trafficShapingForwarder.networkSpeeds = new ConcurrentHashMap<NetworkInterface, Long>();
			
			trafficShapingForwarder.highestPriorityFlowNumbers = new ConcurrentHashMap<Integer, Integer>();
			Dispatcher.getInstance(false).addPacketForwardingListener(trafficShapingForwarder);
			System.out.println("TrafficShapingForwarder ENABLED");
		}
		return trafficShapingForwarder;
	}
	
	@Override
	public void deactivate() {
		if (trafficShapingForwarder != null) {
			Dispatcher.getInstance(false).removePacketForwardingListener(trafficShapingForwarder);
			trafficShapingForwarder.updateManager.stopUpdateManager();
			trafficShapingForwarder = null;
			System.out.println("TrafficShapingForwarder DISABLED");
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
					// If the Operating System is Linux, convert from Mb/s to b/s
					if (SystemInfo.getCurrentPlatformEnum().equals(PlatformEnum.LINUX))
						networkSpeed = networkSpeed * 1000000;
					// If no valid speed value has been found, assign a default value
					if (networkSpeed == 0)
						networkSpeed = 10000000L;
					networkSpeed = networkSpeed / 8;
					// networkSpeed = networkSpeed / 2;
					networkSpeeds.put(networkInterface, networkSpeed);
				}
		}
		return networkSpeed;
	}
	
	private synchronized long getAverageInterPacketTime(NetworkInterface networkInterface) {
		long[] interPacketTimes = new long[4];
		List<Long> lastFivePacketsStartTimes = this.lastFivePacketsSendStartTimesLists.get(networkInterface);
		long averageInterPacketTime = 0;
		if (lastFivePacketsStartTimes != null && lastFivePacketsStartTimes.size() == interPacketTimes.length+1) {
			long totalInterPacketTime = 0;
			for (int i = 0; i < interPacketTimes.length; i++) {
				interPacketTimes[i] = lastFivePacketsStartTimes.get(i) - lastFivePacketsStartTimes.get(i+1);
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
			Map<Integer, Integer> previousPriorityFlowIdsSentPackets = new ConcurrentHashMap<Integer, Integer>();
			int previousPriorityTotalSentPackets = 0;
			for (Integer flowId : priorityFlowIdsSentPackets.keySet()) {
				int flowIdSentPackets = priorityFlowIdsSentPackets.get(flowId);
				previousPriorityFlowIdsSentPackets.put(flowId, flowIdSentPackets);
				previousPriorityTotalSentPackets = previousPriorityTotalSentPackets + flowIdSentPackets;
				// flowIdSentPackets = flowIdSentPackets / 2;
				// if (flowIdSentPackets == 0)
				// 	priorityFlowIdsSentPackets.remove(flowId);
				// else
				// 	priorityFlowIdsSentPackets.put(flowId, flowIdSentPackets);
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
		// Check if the current packet contains a valid flowId and has to be processed according to the SDN paradigm
		if (up.getFlowId() != GenericPacket.UNUSED_FIELD && up.getDestNodeId() != Dispatcher.getLocalRampId()) {
			ControllerClient controllerClient = ControllerClient.getInstance();
			int flowPriority = controllerClient.getFlowPriority(up.getFlowId());
			NetworkInterface nextSendNetworkInterface = getNextSendNetworkInterface(up.getDest()[up.getCurrentHop()]);
			long networkSpeed = getNetworkSpeed(nextSendNetworkInterface);
			// If the packet is the first to arrive, save the send informations and occupy the transmission channel
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
						lastFivePacketsSendStartTimes = new ArrayList<Long>();
					lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
					if (lastFivePacketsSendStartTimes.size() == 6)
						lastFivePacketsSendStartTimes.remove(5);
					this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
					Map<Integer, Integer> priorityFlowIdsSentPackets = new ConcurrentHashMap<Integer, Integer>();
					priorityFlowIdsSentPackets.put(up.getFlowId(), 1);
					this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
				}
				System.out.println("TrafficShapingForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " is the first to reach the channel, no changes made to it");
			}
			// If the packet has a priority value, get the elapsed time since the last send start and the send probability
			else {
				long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
				int sendProbability = getSendProbability(up.getFlowId(), flowPriority);
				int randomNumber = ThreadLocalRandom.current().nextInt(100);
				// If the packet is not allowed to proceed, wait and retry
				while (elapsed < this.lastPacketSendDurations.get(nextSendNetworkInterface) && randomNumber > sendProbability) {
					elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
					// long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
					long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
					if (timeToWait > 4000)
						timeToWait = 4000;
					if (timeToWait < 10)
						timeToWait = 10;
					System.out.println("TrafficShapingForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " and priority value " + flowPriority + " has not been sent, waiting " + timeToWait + " milliseconds and retrying");
					try {
						Thread.sleep(timeToWait);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sendProbability = getSendProbability(up.getFlowId(), flowPriority);
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
						lastFivePacketsSendStartTimes = new ArrayList<Long>();
					lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
					if (lastFivePacketsSendStartTimes.size() == 6)
						lastFivePacketsSendStartTimes.remove(5);
					this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
					Map<Integer, Integer> priorityFlowIdsSentPackets = this.prioritiesFlowIdsSentPackets.get(flowPriority);
					Map<Integer, Integer> previousPriorityFlowIdsSentPackets = this.previousPrioritiesFlowIdsSentPackets.get(flowPriority);
					if (priorityFlowIdsSentPackets == null) {
						priorityFlowIdsSentPackets = new ConcurrentHashMap<Integer, Integer>();
						this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
					}
					if (priorityFlowIdsSentPackets.containsKey(up.getFlowId()))
						priorityFlowIdsSentPackets.put(up.getFlowId(), priorityFlowIdsSentPackets.get(up.getFlowId())+1);
					else if (previousPriorityFlowIdsSentPackets != null && previousPriorityFlowIdsSentPackets.containsKey(up.getFlowId())) {
						int sentPackets = (previousPriorityFlowIdsSentPackets.get(up.getFlowId()) / 2) + 1;
						priorityFlowIdsSentPackets.put(up.getFlowId(), sentPackets);
					}
					else
						priorityFlowIdsSentPackets.put(up.getFlowId(), 1);
				}
				System.out.println("TrafficShapingForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " and priority value " + flowPriority + ", no changes made to it");
			}
		}
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
		
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
		// Check if the current packet contains a valid flowId and has to be processed according to the SDN paradigm
		if (uh.getFlowId() != GenericPacket.UNUSED_FIELD && uh.getDestNodeId() != Dispatcher.getLocalRampId()) {
			ControllerClient controllerClient = ControllerClient.getInstance();
			int flowPriority = controllerClient.getFlowPriority(uh.getFlowId());
			NetworkInterface nextSendNetworkInterface = getNextSendNetworkInterface(uh.getDest()[uh.getCurrentHop()]);
			int packetLength = 0;
			if (len <= payload.length)
				packetLength = len;
			else
				packetLength = payload.length;
			long networkSpeed = getNetworkSpeed(nextSendNetworkInterface);
			// If the packet is the first to arrive, save the send informations and occupy the transmission channel
			if (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null) {
				synchronized (this) {
					// double sendDuration = ((double) packetLength / networkSpeed) * 1000;
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
					Map<Integer, Integer> priorityFlowIdsSentPackets = new ConcurrentHashMap<Integer, Integer>();
					priorityFlowIdsSentPackets.put(uh.getFlowId(), 1);
					this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
				}
				System.out.println("TrafficShapingForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " is the first to reach the channel, no changes made to it");
			}
			// If the packet has a priority value, get the elapsed time since the last send start and the send probability
			else {
				long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
				int sendProbability = getSendProbability(uh.getFlowId(), flowPriority);
				int randomNumber = ThreadLocalRandom.current().nextInt(100);
				// If the packet is not allowed to proceed, wait and retry
				while (elapsed < this.lastPacketSendDurations.get(nextSendNetworkInterface) && randomNumber > sendProbability) {
					elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
					// long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
					long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
					if (timeToWait > 4000)
						timeToWait = 4000;
					if (timeToWait < 20)
						timeToWait = 20;
					System.out.println("TrafficShapingForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " and priority value " + flowPriority + " has not been sent, waiting " + timeToWait + " milliseconds and retrying");
					try {
						Thread.sleep(timeToWait);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sendProbability = getSendProbability(uh.getFlowId(), flowPriority);
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
						lastFivePacketsSendStartTimes = new ArrayList<Long>();
					lastFivePacketsSendStartTimes.add(0, System.currentTimeMillis());
					if (lastFivePacketsSendStartTimes.size() == 6)
						lastFivePacketsSendStartTimes.remove(5);
					this.lastFivePacketsSendStartTimesLists.put(nextSendNetworkInterface, lastFivePacketsSendStartTimes);
					Map<Integer, Integer> priorityFlowIdsSentPackets = this.prioritiesFlowIdsSentPackets.get(flowPriority);
					Map<Integer, Integer> previousPriorityFlowIdsSentPackets = this.previousPrioritiesFlowIdsSentPackets.get(flowPriority);
					if (priorityFlowIdsSentPackets == null) {
						priorityFlowIdsSentPackets = new ConcurrentHashMap<Integer, Integer>();
						this.prioritiesFlowIdsSentPackets.put(flowPriority, priorityFlowIdsSentPackets);
					}
					if (priorityFlowIdsSentPackets.containsKey(uh.getFlowId()))
						priorityFlowIdsSentPackets.put(uh.getFlowId(), priorityFlowIdsSentPackets.get(uh.getFlowId())+1);
					else if (previousPriorityFlowIdsSentPackets != null && previousPriorityFlowIdsSentPackets.containsKey(uh.getFlowId())) {
						int sentPackets = (previousPriorityFlowIdsSentPackets.get(uh.getFlowId()) / 2) + 1;
						priorityFlowIdsSentPackets.put(uh.getFlowId(), sentPackets);
					}
					else
						priorityFlowIdsSentPackets.put(uh.getFlowId(), 1);
				}
				System.out.println("TrafficShapingForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " and priority value " + flowPriority + ", no changes made to it");
			}
			
			// Log all network traffic handled with this method
			File outputFile = new File("output_internal.csv");
			// if (flowPriority == 0)
			// 	outputFile = new File("output_internal_maxpriority.csv");
			// else
			// 	outputFile = new File("output_internal_lowpriority.csv");
			PrintWriter printWriter = null;
			if (!outputFile.exists()) {
				try {
					printWriter = new PrintWriter(outputFile);
					printWriter.println("timestamp,maxpriority_sentbytes,priority1_1_sentbytes,priority1_2_sentbytes");
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
			if (flowPriority == 1) {
				Integer flowNumber = this.highestPriorityFlowNumbers.get(uh.getFlowId());
				if (flowNumber == null) {
					flowNumber = this.highestPriorityFlowNumbers.size() + 1;
					this.highestPriorityFlowNumbers.put(uh.getFlowId(), flowNumber);
				}
				if (flowNumber == 1)
					printWriter.println(timestamp + ",," + packetLength + ",");
				else if (flowNumber == 2)
					printWriter.println(timestamp + ",,," + packetLength);
			}
			else
				printWriter.println(timestamp + "," + packetLength + ",,");
			printWriter.close();
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
			while (this.active == true) {
				try {
					Thread.sleep(TIME_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				trafficShapingForwarder.resetPrioritiesFlowIdsSentPackets();
			}
		}
	}

}
