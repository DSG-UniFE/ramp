package it.unibo.deis.lia.ramp.core.internode.sdn.dataPlaneForwarder;

import it.unibo.deis.lia.ramp.core.internode.PacketForwardingListener;

/**
 * @author Alessandro Dolci
 */
public interface DataPlaneForwarder extends PacketForwardingListener {
    void deactivate();
}
