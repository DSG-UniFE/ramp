package test.distributedactuator;

import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClientListener;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorRequest;
import it.unibo.deis.lia.ramp.util.Benchmark;

public class Sensor2 extends Thread implements DistributedActuatorClientListener {

	private boolean open = false;
	// resilience from 0 to 100
	private float resilience = 40;
	private String command = null;


	public Sensor2(){
		setOpen(true);;
	}

	@Override
	public void run() {
		while(open) {
			try {
				Benchmark.append(System.currentTimeMillis(), "s_executed_command", 0, 0, 0);
				sleep(5*1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		// periodically retrieve pictures (5 seconds)
		// send alert if images are different more than threshold
	}

	@Override
	public void activateResilience() {
		Benchmark.append(System.currentTimeMillis(), "s_activated_resilience", 0, 0, 0);
		System.out.println("Sensor.activateResilience");
		setResilience(50);
	}

	@Override
	public void receivedCommand(DistributedActuatorRequest dar) {
		Benchmark.append(System.currentTimeMillis(), "s_received_command", 0, 0, 0);
		System.out.println("Sensor.receivedCommand: " + dar);
		String[] commands = dar.getCommand().split(",");
		setCommand(commands[0].split("=")[1]);
		setResilience(Float.parseFloat(commands[1].split("=")[1]));
	}

	public void stopSensor() {
		setOpen(false);
	}

	public float getResilience() {
		return resilience;
	}

	public void setResilience(float resilience) {
		this.resilience = resilience;
	}

	public boolean isOpen() {
		return open;
	}

	public void setOpen(boolean open) {
		this.open = open;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

}