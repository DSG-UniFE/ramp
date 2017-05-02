
package it.unibo.deis.lia.ramp.core.internode;

/**
 * 
 * @author Carlo Giannelli
 */
class ResolverRequest implements java.io.Serializable {

	private static final long serialVersionUID = -4230422813484349160L;

	private int nodeId;

	protected ResolverRequest(int nodeId) {
		this.nodeId = nodeId;
	}

	protected int getNodeId() {
		return nodeId;
	}

}
