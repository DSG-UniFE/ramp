package it.unibo.deis.lia.ramp.core.internode;

import java.io.Serializable;

import it.unibo.deis.lia.ramp.core.internode.OpportunisticNetworkingManager.ReplacePackets;

/**
*
* @author Stefano Lanzone
*/
public class OpportunisticNetworkingSettings implements Serializable {

	private static final long serialVersionUID = -7329696785517184049L;
   
	private int sendPacketsPeriod;            //seconds
	private int expirationTimeManagedPackets; //minutes
	private boolean persistPackets;
	private boolean removePacketAfterSend;
	private int availableStorage;             //MB
	
	private int packetSizeThresholdHigher;    //KB
	private int packetSizeThresholdLower;     //KB
	private int minNumberOfOneHopDestination;
	private int maxNumberOfOneHopDestination;
	private int numberOfOneHopDestinations;
	
	private String replacePackets;    //mechanism to replace in case of storage unavailable

	//Getters and Setters
	public int getSendPacketsPeriod() {
		return sendPacketsPeriod;
	}

	public void setSendPacketsPeriod(int sendPacketsPeriod) {
		this.sendPacketsPeriod = sendPacketsPeriod;
	}

	public int getExpirationTimeManagedPackets() {
		return expirationTimeManagedPackets;
	}

	public void setExpirationTimeManagedPackets(int expirationTimeManagedPackets) {
		this.expirationTimeManagedPackets = expirationTimeManagedPackets;
	}

	public boolean isPersistPackets() {
		return persistPackets;
	}

	public void setPersistPackets(boolean persistPackets) {
		this.persistPackets = persistPackets;
	}
	
	public boolean isRemovePacketAfterSend() {
		return removePacketAfterSend;
	}

	public void setRemovePacketAfterSend(boolean removePacketAfterSend) {
		this.removePacketAfterSend = removePacketAfterSend;
	}

	public int getAvailableStorage() {
		return availableStorage;
	}

	public void setAvailableStorage(int availableStorage) {
		this.availableStorage = availableStorage;
	}

	public int getNumberOfOneHopDestinations() {
		return numberOfOneHopDestinations;
	}

	public void setNumberOfOneHopDestinations(int numberOfOneHopDestinations) {
		this.numberOfOneHopDestinations = numberOfOneHopDestinations;
	}
	
	public int getMinNumberOfOneHopDestinations() {
		return minNumberOfOneHopDestination;
	}

	public void setMinNumberOfOneHopDestinations(int minNumberOfOneHopDestination) {
		this.minNumberOfOneHopDestination = minNumberOfOneHopDestination;
	}
	
	public int getMaxNumberOfOneHopDestinations() {
		return maxNumberOfOneHopDestination;
	}

	public void setMaxNumberOfOneHopDestinations(int maxNumberOfOneHopDestination) {
		this.maxNumberOfOneHopDestination = maxNumberOfOneHopDestination;
	}
	
	public int getPacketSizeThresholdHigher() {
		return packetSizeThresholdHigher;
	}

	public void setPacketSizeThresholdHigher(int packetSizeThresholdHigher) {
		this.packetSizeThresholdHigher = packetSizeThresholdHigher;
	}
	
	public int getPacketSizeThresholdLower() {
		return packetSizeThresholdLower;
	}

	public void setPacketSizeThresholdLower(int packetSizeThresholdLower) {
		this.packetSizeThresholdLower = packetSizeThresholdLower;
	}
	
	public String getReplacePackets() {
		return replacePackets;
	}

	public void setReplacePackets(String replacePackets) {
		this.replacePackets = replacePackets;
	}
	
	public OpportunisticNetworkingSettings()
	{
		 //Default values
		 this.sendPacketsPeriod = 10;
		 this.expirationTimeManagedPackets = 720; //12 ore
		 this.persistPackets = true;
		 this.removePacketAfterSend = true;
		 this.availableStorage = 100; 
		 this.minNumberOfOneHopDestination = 2;
		 this.numberOfOneHopDestinations = 4;
		 this.maxNumberOfOneHopDestination = 6;
		 this.packetSizeThresholdHigher = 100;
		 this.packetSizeThresholdLower = 50;
		 this.replacePackets = ReplacePackets.OLD.toString();
	}
}
