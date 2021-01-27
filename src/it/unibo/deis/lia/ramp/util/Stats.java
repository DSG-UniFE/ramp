package it.unibo.deis.lia.ramp.util;

import java.util.List;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import it.unibo.deis.lia.ramp.RampEntryPoint;


public class Stats extends Thread {
	private final int TIMEOUT = 60 * 1000; // milliseconds
	private boolean open = false;
	private boolean supported = true;
	private long startTotalRX = 0;
	private long startTotalTX = 0;
	private android.content.Context context;
	private long rxAppBytes = 0;
	private long txAppBytes = 0;
	private long rxTotalBytes = 0;
	private long txTotalBytes = 0;
	private String packageName;

	public boolean isActive() {
		return open == true;
	}

	public Stats(android.content.Context context, String packageName) throws Exception {
		if (isAndroidContext()) {
			this.context = context;
			this.setPackageName(packageName);
			startTotalRX = android.net.TrafficStats.getTotalRxBytes();
			startTotalTX = android.net.TrafficStats.getTotalTxBytes();
			if (startTotalRX == android.net.TrafficStats.UNSUPPORTED
					|| startTotalTX == android.net.TrafficStats.UNSUPPORTED) {
				supported = false;
				System.out.println("Stats ERROR: your device does not support traffic stat monitoring");
			} else {
				open = true;
				this.start();
			}
		}
	}

	private boolean isAndroidContext() {
		if (RampEntryPoint.getAndroidContext() != null) {
			return true;
		}
		return false;
	}

	@Override
	public void run() {
		int uid;
		try {
			if (supported) {
				System.out.println("Stats START");
				while (open) {
					uid = getUID();
					if (uid > 0) {
						setRxAppBytes(android.net.TrafficStats.getUidRxBytes(uid));
						setTxAppBytes(android.net.TrafficStats.getUidTxBytes(uid));
						setRxTotalBytes(android.net.TrafficStats.getTotalRxBytes() - startTotalRX);
						setTxTotalBytes(android.net.TrafficStats.getTotalTxBytes() - startTotalTX);
					}
					synchronized (this) {
						wait(TIMEOUT);
					}
				}
			} else {
				System.out.println("Stats ERROR: your device does not support traffic stat monitoring, EXIT");
				open = false;
			}
		} catch (InterruptedException ie) {
			System.out.println("Stats: this should happen only at exit");
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Stats FINISHED");
	}

	public void deactivate() {
		System.out.println("Stats END");
		open = false;
		synchronized (this) {
			notify();
		}
	}

	private int getUID() {
		boolean find = false;
		final android.content.pm.PackageManager pm = context.getPackageManager();
		// get a list of installed apps.
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		int uid = -1;
		// loop through the list of installed packages and see if the selected
		// app is in the list
		for (ApplicationInfo packageInfo : packages) {
			if (packageInfo.packageName.equals(getPackageName())) {
				// get the UID for the selected app
				uid = packageInfo.uid;
				find = true;
				break;
			}
		}

		return uid;
	}

	public long getRxAppBytes() {
		return rxAppBytes;
	}

	private void setRxAppBytes(long rxAppBytes) {
		this.rxAppBytes = rxAppBytes;
	}

	public long getTxAppBytes() {
		return txAppBytes;
	}

	private void setTxAppBytes(long txAppBytes) {
		this.txAppBytes = txAppBytes;
	}

	public long getRxTotalBytes() {
		return rxTotalBytes;
	}

	private void setRxTotalBytes(long rxTotalBytes) {
		this.rxTotalBytes = rxTotalBytes;
	}

	public long getTxTotalBytes() {
		return txTotalBytes;
	}

	private void setTxTotalBytes(long txTotalBytes) {
		this.txTotalBytes = txTotalBytes;
	}

	public String getPackageName() {
		return packageName;
	}

	private void setPackageName(String packageName) {
		this.packageName = packageName;
	}
}
