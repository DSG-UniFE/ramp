package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;

import java.io.Serializable;

public class CBRPacket implements Serializable, Comparable<CBRPacket> {
	
	private static final long serialVersionUID = 5761776228098775355L;
	
	private static int CREATION_COUNTER = 0;
	public static int PAYLOAD_DEFAULT_SIZE = 5 * 1024; // 5KB
	private static byte[] staticPayload;
	
	static {
		// initialize staticPayload with random bytes
		staticPayload = new byte[PAYLOAD_DEFAULT_SIZE];
		int i;
		for(i = 0; i < staticPayload.length; i++){
			byte randomByte = (byte) RampEntryPoint.nextRandomInt();
			staticPayload[i++] = randomByte;
		}
		//System.out.println("CBRPacket staticPayload initialized with size: " + i/1024 + " KB");
	}
	
	private long creationTimestamp;
	private int id;
	private byte[] payload;
	
	public CBRPacket() {
		creationTimestamp = System.currentTimeMillis();
		id = CREATION_COUNTER++;
		payload = staticPayload;
	}
	
	public long getCreationTimestamp() {
		return creationTimestamp;
	}

	public int getId() {
		return id;
	}
	
	public int getSize() {
		return payload.length;
	}

	@Override
	public String toString() {
		return "CBRPacket [id=" + id +", creationTimestamp=" + creationTimestamp + "]";
	}

	@Override
	public int compareTo(CBRPacket o) {
		int otherId = o.id;
		if(id < otherId)
			return -1;
		else if(id > otherId)
			return 1;
		else
			return 0;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null)
			return false;
		if(obj instanceof CBRPacket){
			CBRPacket other = (CBRPacket) obj;
			if(id == other.id)
				return true;
			return false;
		}else{
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return id;
	}
}
