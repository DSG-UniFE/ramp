package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.AbstractDataPlaneRule;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This default DataPlaneRule prints to System.out the DataTypeId (a.k.a SerialVersionUID) of the packet.
 */
public class PrintDataTypeIdDataPlaneRule extends AbstractDataPlaneRule implements Serializable {
    private static final long serialVersionUID = 7084344352852358422L;

    public PrintDataTypeIdDataPlaneRule() {
    }

    @Override
    public  void applyRuleToUnicastHeader(UnicastHeader uh) {
        long dataTypeId = uh.getDataType();
        System.out.println("PrintDataTypeIdDataPlaneRule: dataTypeId: " + dataTypeId);
    }

    @Override
    public void applyRuleToUnicastPacket(UnicastPacket up) {
        long dataTypeId = up.getHeader().getDataType();
        System.out.println("PrintDataTypeIdDataPlaneRule: dataTypeId: " + dataTypeId);
    }

    @Override
    public void applyRuleToBroadcastPacket(BroadcastPacket bp) {
        long dataTypeId = bp.getDataType();
        System.out.println("PrintDataTypeIdDataPlaneRule: dataTypeId: " + dataTypeId);
    }
}
