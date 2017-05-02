
package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;


/**
 * 
 * @author Carlo Giannelli
 */
public interface PacketForwardingListener {

	void receivedUdpUnicastPacket(UnicastPacket up);
	void receivedUdpBroadcastPacket(BroadcastPacket bp);
	
	void receivedTcpUnicastPacket(UnicastPacket up);
	void receivedTcpUnicastHeader(UnicastHeader uh);
	void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk);
	void receivedTcpBroadcastPacket(BroadcastPacket bp);
	
	void sendingTcpUnicastPacketException(UnicastPacket up, Exception e);
	void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e);
	
}
