package it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.defaultDataPlaneRules;

import it.unibo.deis.lia.ramp.core.e2e.BroadcastPacket;
import it.unibo.deis.lia.ramp.core.e2e.UnicastHeader;
import it.unibo.deis.lia.ramp.core.e2e.UnicastPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.rulesManager.AbstractDataPlaneRule;

import java.io.Serializable;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * Class useful in case you want to simulate a delay in the communication
 * for a specific DataType
 */
public class AddDelayDataPlaneRule extends AbstractDataPlaneRule implements Serializable {

    private static final long serialVersionUID = -3993192478895803424L;

    /**
     * Insert the serialVersionUID of the object
     * class you want to delay
     */
    private long dataTypeId = 793107902207408161L;

    public AddDelayDataPlaneRule() {
    }

    @Override
    public void applyRuleToUnicastHeader(UnicastHeader uh) {
        applyRulePolicy(uh.getDataType());
    }

    @Override
    public void applyRuleToUnicastPacket(UnicastPacket up) {
        applyRulePolicy(up.getHeader().getDataType());
    }

    @Override
    public void applyRuleToBroadcastPacket(BroadcastPacket bp) {
        applyRulePolicy(bp.getDataType());
    }

    private void applyRulePolicy(long dataTypeId) {
        if(dataTypeId == this.dataTypeId) {
            /*
             * If the dataTypeId belongs to VibrationDataType class
             * perform a sleep
             */
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("AddDelayDataPlaneRule: dataTypeId: " + dataTypeId);
        }
    }
}
