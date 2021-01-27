/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application;

/**
 *
 * @author useruser
 */
public class BroadcastClientQualityData {
	
	// RTP Sequence Number
    private int serialNumber;
    
    // Packet Delay Variation in micros
    private int pdvRamp;
    private int packetIntervalRamp;
    
    // pdv related to RTP timestamp
    private int pdvRtp;
    private int timestampIntervalRtp;

    public BroadcastClientQualityData(int pdvRamp, int serialNumber, int packetIntervalRamp) {
        this.pdvRamp = pdvRamp;
        this.serialNumber = serialNumber;
        this.packetIntervalRamp = packetIntervalRamp;
    }

    public int getPdvRamp() {
        return pdvRamp;
    }

    public int getSerialNumber() {
        return serialNumber;
    }

    public int getPacketIntervalRamp() {
        return packetIntervalRamp;
    }

	public int getPdvRtp() {
		return pdvRtp;
	}
	public void setPdvRtp(int pdvRtp) {
		this.pdvRtp = pdvRtp;
	}

	public int getTimestampIntervalRtp() {
		return timestampIntervalRtp;
	}
	public void setTimestampIntervalRtp(int timestampIntervalRtp) {
		this.timestampIntervalRtp = timestampIntervalRtp;
	}
    
}
