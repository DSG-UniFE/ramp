package it.unibo.deis.lia.ramp.util;

import java.io.Serializable;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

public class CPUMemoryStats implements NodeStats, Serializable {
	
	private static final long serialVersionUID = 4426898218025406538L;
	
	private CentralProcessor centralProcessor;
	private GlobalMemory globalMemory;
	private int nodeId;
	private int logicalProcessorCount;
	private double systemLoadAverage;
	private double systemLoad; // value in [0.0, 1.0]
	private long totalMemory; // bytes
	private long availableMemory; // bytes
	
	public CPUMemoryStats(int nodeId) {
		SystemInfo systemInfo = new SystemInfo();
		this.centralProcessor = systemInfo.getHardware().getProcessor();
		this.globalMemory = systemInfo.getHardware().getMemory();
	}
	
	@Override
	public void updateStats() {
		this.logicalProcessorCount = this.centralProcessor.getLogicalProcessorCount();
		this.systemLoadAverage = this.centralProcessor.getSystemLoadAverage();
		this.systemLoad = this.centralProcessor.getSystemCpuLoad();
		this.totalMemory = this.globalMemory.getTotal();
		this.availableMemory = this.globalMemory.getAvailable();
	}

	public int getNodeId() {
		return this.nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public int getLogicalProcessorCount() {
		return logicalProcessorCount;
	}

	public void setLogicalProcessorCount(int logicalProcessorCount) {
		this.logicalProcessorCount = logicalProcessorCount;
	}

	public double getSystemLoadAverage() {
		return systemLoadAverage;
	}

	public void setSystemLoadAverage(double systemLoadAverage) {
		this.systemLoadAverage = systemLoadAverage;
	}

	public double getSystemLoad() {
		return systemLoad;
	}

	public void setSystemLoad(double systemLoad) {
		this.systemLoad = systemLoad;
	}

	public long getTotalMemory() {
		return totalMemory;
	}

	public void setTotalMemory(long totalMemory) {
		this.totalMemory = totalMemory;
	}

	public long getAvailableMemory() {
		return availableMemory;
	}

	public void setAvailableMemory(long availableMemory) {
		this.availableMemory = availableMemory;
	}

}
