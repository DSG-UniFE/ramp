/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.core.internode;

import it.unibo.deis.lia.ramp.core.e2e.*;
import it.unibo.deis.lia.ramp.*;

import java.io.*;

/**
 * 
 * @author useruser
 */
public class Layer3RoutingManager implements PacketForwardingListener {

	private static String superuserPassword = null;
	private static long lastSuperuserPasswordRefresh = -1;
	private static final long TIMEOUT_SU_PASSWORD = 10 * 1000;

	private static Layer3RoutingManager layer3RoutingManager = null;

	synchronized public static Layer3RoutingManager getInstance() throws Exception {
		if (layer3RoutingManager == null) {

			if (!RampEntryPoint.os.startsWith("linux")) {
				throw new Exception("Unsupported Operating System: " + System.getProperty("os.name"));
			}

			layer3RoutingManager = new Layer3RoutingManager();

			// activate packet forwarding
			try {
				Layer3RoutingManager.activatePacketForwarding();

				Dispatcher.getInstance(false).addPacketForwardingListener(layer3RoutingManager);
				System.out.println("Layer3RoutingManager ENABLED");

			} catch (Exception e) {
				layer3RoutingManager = null;
				throw e;
			}
		}
		return layer3RoutingManager;
	}

	public static void deactivate() {// throws Exception{
		if (layer3RoutingManager != null) {
			Dispatcher.getInstance(false).removePacketForwardingListener(layer3RoutingManager);

			// deactivate packet forwarding
			Layer3RoutingManager.deactivatePacketForwarding();

			layer3RoutingManager = null;
			System.out.println("Layer3RoutingManager DISABLED");
		}
	}

	@Override
	public void receivedUdpUnicastPacket(UnicastPacket up) {
		receivedTcpUnicastPacket(up);
	}

	@Override
	public void receivedTcpUnicastPacket(UnicastPacket up) {
		try {
			Object payload = E2EComm.deserialize(up.getBytePayload());
			if (payload instanceof Layer3RoutingRequest) {
				// System.out.println("Layer3RoutingManager new Layer3RoutingRequest");

				String[] source = up.getSource();
				// System.out.println("Layer3RoutingManager source "+source);
				String prev = null;
				if (source.length == 0) {
					// System.out.println("Layer3RoutingManager the first hop");
				} else {
					// System.out.println("Layer3RoutingManager not the first hop");
					prev = source[source.length - 1];
				}

				String[] dest = up.getDest();
				// System.out.println("Layer3RoutingManager dest "+dest);
				String next = null;
				int currentHop = up.getCurrentHop();
				if (dest.length > currentHop) {
					// not the last hop
					// System.out.println("Layer3RoutingManager not the last hop");
					next = dest[currentHop];
				} else {
					// the last hop
					// System.out.println("Layer3RoutingManager the last hop");
				}

				setLayer3NextHop(prev, next);
			} else {
				// do nothing...
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void setLayer3NextHop(String from, String to) throws Exception {
		System.out.println("Layer3RoutingManager setLayer3NextHop from=" + from + " to=" + to);
		if (RampEntryPoint.os.startsWith("windows")) {
			try {
				if (from != null) {
					// 1) enable NAT
					/*
					 * String com1 = "net stop SharedAccess"; System.out.println("Layer3RoutingManager "+com1); Process p1 = Runtime.getRuntime().exec(com1); p1.waitFor(); p1.destroy();
					 */

					String com2 = "net stop RemoteAccess";
					System.out.println("Layer3RoutingManager " + com2);
					Process p2 = Runtime.getRuntime().exec(com2);
					p2.waitFor();
					p2.destroy();

					String com3 = "net start RemoteAccess";
					System.out.println("Layer3RoutingManager " + com3);
					Process p3 = Runtime.getRuntime().exec(com3);
					p3.waitFor();
					p3.destroy();

					// Vista
					// netsh>interf ipv4
					// netsh interface ipv4>set interface "8" forwarding=enable

					// XP
					/*
					 * String pre = "netsh routing ip nat ";
					 * 
					 * String com4 = pre + "uninstall"; System.out.println("Layer3RoutingManager "+com4); Process p4 = Runtime.getRuntime().exec(com4); p4.waitFor(); p4.destroy();
					 * 
					 * String com5 = pre + "install"; System.out.println("Layer3RoutingManager "+com5); Process p5 = Runtime.getRuntime().exec(com5); p5.waitFor(); p5.destroy();
					 * 
					 * String com6 = pre + "set global tcptimeoutmins=1440 udptimeoutmins=1 loglevel=ERROR"; System.out.println("Layer3RoutingManager "+com6); Process p6 = Runtime.getRuntime().exec(com6); p6.waitFor(); p6.destroy();
					 */

					/*
					 * if(to!=null){ String interfTo = Layer3RoutingManager.fromIpToName(to.getHostAddress()); String com7 = pre + "add interface name=\""+interfTo+"\" private"; System.out.println("Layer3RoutingManager "+com7); Process p7 = Runtime.getRuntime().exec(com7); p7.waitFor();
					 * p7.destroy(); } else{ // String com8 = pre + "add interface name=\""+interfDefaultGateway+"\" private"; }
					 */

					/*
					 * String interfFrom = Layer3RoutingManager.fromIpToName(from.getHostAddress()); String com8 = pre + "add interface name=\""+interfFrom+"\" full"; System.out.println("Layer3RoutingManager "+com8); Process p8 = Runtime.getRuntime().exec(com8); p8.waitFor(); p8.destroy();
					 */

				}
				if (to != null) {
					// 2) default gateway
					// String com1 = "route add 0.0.0.0 mask 0.0.0.0 "+to.getHostAddress();
					String com1 = "route add 0.0.0.0 mask 0.0.0.0 " + to;
					System.out.println("Layer3RoutingManager " + com1);
					Process p1 = Runtime.getRuntime().exec(com1);
					p1.waitFor();
					p1.destroy();
				}

				// 3) add DNS
				// XP
				/*
				 * if(to!=null){ String interfTo = Layer3RoutingManager.fromIpToName(to.getHostAddress()); String comDNS = "netsh routing ip add dns \""+interfTo+"\" static 137.204.58.1"; System.out.println("Layer3RoutingManager "+comDNS); Process pDNS = Runtime.getRuntime().exec(comDNS);
				 * pDNS.waitFor(); pDNS.destroy(); comDNS = "netsh routing ip add dns \""+interfTo+"\" static 137.204.59.1"; System.out.println("Layer3RoutingManager "+comDNS); pDNS = Runtime.getRuntime().exec(comDNS); pDNS.waitFor(); pDNS.destroy(); }
				 */
				// Vista
				if (to != null) {
					// String interfTo = Layer3RoutingManager.fromIpToName(to.getHostAddress());
					String interfTo = Layer3RoutingManager.fromIpToName(to);
					String comDns = "netsh int ip add dnsserver \"" + interfTo + "\" 137.204.58.1";
					System.out.println("Layer3RoutingManager " + comDns);
					Process pDns = Runtime.getRuntime().exec(comDns);
					pDns.waitFor();
					pDns.destroy();
					comDns = "netsh int ip add dnsserver \"" + interfTo + "\" 137.204.59.1";
					System.out.println("Layer3RoutingManager " + comDns);
					pDns = Runtime.getRuntime().exec(comDns);
					pDns.waitFor();
					pDns.destroy();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (RampEntryPoint.os.startsWith("linux")) {
			try {
				if (from != null) {
					// 1) enable NAT
					String com1 = "iptables -t nat -A POSTROUTING -s " + from + " -j MASQUERADE";
					// System.out.println("Layer3RoutingManager "+com1);
					sudoCommand(com1);
				}
				if (to != null) {
					// 2) default gateway
					// 2a) delete previous default gateways
					Process pShow = Runtime.getRuntime().exec("ip route show");
					BufferedReader is = new BufferedReader(new InputStreamReader(pShow.getInputStream()));
					String line;
					while ((line = is.readLine()) != null) {
						if (line.contains("default")) {
							String[] delTokens = line.split(" ");
							String delCom = "ip route del default via " + delTokens[2] + " dev " + delTokens[4];
							// System.out.println("Layer3RoutingManager delCom "+delCom);
							sudoCommand(delCom);
						}
					}

					// 2b) add new default gateway
					String interf = Layer3RoutingManager.fromIpToName(to);
					String addGateway = "ip route add default via " + to + " dev " + interf;
					// System.out.println("Layer3RoutingManager addGateway "+addGateway);
					sudoCommand(addGateway);
				}

				// 3) add DNS
				FileWriter fwDns = new FileWriter("./temp/resolv.conf");
				BufferedWriter writerDns = new BufferedWriter(fwDns);
				writerDns.write("nameserver 137.204.58.1");
				writerDns.newLine();
				writerDns.write("nameserver 137.204.59.1");
				writerDns.newLine();
				writerDns.flush();
				writerDns.close();
				sudoCommand("mv ./temp/resolv.conf /etc/resolv.conf");
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			throw new Exception("Unsupported Operating System: " + System.getProperty("os.name"));
		}
	}

	private static void activatePacketForwarding() throws Exception {
		System.out.println("Layer3RoutingManager activatePacketForwarding");
		if (RampEntryPoint.os.startsWith("windows")) {
			System.out.println("Layer3RoutingManager: remember to activate the IPEnableRouter registry key");
			System.out.println("Layer3RoutingManager: windows users must be administrators");
		} else if (RampEntryPoint.os.startsWith("linux")) {
			sudoCommand("sysctl -w net.ipv4.ip_forward=1");
		} else {
			throw new Exception("Unsupported Operating System: " + System.getProperty("os.name"));
		}
	}

	private static void deactivatePacketForwarding() {// throws Exception{
		if (RampEntryPoint.os.startsWith("windows")) {
			System.out.println("Layer3RoutingManager: remember to deactivate the IPEnableRouter registry key");
		} else if (RampEntryPoint.os.startsWith("linux")) {
			try {
				sudoCommand("sysctl -w net.ipv4.ip_forward=0");
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			// throw new Exception("Unsupported Operating System: "+System.getProperty("os.name"));
		}
	}

	private static String fromIpToName(String ip) throws Exception {
		String interf = null;

		if (RampEntryPoint.os.startsWith("windows")) {
			Process p = null;
			p = Runtime.getRuntime().exec("ipconfig");
			BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String previousLine = null;
			String line;

			String ipAddress = ip.replaceAll("/", "");
			// System.out.println("Layer3RoutingManager toIp "+toIp);
			String[] tokens = ipAddress.split("[.]");
			// System.out.println("Layer3RoutingManager tokens.length "+tokens.length);
			String net = tokens[0] + "." + tokens[1] + "." + tokens[2] + ".";
			// System.out.println("Layer3RoutingManager net "+net);
			while ((interf == null) && ((line = is.readLine()) != null)) {
				if (line.contains(net)) {
					// System.out.println("Layer3RoutingManager previousLine "+previousLine);
					interf = previousLine.split(":")[0];
				}
				if (line.toLowerCase().startsWith("ethernet adapter")) {
					previousLine = line.substring("ethernet adapter".length() + 1);
				} else if (line.toLowerCase().startsWith("scheda ethernet")) {
					previousLine = line.substring("scheda ethernet".length() + 1);
				}
			}
		} else if (RampEntryPoint.os.startsWith("linux")) {
			Process p = null;
			p = Runtime.getRuntime().exec("ip addr show");
			BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String previousLine = null;
			String previousPreviousLine = null;
			String line;

			String toIp = ip.replaceAll("/", "");
			// System.out.println("Layer3RoutingManager toIp "+toIp);
			String[] tokens = toIp.split("[.]");
			// System.out.println("Layer3RoutingManager tokens.length "+tokens.length);
			String net = tokens[0] + "." + tokens[1] + "." + tokens[2] + ".";
			// System.out.println("Layer3RoutingManager net "+net);
			while ((interf == null) && ((line = is.readLine()) != null)) {
				if (line.contains(net)) {
					// System.out.println("Layer3RoutingManager previousPreviousLine "+previousPreviousLine);
					interf = previousPreviousLine.split(": ")[1];
					// System.out.println("Layer3RoutingManager interf "+interf);
				}
				previousPreviousLine = previousLine;
				previousLine = line;
			}
		} else {
			throw new Exception("Unsupported Operating System: " + System.getProperty("os.name"));
		}

		return interf;
	}

	@Override
	public void receivedTcpUnicastHeader(UnicastHeader uh) {
	}

	@Override
	public void receivedUdpBroadcastPacket(BroadcastPacket bp) {
	}

	@Override
	public void receivedTcpBroadcastPacket(BroadcastPacket bp) {
	}

	@Override
	public void receivedTcpPartialPayload(UnicastHeader uh, byte[] payload, int off, int len, boolean lastChunk) {
	}

	@Override
	public void sendingTcpUnicastPacketException(UnicastPacket up, Exception e) {
	}

	@Override
	public void sendingTcpUnicastHeaderException(UnicastHeader uh, Exception e) {
	}

	private static String sudoCommand(String command) throws Exception {
		String res = "";

		String[] commandArray = { "sh", "-c", "sudo -S " + command + " 2>&1" };
		/*
		 * System.out.print("sudoCommand.commandArray: "); for(String s : commandArray){ System.out.print(s+" "); } System.out.println();/*
		 */

		Process pRoot = Runtime.getRuntime().exec(commandArray);

		InputStream is = pRoot.getInputStream();

		int attempts = 10;
		while (attempts > 0 && is.available() == 0) {
			Thread.sleep(50);
			attempts--;
		}
		String line = "";
		while (is.available() > 0) {
			line += (char) is.read();
		}
		res += line;
		// System.out.println("sudoCommand line1: "+line);

		if (line.contains("password for")) {
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(pRoot.getOutputStream()));
			String pass = getSuperuserPassword();
			if (pass == null) {
				throw new Exception("Need a password");
			}
			bw.write(pass);
			bw.newLine();
			bw.flush();

			attempts = 10;
			while (attempts >= 0 && is.available() <= 1) {
				Thread.sleep(200);
				attempts--;
			}
			// System.out.println("sudoCommand is.available(): "+is.available());
			line = "";
			while (is.available() > 0) {
				line += (char) is.read();
			}
			res += line;
			// System.out.println("sudoCommand line: "+line);

			if (line.contains("is not in the sudoers file")) {
				throw new Exception("The user is not in the sudoers file");
			} else if (line.contains("Sorry, try again")) {
				throw new Exception("Wrong password");
			}
		}

		String[] commandK = { "sh", "-c", "sudo -S -k 2>&1" };
		/*
		 * System.out.print("sudoCommand.commandK: "); for(String s : commandK){ System.out.print(s+" "); } System.out.println();/*
		 */

		// Process pK =
		Runtime.getRuntime().exec(commandK);
		/*
		 * BufferedReader brK = new BufferedReader(new InputStreamReader(pK.getInputStream())); while( (line=brK.readLine()) != null ){ System.out.println("sudoCommand line k: "+line); }/*
		 */

		// System.out.println("end sudoCommand: "+command);

		return res;
	}

	private static String getSuperuserPassword() {
		if (System.currentTimeMillis() - lastSuperuserPasswordRefresh > TIMEOUT_SU_PASSWORD) {
			// discard previous password
			superuserPassword = null;
		}
		if (superuserPassword == null) {
			// get superuser password
			javax.swing.JPasswordField passwordField = new javax.swing.JPasswordField();
			Object[] message = { "root password?", passwordField };
			int res = javax.swing.JOptionPane.showConfirmDialog(null, message, "Granting root privileges", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
			if (res == javax.swing.JOptionPane.OK_OPTION) {
				superuserPassword = new String(passwordField.getPassword());
			}
			// TODO controlla che la password sia corretta
			/*
			 * else{ throw new Exception("Need a password"); }
			 */
			lastSuperuserPasswordRefresh = System.currentTimeMillis();
		}
		return superuserPassword;
	}

}
