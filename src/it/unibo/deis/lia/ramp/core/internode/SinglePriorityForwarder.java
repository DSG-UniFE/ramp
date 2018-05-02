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
public class SinglePriorityForwarder implements DataPlaneForwarder {
	
	private static final int MAX_ATTEMPTS = 8000;
	
	private static SinglePriorityForwarder singleFlowForwarder = null;
	
	// No need to make private fields static, since access always happens through the singleton
	private Map<NetworkInterface, Long> lastPacketSendStartTimes;
	private Map<NetworkInterface, List<Long>> lastFivePacketsSendStartTimesLists;
	private Map<NetworkInterface, Double> lastPacketSendDurations;
	private Map<String, NetworkInterface> networkInterfaces;
	private Map<NetworkInterface, Long> networkSpeeds;
	
	public synchronized static SinglePriorityForwarder getInstance() {
		if (singleFlowForwarder == null) {
			singleFlowForwarder = new SinglePriorityForwarder();
			
			singleFlowForwarder.lastPacketSendStartTimes = new ConcurrentHashMap<NetworkInterface, Long>();
			singleFlowForwarder.lastFivePacketsSendStartTimesLists = new ConcurrentHashMap<NetworkInterface, List<Long>>();
			singleFlowForwarder.lastPacketSendDurations = new ConcurrentHashMap<NetworkInterface, Double>();
			singleFlowForwarder.networkInterfaces = new ConcurrentHashMap<String, NetworkInterface>();
			singleFlowForwarder.networkSpeeds = new ConcurrentHashMap<NetworkInterface, Long>();
			Dispatcher.getInstance(false).addPacketForwardingListener(singleFlowForwarder);
			System.out.println("SingleFlowForwarder ENABLED");
		}
		return singleFlowForwarder;
	}

	@Override
	public synchronized void deactivate() {
		if (singleFlowForwarder != null) {
			Dispatcher.getInstance(false).removePacketForwardingListener(singleFlowForwarder);
			singleFlowForwarder = null;
			System.out.println("SingleFlowForwarder DISABLED");
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
			// If the flow has the highest priority or is the first to arrive, save the send informations and occupy the transmission channel
			if (flowPriority == 0 || (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null)) {
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
				}
				System.out.println("SingleFlowForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " has the highest priority, no changes made to it");
			}
			// If the flow hasn't the highest priority or hasn't a valid priority value, check if the transmission channel is empty
			else {
				int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
				int randomNumber = ThreadLocalRandom.current().nextInt(100);
				int attempts = 0;
				// If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
				while (currentProgressPercentage < 100 && attempts < MAX_ATTEMPTS) {
					// long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
					// long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
					long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
					if (timeToWait > 4000)
						timeToWait = 4000;
					if (timeToWait < 10)
						timeToWait = 10;
					System.out.println("SingleFlowForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
					try {
						Thread.sleep(timeToWait);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					attempts++;
					currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
					randomNumber = ThreadLocalRandom.current().nextInt(100);
				}
				// If the maximum number of attempts is reached and the transmission channel is occupied, drop the packet
				if (attempts == MAX_ATTEMPTS) {
					up.setDest(null);
					System.out.println("SingleFlowForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + ", maximum number of attempts made, the packet is being dropped");
				}
				// If the maximum number of attempts is not reached, save the send informations and occupy the transmission channel
				else {
					// synchronized (this) {
					// 	double sendDuration = ((double) up.getBytePayload().length / networkSpeed) * 1000;
					// 	if (sendDuration < 10)
					// 		sendDuration = 10;
					// 	this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
					// 	this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
					// }
					System.out.println("SingleFlowForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " found the transmission channel free, no changes made to the packet");
				}
			}
			File outputFile = new File("output_internal.csv");
			// if (flowPriority == 0)
			// 	outputFile = new File("output_internal_maxpriority.csv");
			// else
			// 	outputFile = new File("output_internal_lowpriority.csv");
			PrintWriter printWriter = null;
			if (!outputFile.exists()) {
				try {
					printWriter = new PrintWriter(outputFile);
					printWriter.println("timestamp,maxpriority_sentbytes,lowpriority_sentbytes");
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
			if (flowPriority == 0)
				printWriter.println(timestamp + "," + up.getBytePayload().length + ",");
			else
				printWriter.println(timestamp + ",," + up.getBytePayload().length);
			printWriter.close();
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
			System.out.println("packet length: " + packetLength + ", network speed: " + networkSpeed);
			// If the flow has the highest priority or is the first to arrive, save the send informations and occupy the transmission channel
			if (flowPriority == 0 || (this.lastPacketSendStartTimes.get(nextSendNetworkInterface) == null && this.lastPacketSendDurations.get(nextSendNetworkInterface) == null)) {
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
					System.out.println("start time: " + this.lastPacketSendStartTimes.get(nextSendNetworkInterface) + ", duration: " + this.lastPacketSendDurations.get(nextSendNetworkInterface));
				}
				System.out.println("SingleFlowForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " has the highest priority, no changes made to it");
			}
			// If the flow hasn't the highest priority or hasn't a valid priority value, check if the transmission channel is empty
			else {
				int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
				int randomNumber = ThreadLocalRandom.current().nextInt(100);
				System.out.println("current progress: " + currentProgressPercentage + ", number: " + randomNumber);
				int attempts = 0;
				// If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
				while (currentProgressPercentage < 100 && attempts < MAX_ATTEMPTS) {
					// long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
					// long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
					long timeToWait = getAverageInterPacketTime(nextSendNetworkInterface);
					if (timeToWait > 4000)
						timeToWait = 4000;
					if (timeToWait < 10)
						timeToWait = 10;
					System.out.println("SingleFlowForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
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
				// If the maximum number of attempts is reached and the transmission channel is occupied, drop the packet
				if (attempts == MAX_ATTEMPTS) {
					uh.setDest(null);
					System.out.println("SingleFlowForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + ", maximum number of attempts made, the packet is being dropped");
				}
				// If the maximum number of attempts is not reached, save the send informations and occupy the transmission channel
				else {
					// synchronized (this) {
					// 	double sendDuration = ((double) packetLength / networkSpeed) * 1000;
					// 	if (sendDuration < 10)
					// 		sendDuration = 10;
					// 	this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
					// 	this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
					// 	System.out.println("start time: " + this.lastPacketSendStartTimes.get(nextSendNetworkInterface) + ", duration: " + this.lastPacketSendDurations.get(nextSendNetworkInterface));
					// }
					System.out.println("SingleFlowForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " found the transmission channel free, no changes made to the packet");
				}
			}
			File outputFile = new File("output_internal.csv");
			// if (flowPriority == 0)
			// 	outputFile = new File("output_internal_maxpriority.csv");
			// else
			// 	outputFile = new File("output_internal_lowpriority.csv");
			PrintWriter printWriter = null;
			if (!outputFile.exists()) {
				try {
					printWriter = new PrintWriter(outputFile);
					printWriter.println("timestamp,maxpriority_sentbytes,lowpriority_sentbytes");
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
			if (flowPriority == 0)
				printWriter.println(timestamp + "," + packetLength + ",");
			else
				printWriter.println(timestamp + ",," + packetLength);
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

}
