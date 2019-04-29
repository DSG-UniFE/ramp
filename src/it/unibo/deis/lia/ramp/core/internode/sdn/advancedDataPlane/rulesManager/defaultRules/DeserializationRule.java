package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultRules;

import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

import java.io.Serializable;

public class DeserializationRule implements Serializable {

    private static final long serialVersionUID = -6549746290376459282L;

    public DeserializationRule() {

    }

    public void applyRule(UnicastPacket up) {
        System.out.println("Deserialization Rule applied");
    }
}
