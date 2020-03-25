package test.sdncontroller;

import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;
import it.unibo.deis.lia.ramp.core.internode.sdn.advancedDataPlane.dataTypesManager.defaultDataTypes.VibrationDataType;

import java.time.LocalDateTime;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class ControllerMessageMLRTestReceiver {
    private long receivedTimeMilliseconds;
    private LocalDateTime receivedTime;
    private GenericPacket genericPacket;
    private Object object;

    public ControllerMessageMLRTestReceiver(LocalDateTime receivedTime, GenericPacket genericPacket) {
        this.receivedTimeMilliseconds = -1;
        this.receivedTime = receivedTime;
        this.genericPacket = genericPacket;
        this.object = null;
    }

    public ControllerMessageMLRTestReceiver(long receivedTimeMilliseconds, LocalDateTime receivedTime, GenericPacket genericPacket) {
        this.receivedTimeMilliseconds = receivedTimeMilliseconds;
        this.receivedTime = receivedTime;
        this.genericPacket = genericPacket;
        this.object = null;
    }

    public ControllerMessageMLRTestReceiver(LocalDateTime receivedTime, Object object) {
        this.receivedTimeMilliseconds = -1;
        this.receivedTime = receivedTime;
        this.genericPacket = null;
        this.object = object;
    }

    public void setReceivedTimeMilliseconds(long receivedTimeMilliseconds) {
        this.receivedTimeMilliseconds = receivedTimeMilliseconds;
    }

    public long getReceivedTimeMilliseconds() {
        return this.receivedTimeMilliseconds;
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

    public void setObject(Object object) {
        this.object = object;
    }

    public Object getObject() {
        return this.object;
    }
}
