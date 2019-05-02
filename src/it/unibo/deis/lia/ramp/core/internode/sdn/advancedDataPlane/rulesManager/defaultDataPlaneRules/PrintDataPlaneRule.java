package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules;

import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.AbstractDataPlaneRule;

import java.io.Serializable;

public class PrintDataPlaneRule extends AbstractDataPlaneRule implements Serializable {
    private static final long serialVersionUID = 7084344352852358422L;

    public PrintDataPlaneRule() {
    }

    @Override
    public void applyRuleToUnicastPacket(UnicastPacket up) {
        System.out.println("Print Rule applied");
    }
}
