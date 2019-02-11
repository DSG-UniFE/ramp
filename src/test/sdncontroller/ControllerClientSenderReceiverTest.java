package test.sdncontroller;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.ControllerClient;
import it.unibo.deis.lia.ramp.core.internode.ControllerService;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
import test.sdncontroller.ControllerClientTestSender.StatsPrinter;

public class ControllerClientSenderReceiverTest {

	private static RampEntryPoint ramp;
	private static ControllerService controllerService;
	private static ControllerClient controllerClient;
	
	public static void main(String[] args) {

		ramp = RampEntryPoint.getInstance(true, null);

		controllerService = ControllerService.getInstance();
		
		// Wait a few second to allow the node to discover neighbors
		try {
			Thread.sleep(5*1000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		// Force neighbors update to make sure to know them
		ramp.forceNeighborsUpdate();
		
		System.out.println("ControllerClientTestReceiver: registering shutdown hook");
		// Setup signal handling in order to always stop RAMP gracefully
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (ramp != null && controllerClient != null) {
						System.out.println("ShutdownHook is being executed: gracefully stopping RAMP...");
						ServiceManager.getInstance(false).removeService("SDNControllerTestSendFirst");
						ServiceManager.getInstance(false).removeService("SDNControllerTestSendSecond");
						controllerClient.stopClient();
						controllerService.stopService();
						ramp.stopRamp();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}));

		controllerClient = ControllerClient.getInstance();
		
		new Thread() {
			@Override
			public void run() {
				try {
						// Start Receiver
						ControllerClientTestReceiver.receiveTwoSeriesOfPacketsInDifferentThreads();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
		
		
		// Wait few seconds
		try {
			Thread.sleep(5*1000);
		} catch (InterruptedException e2) {
			e2.printStackTrace();
		}
		
		// Start Sender
		ControllerClientTestSender.StatsPrinter statsPrinter = new StatsPrinter("output_external.csv");
		statsPrinter.start();
		try {
			// TODO RemoveComment Dmitrij
			//ControllerClientTestSender.sendTwoSeriesOfPacketsToDifferentReceivers(controllerClient);
		} catch (Exception e) {
			e.printStackTrace();
		}
		statsPrinter.stopStatsPrinter();
		
		
		controllerClient.stopClient();
		ramp.stopRamp();
	}

}
