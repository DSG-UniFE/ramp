package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;

import java.net.SocketAddress;
import java.time.LocalDateTime;

public class TestReceiverPacketOsRouting {
    private LocalDateTime receivedTime;
    private SDNControllerMessage controllerMessage;
    private SocketAddress socketAddress;

    public TestReceiverPacketOsRouting(LocalDateTime receivedTime, SDNControllerMessage controllerMessage, SocketAddress socketAddress) {
        this.receivedTime = receivedTime;
        this.controllerMessage = controllerMessage;
        this.socketAddress = socketAddress;
    }

    public void setReceivedTime(LocalDateTime receivedTime) {
        this.receivedTime = receivedTime;
    }

    public LocalDateTime getReceivedTime() {
        return this.receivedTime;
    }

    public void setControllerMessage(SDNControllerMessage controllerMessage) {
        this.controllerMessage = controllerMessage;
    }

    public SDNControllerMessage getControllerMessage() {
        return this.controllerMessage;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public void setSocketAddress(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }
}
