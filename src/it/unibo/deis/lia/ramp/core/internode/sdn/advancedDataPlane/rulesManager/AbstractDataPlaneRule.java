package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

import java.io.Serializable;

public class AbstractDataPlaneRule implements Serializable {
    private static final long serialVersionUID = 2551324504447890609L;

    public AbstractDataPlaneRule() {

    }

    public void applyRuleToUnicastPacket(UnicastPacket up) {

    }

    public void applyRuleToBroadcastPacket(BroadcastPacket bp) {

    }
}
