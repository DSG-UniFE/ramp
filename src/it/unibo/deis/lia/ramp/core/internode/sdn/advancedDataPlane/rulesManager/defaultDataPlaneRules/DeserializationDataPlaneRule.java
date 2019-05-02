package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules;

import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.AbstractDataPlaneRule;

import java.io.Serializable;

public class DeserializationDataPlaneRule extends AbstractDataPlaneRule implements Serializable {

    private static final long serialVersionUID = -6549746290376459282L;

    public DeserializationDataPlaneRule() {

    }

    @Override
    public void applyRuleToUnicastPacket(UnicastPacket up) {
        System.out.println("Deserialization Rule applied");
    }
}
