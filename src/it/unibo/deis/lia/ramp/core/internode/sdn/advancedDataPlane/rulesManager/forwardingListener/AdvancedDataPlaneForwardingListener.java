package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.forwardingListener;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.PacketForwardingListener;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class AdvancedDataPlaneForwardingListener implements PacketForwardingListener {

    @Override
    public void receivedUdpUnicastPacket(UnicastPacket up) {
        receivedTcpUnicastPacket(up);
    }

    @Override
    public void receivedUdpBroadcastPacket(BroadcastPacket bp) {

    }

    @Override
    public void receivedTcpUnicastPacket(UnicastPacket up) {
        receivedTcpUnicastHeader(up.getHeader());
    }

    @Override
    public void receivedTcpUnicastHeader(UnicastHeader uh) {
        if (uh.getDataType() != GenericPacket.UNUSED_FIELD) {
            long dataTypeId = uh.getDataType();
            System.out.println("AdvancedDataPlaneForwardingListener: data type: " + dataTypeId);
        }
    }

    @Override
    public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {

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
}
