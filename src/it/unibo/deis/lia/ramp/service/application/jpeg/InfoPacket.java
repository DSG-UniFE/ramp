package it.unibo.deis.lia.ramp.service.application.jpeg;

import java.io.Serializable;

public class InfoPacket implements Serializable {

	private static final long serialVersionUID = 6726275347195944709L;
	
	private byte info;
	
	public InfoPacket(byte info) {
		this.info = info;
	}
	
	public byte getInfo() {
		return info;
	}

}
