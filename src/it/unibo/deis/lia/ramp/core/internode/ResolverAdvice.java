
package it.unibo.deis.lia.ramp.core.internode;

/**
 * 
 * @author Carlo Giannelli
 */
public class ResolverAdvice implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4219358506331142668L;

	private int nodeId;
	private String[] brokenPath;
	private int failedHop;
	private String[] newPath;

	protected ResolverAdvice(int nodeId) {
		this.nodeId = nodeId;
		this.brokenPath = null;
		this.failedHop = -1;
		this.newPath = null;
	}

	protected ResolverAdvice(int nodeId, String[] brokenPath, int failedHop, String[] newPath) {
		this.nodeId = nodeId;
		this.brokenPath = brokenPath;
		this.failedHop = failedHop;
		this.newPath = newPath;
	}

	protected String[] getBrokenPath() {
		return brokenPath;
	}

	protected int getNodeId() {
		return nodeId;
	}

	protected int getFailedHop() {
		return failedHop;
	}

	protected String[] getNewPath() {
		return newPath;
	}

}
