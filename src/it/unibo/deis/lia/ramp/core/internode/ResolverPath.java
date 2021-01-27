
package it.unibo.deis.lia.ramp.core.internode;

import java.util.Arrays;

/**
 * 
 * @author Carlo Giannelli
 */
public class ResolverPath {
	private String[] path;
	private long lastUpdate;

	public ResolverPath(String[] path) {
		this.path = path;
		this.lastUpdate = System.currentTimeMillis();
	}

	public String[] getPath() {
		return path;
	}

	public long getLastUpdate() {
		return lastUpdate;
	}

	@Override
	public boolean equals(Object object) {
		boolean res = true;
		if (!(object instanceof ResolverPath)) {
			res = false;
		} else {
			ResolverPath rp = (ResolverPath) object;
			if (this.path.length != rp.path.length) {
				res = false;
			} else {
				for (int i = 0; res == true && i < this.path.length; i++) {
					if (!this.path[i].equals(rp.path[i])) {
						res = false;
					}
				}
			}
		}
		return res;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 73 * hash + (this.path != null ? this.path.hashCode() : 0);
		return hash;
	}

	@Override
	public String toString() {
		return "(" + Arrays.toString(path) + " " + lastUpdate + ")";
	}

}
