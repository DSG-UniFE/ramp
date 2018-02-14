package it.unibo.deis.lia.ramp.core.internode;

import java.util.List;

/**
 * 
 * @author Alessandro Dolci
 *
 */
public class MulticastPathDescriptor extends PathDescriptor {

	private static final long serialVersionUID = 5550982447466461542L;

	private int destPort;
	
	public MulticastPathDescriptor(String[] path, List<Integer> pathNodeIds, int destPort) {
		super(path, pathNodeIds);
		this.destPort = destPort;
	}

	public int getDestPort() {
		return destPort;
	}

	public void setDestPort(int destPort) {
		this.destPort = destPort;
	}

}
