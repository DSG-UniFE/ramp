package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;

import java.time.LocalDateTime;

public class TestReceiverPacket {

    private LocalDateTime receivedTime;
    private GenericPacket genericPacket;

    public TestReceiverPacket(LocalDateTime receivedTime, GenericPacket genericPacket) {
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
