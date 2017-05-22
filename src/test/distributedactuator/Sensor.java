package test.distributedactuator;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorClientListener;
import it.unibo.deis.lia.ramp.core.internode.DistributedActuatorRequest;
import it.unibo.deis.lia.ramp.exception.ImagesMismatchException;
import it.unibo.deis.lia.ramp.util.ImageComparison;

public class Sensor extends Thread implements DistributedActuatorClientListener {
	
	private static String IMG_PATH = "/tmp/images/img.jpg";
	private boolean open = false;
	private float threshold;
	private float resilience;
	private String command = null;
	BufferedImage oldImg = null;
	
	
	public Sensor(){
		setOpen(true);;
	}
	
	@Override
	public void run() {
		while(open) {
			try {
				if (getCommand() != null) {
					executeCommand(getCommand());
					if (oldImg == null) {
						oldImg = ImageIO.read(new File(IMG_PATH));
					}
					BufferedImage img = ImageIO.read(new File(IMG_PATH));
				    
					double result = ImageComparison.imageDifference(oldImg, img);
				    System.out.println("Difference: " +  new DecimalFormat("#.###").format(result) + "%");
				    
				    if (result > threshold) {
				    	System.out.println(">>>ATTENTION<<< The threshold exceeded");
				    }
				}
				sleep(5*1000);
			} catch (InterruptedException | IOException | ImagesMismatchException e) {
				e.printStackTrace();
			}
		}
		// periodically retrieve pictures (5 seconds)
		// send alert if images are different more than threshold
	}

	@Override
	public void activateResilience() {
		System.out.println("Sensor.activateResilience");
		// modify the threshold according to the saved resilience
	}

	@Override
	public void receivedCommand(DistributedActuatorRequest dar) {
		System.out.println("Sensor.receivedCommand: " + dar);
		String[] commands = dar.getCommand().split(",");
		setCommand(commands[0].split("=")[1]);
		setResilience(Float.parseFloat(commands[1].split("=")[1]));
		
		// modify the threshold
	}

	private void executeCommand(String command) {
		// Build command
        List<String> commands = new ArrayList<String>();
        commands.add("/bin/sh");
        commands.add("-c");

        // Add arguments
        commands.add("python /home/pi/workspace/...");
        System.out.println(commands);

        try {
        	// Run macro on target
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.directory(new File("/home/pi/workspace/..."));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
        	// Read output
            StringBuilder out = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null, previous = null;
			while ((line = br.readLine()) != null)
			    if (!line.equals(previous)) {
			        previous = line;
			        out.append(line).append('\n');
			        System.out.println(line);
			    }
			
			// Check result
			if (process.waitFor() == 0) {
				System.out.println("Success!");
//	            System.exit(0);
			}
			
			// Abnormal termination: Log command parameters and output and throw ExecutionException
//	        System.err.println(commands);
	        System.err.println(out.toString());
//	        System.exit(1);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;
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