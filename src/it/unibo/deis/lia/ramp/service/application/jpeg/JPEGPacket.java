package it.unibo.deis.lia.ramp.service.application.jpeg;

import java.io.Serializable;

public class JPEGPacket implements Serializable {

	private static final long serialVersionUID = -8909446747837624172L;

	private byte[] data;
	private byte quality;
	private short id;
	private static short count = 0;

	public JPEGPacket(byte[] data, byte quality) {
		this.data = data;
		this.quality = quality;
		this.id = count;
		count++;
	}

	public short getId() {
		return id;
	}

	public byte[] getData() {
		return data;
	}
	
	public byte getQuality() {
		return quality;
	}

}
