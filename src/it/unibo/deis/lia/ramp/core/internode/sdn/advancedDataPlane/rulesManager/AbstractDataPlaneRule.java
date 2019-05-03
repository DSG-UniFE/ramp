package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 */
public abstract class AbstractDataPlaneRule implements Serializable {
    private static final long serialVersionUID = 2551324504447890609L;

    public AbstractDataPlaneRule() {

    }

    /**
     * This rule is used when a fragmented packet arrives, in particular
     * the rule is called as soon as the UnicastHeader is received.
     * @param uh incoming UnicastHeader
     */
    public void applyRuleToUnicastHeader(UnicastHeader uh) { }

    /**
     * This rule is called when a UnicastPacket arrives.
     * @param up incoming UnicastPacket
     */
    public void applyRuleToUnicastPacket(UnicastPacket up) { }

    /**
     * This rule is called when a BroadcastPacket arrives.
     * @param bp incoming BroadcastPacket
     */
    public void applyRuleToBroadcastPacket(BroadcastPacket bp) { }
}
