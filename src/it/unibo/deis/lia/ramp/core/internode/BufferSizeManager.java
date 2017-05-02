
package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.*;

/**
 * 
 * @author Carlo Giannelli
 */
public class BufferSizeManager implements PacketForwardingListener {

	private static BufferSizeManager bufferSizeManager = null;

	public static synchronized BufferSizeManager getInstance() {
		if (bufferSizeManager == null) {
			bufferSizeManager = new BufferSizeManager();
			Dispatcher.getInstance(false).addPacketForwardingListener(bufferSizeManager);
			System.out.println("BufferSizeManager ENABLED");
		}
		return bufferSizeManager;
	}

	public static void deactivate() {
		if (bufferSizeManager != null) {
			System.out.println("BufferSizeManager DISABLED");
			Dispatcher.getInstance(false).removePacketForwardingListener(bufferSizeManager);
			bufferSizeManager = null;
		}
	}

	private int localBufferSize = 100 * 1024;

	public int getLocalBufferSize() {
		return localBufferSize;
	}

	public void setLocalBufferSize(int localBufferSize) throws Exception {
		if (localBufferSize < 0) {
			throw new Exception("Illegal localBufferSize: should be equal to or greater than 0, but localBufferSize=" + localBufferSize);
		} else if (localBufferSize == 0) {
			this.localBufferSize = E2EComm.DEFAULT_BUFFERSIZE;
		} else {
			this.localBufferSize = localBufferSize;
		}
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
		uh.setBufferSize(localBufferSize);
	}

	@Override
	public void receivedUdpUnicastPacket(UnicastPacket up) {
		// do nothing
	}

	@Override
	public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
		// do nothing
	}

	@Override
	public void receivedTcpUnicastPacket(UnicastPacket up) {
		// do nothing
	}

	@Override
	public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
		// do nothing
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
		// do nothing
	}

	@Override
	public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
		// do nothing
	}

	@Override
	public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
		// do nothing
	}

}
