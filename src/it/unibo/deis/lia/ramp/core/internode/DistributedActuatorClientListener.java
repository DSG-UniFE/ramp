package it.unibo.deis.lia.ramp.core.internode;

public interface DistributedActuatorClientListener {
	
	public void activateResilience();
	public void receivedCommand(DistributedActuatorRequest dar);
	
}
