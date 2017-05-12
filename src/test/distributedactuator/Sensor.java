package test.distributedactuator;

import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClientListener;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorRequest;

public class Sensor implements DistributedActuatorClientListener{

	@Override
	public void activateResilience() {
		System.out.println("DistributedActuatorClientTest.activateResilience");
	}

	@Override
	public void receivedCommand(DistributedActuatorRequest dar) {
		System.out.println("DistributedActuatorClientTest.receivedCommand: "+dar);
	}
	
}