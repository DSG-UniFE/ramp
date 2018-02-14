package it.unibo.deis.lia.ramp.util;

import java.io.Serializable;
import java.net.NetworkInterface;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class NetworkInterfaceStats implements NodeStats, Serializable {
	
	private static final long serialVersionUID = -1271659489834372972L;
	
	private int nodeId;
	private NetworkIF networkIF;
	private long interfaceSpeed;
	private long startReceivedBytes;
	private long startTransmittedBytes;
	private long startReceivedPackets;
	private long startTrasmittedPackets;
	private long startTransmissionErrors;
	private long receivedBytes;
	private long trasmittedBytes;
	private long receivedPackets;
	private long transmittedPackets;
	private long transmissionErrors;
	
	public NetworkInterfaceStats(int nodeId, NetworkInterface networkInterface) {
		this.nodeId = nodeId;
		SystemInfo systemInfo = new SystemInfo();
		HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
		NetworkIF[] networkIFs = hardwareAbstractionLayer.getNetworkIFs();
		for (int i = 0; i < networkIFs.length; i++)
			if (networkIFs[i].getName().equals(networkInterface.getName())) {
				this.networkIF = networkIFs[i];
				this.interfaceSpeed = networkIFs[i].getSpeed();
				this.startReceivedBytes = networkIFs[i].getBytesRecv();
				this.startTransmittedBytes = networkIFs[i].getBytesSent();
				this.startReceivedPackets = networkIFs[i].getPacketsRecv();
				this.startTrasmittedPackets = networkIFs[i].getPacketsSent();
				this.startTransmissionErrors = networkIFs[i].getOutErrors();
			}
		this.receivedBytes = 0;
		this.trasmittedBytes = 0;
		this.receivedPackets = 0;
		this.transmittedPackets = 0;
		this.transmissionErrors = 0;
	}
	
	@Override
	public void updateStats() {
		this.networkIF.updateNetworkStats();
		this.interfaceSpeed = this.networkIF.getSpeed();
		this.receivedBytes = this.networkIF.getBytesRecv() - this.startReceivedBytes;
		this.trasmittedBytes = this.networkIF.getBytesSent() - this.startTransmittedBytes;
		this.receivedPackets = this.networkIF.getPacketsRecv() - this.startReceivedPackets;
		this.transmittedPackets = this.networkIF.getPacketsSent() - this.startTrasmittedPackets;
		this.transmissionErrors = this.networkIF.getOutErrors() - this.startTransmissionErrors;
	}

	public int getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public long getInterfaceSpeed() {
		return interfaceSpeed;
	}

	public void setInterfaceSpeed(long interfaceSpeed) {
		this.interfaceSpeed = interfaceSpeed;
	}

	public long getReceivedBytes() {
		return receivedBytes;
	}

	public void setReceivedBytes(long receivedBytes) {
		this.receivedBytes = receivedBytes;
	}

	public long getTrasmittedBytes() {
		return trasmittedBytes;
	}

	public void setTrasmittedBytes(long trasmittedBytes) {
		this.trasmittedBytes = trasmittedBytes;
	}

	public long getReceivedPackets() {
		return receivedPackets;
	}

	public void setReceivedPackets(long receivedPackets) {
		this.receivedPackets = receivedPackets;
	}

	public long getTransmittedPackets() {
		return transmittedPackets;
	}

	public void setTransmittedPackets(long transmittedPackets) {
		this.transmittedPackets = transmittedPackets;
	}

	public long getTransmissionErrors() {
		return transmissionErrors;
	}

	public void setTransmissionErrors(long transmissionErrors) {
		this.transmissionErrors = transmissionErrors;
	}

}
