package test.sdncontroller;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.ControllerService;

public class ControllerTest {
	
	public static void main(String[] args) {
		RampEntryPoint.getInstance(true, null);
		
		ControllerService controllerService = ControllerService.getInstance();
		ControllerClient controllerClient = ControllerClient.getInstance();
	}

}
