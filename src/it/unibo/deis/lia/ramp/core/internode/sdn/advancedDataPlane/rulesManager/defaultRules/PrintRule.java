package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultRules;

import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

import java.io.Serializable;

public class PrintRule implements Serializable {
    private static final long serialVersionUID = 7084344352852358422L;

    public PrintRule() {
    }

    public void applyRule(UnicastPacket up) {
        System.out.println("Print Rule applied");
    }
}
