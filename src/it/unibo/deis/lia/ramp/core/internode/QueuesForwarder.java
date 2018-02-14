package it.unibo.deis.lia.ramp.core.internode;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
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
public class QueuesForwarder implements DataPlaneForwarder {
	
	private static final int MAX_ATTEMPTS = 8000;
	
	private static QueuesForwarder queuesForwarder = null;
	private UpdateManager updateManager;
	
	// No need to make private fields static, since access always happens through the singleton
	private Map<Integer, Integer> highestPriorityFlowIdsSentPackets;
	private Map<Integer, Integer> previousHighestPriorityFlowIdsSentPackets;
	private int previousHighestPriorityTotalFlows;
	private int previousHighestPriorityTotalSentPackets;
	private int lastPacketPriority;
	private Map<NetworkInterface, Long> lastPacketSendStartTimes;
	private Map<NetworkInterface, Double> lastPacketSendDurations;
	private Map<String, NetworkInterface> networkInterfaces;
	private Map<NetworkInterface, Long> networkSpeeds;
	
	public synchronized static QueuesForwarder getInstance() {
		if (queuesForwarder == null) {
			queuesForwarder = new QueuesForwarder();
			queuesForwarder.updateManager = new UpdateManager();
			queuesForwarder.updateManager.start();
			
			queuesForwarder.highestPriorityFlowIdsSentPackets = new ConcurrentHashMap<Integer, Integer>();
			queuesForwarder.previousHighestPriorityFlowIdsSentPackets = new ConcurrentHashMap<Integer, Integer>();
			queuesForwarder.previousHighestPriorityTotalFlows = 0;
			queuesForwarder.previousHighestPriorityTotalSentPackets = 0;
			queuesForwarder.lastPacketPriority = -1;
			queuesForwarder.lastPacketSendStartTimes = new ConcurrentHashMap<NetworkInterface, Long>();
			queuesForwarder.lastPacketSendDurations = new ConcurrentHashMap<NetworkInterface, Double>();
			queuesForwarder.networkInterfaces = new ConcurrentHashMap<String, NetworkInterface>();
			queuesForwarder.networkSpeeds = new ConcurrentHashMap<NetworkInterface, Long>();
			Dispatcher.getInstance(false).addPacketForwardingListener(queuesForwarder);
			System.out.println("QueuesForwarder ENABLED");
		}
		return queuesForwarder;
	}

	@Override
	public void deactivate() {
		if (queuesForwarder != null) {
			Dispatcher.getInstance(false).removePacketForwardingListener(queuesForwarder);
			queuesForwarder.updateManager.stopUpdateManager();
			queuesForwarder = null;
			System.out.println("QueuesForwarder DISABLED");
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
	
	private synchronized int getCurrentProgressPercentage(NetworkInterface networkInterface) {
		long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(networkInterface);
		int currentProgressPercentage = (int) ((elapsed * 100) / this.lastPacketSendDurations.get(networkInterface));
		return currentProgressPercentage;
	}
	
	private synchronized int getSendProbability(int flowId) {
		int sendProbability = 100;
		Integer previousSentPackets = this.previousHighestPriorityFlowIdsSentPackets.get(flowId);
		if (previousSentPackets != null && this.highestPriorityFlowIdsSentPackets.keySet().size() > 1) {
			int sentPacketsTarget = this.previousHighestPriorityTotalSentPackets / this.previousHighestPriorityTotalFlows;
			int sentPacketsOffset = previousSentPackets - sentPacketsTarget;
			sendProbability = sendProbability - (sentPacketsOffset * this.previousHighestPriorityTotalFlows);
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
			flowIdSentPackets = flowIdSentPackets / 2;
			if (flowIdSentPackets == 0)
				this.highestPriorityFlowIdsSentPackets.remove(flowId);
			else
				this.highestPriorityFlowIdsSentPackets.put(flowId, flowIdSentPackets);
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
					double sendDuration = ((double) up.getBytePayload().length / networkSpeed) * 1000;
					if (sendDuration < 10)
						sendDuration = 10;
					this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
					this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
					if (flowPriority == 0)
						this.highestPriorityFlowIdsSentPackets.put(up.getFlowId(), 1);
					this.lastPacketPriority = flowPriority;
				}
				System.out.println("QueuesForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " is the first to reach the channel, no changes made to it");
			}
			// If the flow has the highest priority, check if the last packet was sent by a flow with the highest priority
			else if (flowPriority == 0) {
				// If the last packet was not sent by a flow with the highest priority, save the send informations and occupy the transmission channel
				if (this.lastPacketPriority > 0) {
					synchronized (this) {
						double sendDuration = ((double) up.getBytePayload().length / networkSpeed) * 1000;
						if (sendDuration < 10)
							sendDuration = 10;
						this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
						this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
						if (this.highestPriorityFlowIdsSentPackets.containsKey(up.getFlowId()))
							this.highestPriorityFlowIdsSentPackets.put(up.getFlowId(), this.highestPriorityFlowIdsSentPackets.get(up.getFlowId())+1);
						else
							this.highestPriorityFlowIdsSentPackets.put(up.getFlowId(), 1);
						this.lastPacketPriority = flowPriority;
					}
					System.out.println("QueuesForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " has the highest priority, no changes made to it");
				}
				// If the last packet was sent by a flow with the highest priority, get the send probability
				else {
					int sendProbability = getSendProbability(up.getFlowId());
					int randomNumber = ThreadLocalRandom.current().nextInt(100);
					// If the packet is not allowed to proceed, wait and retry
					while (randomNumber > sendProbability) {
						long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
						long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
						if (timeToWait > 4000)
							timeToWait = 4000;
						if (timeToWait < 5)
							timeToWait = 5;
						System.out.println("QueuesForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " has the highest priority but has not been sent, waiting " + timeToWait + " milliseconds and retrying");
						try {
							Thread.sleep(timeToWait);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						sendProbability = getSendProbability(up.getFlowId());
						randomNumber = ThreadLocalRandom.current().nextInt(100);
					}
					synchronized (this) {
						double sendDuration = ((double) up.getBytePayload().length / networkSpeed) * 1000;
						if (sendDuration < 10)
							sendDuration = 10;
						this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
						this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
						if (this.highestPriorityFlowIdsSentPackets.containsKey(up.getFlowId()))
							this.highestPriorityFlowIdsSentPackets.put(up.getFlowId(), this.highestPriorityFlowIdsSentPackets.get(up.getFlowId())+1);
						else
							this.highestPriorityFlowIdsSentPackets.put(up.getFlowId(), 1);
						this.lastPacketPriority = flowPriority;
					}
					System.out.println("QueuesForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " has the highest priority, no changes made to it");
				}
			}
			// If the flow hasn't the highest priority or hasn't a valid priority value, check if the transmission channel is empty
			else {
				int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
				int randomNumber = ThreadLocalRandom.current().nextInt(100);
				int attempts = 0;
				// If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
				while (randomNumber > currentProgressPercentage && attempts < MAX_ATTEMPTS) {
					long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
					long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
					if (timeToWait > 4000)
						timeToWait = 4000;
					if (timeToWait < 5)
						timeToWait = 5;
					System.out.println("QueuesForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
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
					System.out.println("QueuesForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + ", maximum number of attempts made, the packet is being dropped");
				}
				// If the maximum number of attempts is not reached, save the send informations and occupy the transmission channel
				else {
					synchronized (this) {
						double sendDuration = ((double) up.getBytePayload().length / networkSpeed) * 1000;
						if (sendDuration < 10)
							sendDuration = 10;
						this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
						this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
						this.lastPacketPriority = flowPriority;
					}
					System.out.println("QueuesForwarder: packet " + up.getPacketId() + " with flowId " + up.getFlowId() + " found the transmission channel free, no changes made to the packet");
				}
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
					double sendDuration = ((double) packetLength / networkSpeed) * 1000;
					if (sendDuration < 10)
						sendDuration = 10;
					this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
					this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
					if (flowPriority == 0)
						this.highestPriorityFlowIdsSentPackets.put(uh.getFlowId(), 1);
					this.lastPacketPriority = flowPriority;
				}
				System.out.println("QueuesForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " is the first to reach the channel, no changes made to it");
			}
			// If the flow has the highest priority, check if the last packet was sent by a flow with the highest priority
			else if (flowPriority == 0) {
				// If the last packet was not sent by a flow with the highest priority, save the send informations and occupy the transmission channel
				if (this.lastPacketPriority > 0) {
					synchronized (this) {
						double sendDuration = ((double) packetLength / networkSpeed) * 1000;
						if (sendDuration < 10)
							sendDuration = 10;
						this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
						this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
						if (this.highestPriorityFlowIdsSentPackets.containsKey(uh.getFlowId()))
							this.highestPriorityFlowIdsSentPackets.put(uh.getFlowId(), this.highestPriorityFlowIdsSentPackets.get(uh.getFlowId())+1);
						else
							this.highestPriorityFlowIdsSentPackets.put(uh.getFlowId(), 1);
						this.lastPacketPriority = flowPriority;
					}
					System.out.println("QueuesForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " has the highest priority, no changes made to it");
				}
				// If the last packet was sent by a flow with the highest priority, get the send probability
				else {
					int sendProbability = getSendProbability(uh.getFlowId());
					int randomNumber = ThreadLocalRandom.current().nextInt(100);
					// If the packet is not allowed to proceed, wait and retry
					while (randomNumber > sendProbability) {
						long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
						long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
						if (timeToWait > 4000)
							timeToWait = 4000;
						if (timeToWait < 5)
							timeToWait = 5;
						System.out.println("QueuesForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " has the highest priority, but has not been sent, waiting " + timeToWait + " milliseconds and retrying");
						try {
							Thread.sleep(timeToWait);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						sendProbability = getSendProbability(uh.getFlowId());
						randomNumber = ThreadLocalRandom.current().nextInt(100);
					}
					synchronized (this) {
						double sendDuration = ((double) packetLength / networkSpeed) * 1000;
						if (sendDuration < 10)
							sendDuration = 10;
						this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
						this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
						if (this.highestPriorityFlowIdsSentPackets.containsKey(uh.getFlowId()))
							this.highestPriorityFlowIdsSentPackets.put(uh.getFlowId(), this.highestPriorityFlowIdsSentPackets.get(uh.getFlowId())+1);
						else
							this.highestPriorityFlowIdsSentPackets.put(uh.getFlowId(), 1);
						this.lastPacketPriority = flowPriority;
					}
					System.out.println("QueuesForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " has the highest priority, no changes made to it");
				}
			}
			// If the flow hasn't the highest priority or hasn't a valid priority value, check if the transmission channel is empty
			else {
				int currentProgressPercentage = getCurrentProgressPercentage(nextSendNetworkInterface);
				int randomNumber = ThreadLocalRandom.current().nextInt(100);
				int attempts = 0;
				// If the transmission channel is occupied, send with a certain probability or wait and retry for a given number of times
				while (randomNumber > currentProgressPercentage && attempts < MAX_ATTEMPTS) {
					long elapsed = System.currentTimeMillis() - this.lastPacketSendStartTimes.get(nextSendNetworkInterface);
					long timeToWait = Math.round((this.lastPacketSendDurations.get(nextSendNetworkInterface) - elapsed) * 1.2);
					if (timeToWait > 4000)
						timeToWait = 4000;
					if (timeToWait < 5)
						timeToWait = 5;
					System.out.println("QueuesForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " hasn't the highest priority, waiting " + timeToWait + " milliseconds and retrying; " + attempts + " attempts made");
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
					uh.setDest(null);
					System.out.println("QueuesForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + ", maximum number of attempts made, the packet is being dropped");
				}
				// If the maximum number of attempts is not reached, save the send informations and occupy the transmission channel
				else {
					synchronized (this) {
						double sendDuration = ((double) packetLength / networkSpeed) * 1000;
						if (sendDuration < 10)
							sendDuration = 10;
						this.lastPacketSendDurations.put(nextSendNetworkInterface, sendDuration);
						this.lastPacketSendStartTimes.put(nextSendNetworkInterface, System.currentTimeMillis());
						this.lastPacketPriority = flowPriority;
					}
					System.out.println("QueuesForwarder: packet " + uh.getPacketId() + " with flowId " + uh.getFlowId() + " found the transmission channel free, no changes made to the packet");
				}
			}
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
		
		// Interval between each update
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
				queuesForwarder.resetHighestPriorityFlowIdsSentPackets();
			}
		}
	}

}
