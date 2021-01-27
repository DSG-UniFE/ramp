package test.sdncontroller;

import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VibrationDataType;

import java.time.LocalDateTime;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessageTestReceiver {
    private LocalDateTime receivedTime;
    private GenericPacket genericPacket;

    public ControllerMessageTestReceiver(LocalDateTime receivedTime, GenericPacket genericPacket) {
        this.receivedTime = receivedTime;
        this.genericPacket = genericPacket;
    }

    public void setReceivedTime(LocalDateTime receivedTime) {
        this.receivedTime = receivedTime;
    }

    public LocalDateTime getReceivedTime() {
        return this.receivedTime;
    }

    public void setGenericPacket(GenericPacket genericPacket) {
        this.genericPacket = genericPacket;
    }

    public GenericPacket getGenericPacket() {
        return this.genericPacket;
    }
}
