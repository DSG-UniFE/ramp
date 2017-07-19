
package it.unibo.deis.lia.ramp;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.security.Key;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.Security;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import javax.crypto.Cipher;

import org.apache.log4j.xml.DOMConfigurator;

import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Handler;
import android.os.Message;
import it.unibo.deis.lia.ramp.core.internode.BufferSizeManager;
import it.unibo.deis.lia.ramp.core.internode.ContinuityManager;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.Heartbeater;
import it.unibo.deis.lia.ramp.core.internode.Layer3RoutingManager;
import it.unibo.deis.lia.ramp.core.internode.OpportunisticNetworkingManager;
import it.unibo.deis.lia.ramp.core.internode.Resolver;
//import it.unibo.deis.lia.ramp.core.social.SocialObserver;
import it.unibo.deis.lia.ramp.service.application.FileSharingService;
import it.unibo.deis.lia.ramp.service.application.ResourceDiscovery;
import it.unibo.deis.lia.ramp.service.application.ResourceProvider;
import it.unibo.deis.lia.ramp.service.management.ServiceManager;
//import it.unibo.deis.lia.ramp.service.upnp.UpnpProxyEntrypoint;
import it.unibo.deis.lia.ramp.util.GeneralUtils;
import it.unibo.deis.lia.ramp.util.LogWithTimestamp;

//import com.restfb.DefaultFacebookClient;

/**
 *
 * @author Carlo Giannelli
 */
public class RampEntryPoint {

	// RAMP main
	public static void main(String args[]) {

		// System.setProperty("java.awt.headless", "true");

		PrintStream defaultOutPrintStream = System.out;
		PrintStream defaultErrPrintStream = System.err; // pippo, pluto
		// Set logs with timestamp
		try {
			PrintStream logWithTimestamp = new LogWithTimestamp(defaultOutPrintStream, "out");
			System.setOut(logWithTimestamp);
			PrintStream logErrWithTimestamp = new LogWithTimestamp(defaultErrPrintStream, "err");
			System.setErr(logErrWithTimestamp);
		} catch (Exception e1) {
			e1.printStackTrace();
			System.setOut(defaultOutPrintStream);
			System.setErr(defaultErrPrintStream);
		}

		System.setProperty("soresm.log.dir", "./logs");
		File log4jFile = new File("./resources/log4j.xml");
		if (log4jFile.exists())
			DOMConfigurator.configure("./resources/log4j.xml");
		// Set log level for restfb API
		// Logger.getLogger(DefaultFacebookClient.class.getName()).setLevel(Level.WARNING);

		System.out.println("RAMP Version: " + releaseDate);
		System.out.println("RampEntryPoint rampProperties = " + rampProperties);

		RampEntryPoint.isHeadless = GraphicsEnvironment.isHeadless();
		System.out.println("RampEntryPoint.main: isHeadless " + isHeadless);

		final RampEntryPoint ramp = RampEntryPoint.getInstance(true, null);

		if (!isHeadless) {
			java.awt.EventQueue.invokeLater(new Runnable() {
				@Override
				public void run() {
					try {
						if (!GeneralUtils.isAndroidContext()) {
							RampEntryPoint.gui = new RampGUIJFrame(ramp);
							gui.setVisible(true);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		} else { // headless
			System.out.println("RampEntryPoint.main: registering shutdown hook");
			// Setup signal handling in order to always stop RAMP gracefully
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						if (RampEntryPoint.ramp == null)
							return; // already stopped
						System.out.println("MAIN.shutdownHook is being executed: gracefully stopping RAMP...");
						RampEntryPoint.ramp.stopRamp();
					} catch (Exception e) {
					}
				}
			}));
		}

	}

	// ---------------------------------------------------------------------

	// RAMP properties
	private static boolean logging;
	public static final String releaseDate = "1 X 2017";
	public static final String os = System.getProperty("os.name").toLowerCase();
	public static final boolean protobuf = true;

	private static Properties rampProperties;
	static {
		rampProperties = GeneralUtils.loadProperties();
		logging = Boolean.parseBoolean(getRampProperty("logging"));
	}

	public static String getRampProperty(String key) {
		String value = rampProperties.getProperty(key);
		return value == null ? null : value.trim();
	}

	public static void setRampProperty(String key, String value) {
		rampProperties.setProperty(key, value);
	}

	public static Set<Object> getRampPropertykeys() {
		return rampProperties.keySet();
	}

	public static boolean isLogging() {
		return logging;
	}

	// ---------------------------------------------------------------------

	// RAMP components
	private Dispatcher dispatcher;
	private Heartbeater heartbeater;
	private Resolver resolver;
	private ServiceManager serviceManager;
	// private static RampWebServer rampWebInterface;

	// Android
	private static android.content.Context androidContext = null;
	private static File androidShareDirectory = null;
	private static Handler androidUIHandler = null;
	private static MulticastLock wifiMulticastLock;

	// private static BundleContext osgiContext=null;

	private static RampEntryPoint ramp = null;
	private static RampGUIJFrame gui = null;
	private static boolean isHeadless;

	public static boolean isActive() {
		return RampEntryPoint.ramp != null;
	}

	synchronized static public RampEntryPoint getInstance(boolean forceStart, Object context) {

		try {
			if (forceStart && RampEntryPoint.ramp == null) {
				if (context == null) {
					// adding other security providers (BC for creating Web
					// server self-signed certificate and PBE-AES Encryption)
					Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(),
							Security.getProviders().length + 1);

					/*
					 * rampWebInterface = RampWebServer.getInstance();
					 * rampWebInterface.start(); synchronized(
					 * RampWebServer.getInstance() ){
					 * RampWebServer.getInstance().wait(); } boolean isDemo =
					 * Boolean.parseBoolean(getRampProperty("demoVersion"));
					 *
					 * if(isDemo){ rampWebInterface.addWar(
					 * "./resources/webserver/RampAccessWebInterfaceDemo.war",
					 * "/", false); rampWebInterface.addWar(
					 * "./resources/webserver/RampAccessWebInterface.war",
					 * "/advanced", false); }else{ rampWebInterface.addWar(
					 * "./resources/webserver/RampAccessWebInterface.war", "/",
					 * false); }
					 */
					// Added by Lorenzo Donini (temp!!!!)
					// TODO: REMEMBER TO PUT THIS LINE IN THE WEB MODULE THAT IS
					// RESPONSIBLE FOR ACTIVATING THE SCACM FUNCTIONALITIES
					// rampWebInterface.addWar("./resources/webserver/RampSocialFilterWebInterface.war",
					// "/filters",false);

					if (Boolean.parseBoolean(RampEntryPoint.getRampProperty("autostart"))) {
						try {
							loadExistingCredentials();
							System.out.println("RampEntryPoint.getInstance: trying to autostart as: " + username);
							if (checkUserCredential(username, passwordHash)) {
//								 starting ERN -- STEFANO LANZONE
//								 SecureJoinEntrypoint sje = SecureJoinEntrypoint.getInstance(username);
//								 SecureJoinUser user = sje.getUser(username, passwordHash, username, passwordHash);
//								 if(user == null) // first time -> add it as GOD
//									 sje.addUser(username, passwordHash, SecureJoinUser.GOD_ROLE, 0);
//
//								 starting Social Observer
//								 SocialObserver.getInstance(username, passwordHash);

								// starting resource provider
								ResourceProvider.getInstance();
								ResourceDiscovery.getInstance();

								// starting UPnP Proxy
								// UpnpProxyEntrypoint.getInstance();

								// starting File Sharing Service
								FileSharingService.getInstance();
							} else {
								System.out.println("Invalid credentials: cannot autostart");
							}
						} catch (Exception e) {
							// autostart impossible
							System.out.println("Credentials not found: cannot autostart");
						}
					}
				} else if (context instanceof android.content.Context) {
					// Android
					RampEntryPoint.androidContext = (android.content.Context) context;

					// adding other security providers (BC for creating Web
					// server self-signed certificate and PBE-AES Encryption)
					// this is needed just in case BC is not included by default
					// in android
					Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(),
							Security.getProviders().length + 1);

					RampEntryPoint.androidShareDirectory = new File(
							android.os.Environment.getExternalStorageDirectory() + "/ramp");
					if (!RampEntryPoint.androidShareDirectory.exists()) {
						RampEntryPoint.androidShareDirectory.mkdirs();
						System.out.println(
								"Android RAMP shared directory created: " + RampEntryPoint.androidShareDirectory);
					} else {
						System.out.println("Android RAMP shared directory already exists: "
								+ RampEntryPoint.androidShareDirectory);
					}

					// FIXME Reload properties for Android
					rampProperties = GeneralUtils.loadProperties();

					// start components
					// SecureJoinEntrypoint.getInstance(null); // can be null
					// because on android never use username and pass
					// SocialObserver.getInstance(null, null); // as above
				}
				/*
				 * else if (context instanceof BundleContext) { // OSGi
				 * RampEntryPoint.osgiContext = (BundleContext) context; }
				 */

//				 Provider[] providers = Security.getProviders();
//				 for (int i = 0; i < providers.length; i++) {
//					 System.out.println("providers[" + i + "] = " + providers[i] +
//							 " - " + providers[i].getInfo());
//				 }

				RampEntryPoint.ramp = new RampEntryPoint();

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return RampEntryPoint.ramp;
	}

	private final static String dbURL = "jdbc:sqlite:resources/db/login.sqlite";
	private static String username;
	private static byte[] passwordHash;

	private static void loadExistingCredentials() throws Exception {
		File file = new File(RampWebServer.getPathToCipheredCredentialsFile());
		FileInputStream fis = new FileInputStream(file);
		// read encUsername length
		byte[] encUsernameLengthBytes = new byte[4];
		fis.read(encUsernameLengthBytes);
		int encUsernameLength = GeneralUtils.byteArrayToInt(encUsernameLengthBytes);
		// read encUsername
		byte[] encUsername = new byte[encUsernameLength];
		fis.read(encUsername);
		// read encPasswordHash
		byte[] encPasswordHash = new byte[(int) (file.length() - encUsernameLength - encUsernameLengthBytes.length)];
		fis.read(encPasswordHash);

		// load keystore
		KeyStore ks = KeyStore.getInstance("JKS");
		char[] password = RampWebServer.getCertificatePassword().toCharArray();
		ks.load(new FileInputStream(RampWebServer.getPathToSecurityCert()), password);

		// decrypt using private key
		Key privateKey = ks.getKey("ramp-unibo", password);
		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);

		username = new String(cipher.doFinal(encUsername), "UTF-16");
		passwordHash = cipher.doFinal(encPasswordHash);

		System.out.println("Credentials decrypted from file with private key: " + username + " "
				+ GeneralUtils.byteArrayToHexString(passwordHash));

		fis.close();
	}

	public static void cipherCredentials(String username, byte[] passwordHash) {
		try {
			// load keystore
			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(new FileInputStream(RampWebServer.getPathToSecurityCert()),
					RampWebServer.getCertificatePassword().toCharArray());

			// encrypt using public key
			PublicKey publicKey = ks.getCertificate("ramp-unibo").getPublicKey();
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);

			byte[] encUsername = cipher.doFinal(username.getBytes("UTF-16"));
			byte[] encPasswordHash = cipher.doFinal(passwordHash);

			File file = new File(RampWebServer.getPathToCipheredCredentialsFile());
			if (file.exists()) // overwrite
				file.delete();

			// save on file
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(GeneralUtils.intToByteArray(encUsername.length));
			fos.write(encUsername);
			fos.write(encPasswordHash);
			fos.close();
			System.out.println("Credentials encrypted on file with public key: " + username + " "
					+ GeneralUtils.byteArrayToHexString(passwordHash));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Check if username and passwordHash are valid
	 *
	 * @param username
	 * @param passwordHash
	 * @return true if username and passwordHash are valid or first login, false
	 *         otherwise
	 */
	public static boolean checkUserCredential(String username, byte[] passwordHash) {
		if (username == null || passwordHash == null) {
			System.out.println("checkUserCredential:" + username == null ? " username == null"
					: "" + passwordHash == null ? " passwordHash == null" : "");
			return false;
		}
		boolean firstLogin = true;
		Connection conn = null;
		Statement stat = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(dbURL);
			try {
				stat = conn.createStatement();
				stat.executeUpdate("CREATE TABLE users (username, passhash)");
				System.out.println("SQLite: users table does not exists. Creating it");
			} catch (SQLException e) {
				// Table already exists (CREATE TABLE raises an SQLException)
				firstLogin = false;
			}
			if (firstLogin) {
				// Add credentials to the users table
				ps = conn.prepareStatement("INSERT INTO users VALUES (?, ?)");
				ps.setString(1, username); // 1-based
				ps.setBytes(2, passwordHash);
				ps.execute();
				System.out.println("Added username " + username + " to users table");
				return true;
			} else {
				// Check if user and passhash are valid
				ps = conn.prepareStatement("SELECT * FROM users WHERE username=? AND passhash=?");
				ps.setString(1, username); // 1-based
				ps.setBytes(2, passwordHash);
				rs = ps.executeQuery();
				if (rs.next()) {
					// There is a row in the result set so the user is valid
					System.out.println("Access granted to " + username);
					return true;
				}
				return false;
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (stat != null)
					stat.close();
				if (conn != null)
					conn.close();
			} catch (SQLException e) {
			}
		}
		return false;
	}

	private RampEntryPoint() throws Exception {
		// only ipv4
		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
		java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");

//		rampProperties = GeneralUtils.loadProperties("./resources/ramp.props");
//		System.out.println("RampEntryPoint rampProperties = " +
//				rampProperties);
//
//		logging = Boolean.parseBoolean(getRampProperty("logging"));

		if (RampEntryPoint.getAndroidContext() != null) {
			WifiManager wifi = (WifiManager) RampEntryPoint.getAndroidContext()
					.getSystemService(android.content.Context.WIFI_SERVICE);
			if (wifi != null) {
				wifiMulticastLock = wifi.createMulticastLock("UdpDispatcher/Heartbeater-MulticastLock");
				wifiMulticastLock.acquire();
			}
		}

		// activating Dispatcher, Heartbeater, Resolver, ServiceManager
		this.dispatcher = Dispatcher.getInstance(true);
		this.heartbeater = Heartbeater.getInstance(true);
		this.resolver = Resolver.getInstance(true);
		this.serviceManager = ServiceManager.getInstance(true);

	}

	synchronized public void stopRamp() {
		if (RampEntryPoint.ramp != null) {
			System.out.println("RampEntryPoint.stopRamp START");

			// BufferSizeManager.deactivate();
			stopBufferSizeManager();

			Layer3RoutingManager.deactivate();
			// UpnpProxyEntrypoint.deactivate();

			// ContinuityManager.deactivate();
			stopContinuityManager();

			ResourceDiscovery.deactivate();
			ResourceProvider.deactivate();

//			if(rampWebInterface!=null)
//				rampWebInterface.deactivate();
//			rampWebInterface = null;
			if (serviceManager != null)
				serviceManager.stopServiceManager();
			serviceManager = null;
			if (heartbeater != null)
				heartbeater.stopHeartbeater();
			heartbeater = null;
			if (resolver != null)
				resolver.stopResolver();
			resolver = null;
			if (dispatcher != null)
				dispatcher.stopDispatcher();
			dispatcher = null;

			if (RampEntryPoint.getAndroidContext() != null) {
				if (wifiMulticastLock != null && wifiMulticastLock.isHeld())
					wifiMulticastLock.release();
			}

			// save properties back to the properties file
			try {
				GeneralUtils.storeProperties(rampProperties);
			} catch (Exception e) {
				System.out.println("Cannot store RAMP properties: " + e.getMessage());
			}

			RampEntryPoint.ramp = null;

			System.out.println("RampEntryPoint.stopRamp END");

			if (RampEntryPoint.gui != null && RampEntryPoint.gui.isVisible()) {
				RampEntryPoint.gui.dispose();
				RampEntryPoint.gui.dispatchEvent(new WindowEvent(gui, WindowEvent.WINDOW_CLOSING));
				RampEntryPoint.gui = null;
			}
		}
	}

	public static android.content.Context getAndroidContext() {
		return RampEntryPoint.androidContext;
	}

	public static File getAndroidSharedDirectory() {
		return RampEntryPoint.androidShareDirectory;
	}

	public static void setAndroidUIHandler(Handler uiHandler) {
		androidUIHandler = uiHandler;
	}

	public static void showMessageOnAndroidUI(String message) {
		if (androidUIHandler == null)
			return;
		Message m = androidUIHandler.obtainMessage(0);
		m.obj = message;
		androidUIHandler.sendMessage(m);
	}


//	public static BundleContext getOsgiContext() {
//		return osgiContext;
//	}

	public void forceNeighborsUpdate() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Heartbeater.getInstance(false).sendHeartbeat(true);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		t.start();
	}

	public String[] getCurrentNeighbors() {
		String[] res = null;
		try {
			Vector<InetAddress> addresses = Heartbeater.getInstance(false).getNeighbors();
			res = new String[addresses.size()];
			for (int i = 0; i < addresses.size(); i++) {
				res[i] = addresses.elementAt(i).getHostAddress();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return res;
	}

	public ArrayList<String> getClients() {
		ArrayList<String> res = new ArrayList<String>();

//		if (osgiContext != null) {
//			org.osgi.framework.Bundle[] bundles = osgiContext.getBundles();
//			for (int i = 0; i < bundles.length; i++){
//				if( bundles[i].getSymbolicName().contains("it.unibo.deis.lia.ramp.osgi.service.application") &&
//						bundles[i].getSymbolicName().endsWith("Client") ){
//					System.out.println("" + bundles[i].getSymbolicName());
//					res.add(bundles[i].getSymbolicName().substring(48));
//					}
//				}
//		} else {
			try {
				Class<?>[] classes = RampEntryPoint.getClasses("it.unibo.deis.lia.ramp.service.application");

				for (int i = 0; i < classes.length; i++) {
					String name = classes[i].getSimpleName();
					if (name.endsWith("Client")) {
						res.add(name);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
//		}
		return res;
	}

	public ArrayList<String> getServices() {
		ArrayList<String> res = new ArrayList<String>();

//		if (osgiContext != null) {
//			org.osgi.framework.Bundle[] bundles = osgiContext.getBundles();
//			for (int i = 0; i < bundles.length; i++){
//				if(bundles[i].getSymbolicName().contains("it.unibo.deis.lia.ramp.osgi.service.application") &&
//						bundles[i].getSymbolicName().endsWith("Service")) {
//					System.out.println("" + bundles[i].getSymbolicName());
//					res.add(bundles[i].getSymbolicName().substring(48));
//				}
//			}
//		} else {
			try {
				Class<?>[] classes = RampEntryPoint.getClasses("it.unibo.deis.lia.ramp.service.application");
				for (int i = 0; i < classes.length; i++) {
					String name = classes[i].getSimpleName();
					if (name.endsWith("Service")) {
						res.add(name);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
//		}
		return res;
	}

	public void startClient(String className) {
//		if (osgiContext != null) {
//			try{
//				org.osgi.framework.Bundle[] bundles = osgiContext.getBundles();
//				int i=0;
//				while(!bundles[i].getSymbolicName().contains(className))
//					i++;
//				bundles[i].start();
//			} catch (Exception e) {
//				System.out.println("Impossibile avviare il bundle " + e.getMessage());
//			}
//		} else {
			try {
				Class<?> c = Class.forName("it.unibo.deis.lia.ramp.service.application." + className);
				Method m = c.getMethod("getInstance");
				m.invoke(null, new Object[] {});
			} catch (Exception e) {
				e.printStackTrace();
			}
//		}
	}

	public void startService(String className) {
		startClient(className);
	}

	public int getNodeId() {
		return Dispatcher.getLocalRampId();
	}

	public String getNodeIdString() {
		return Dispatcher.getLocalRampIdString();
	}

	private BufferSizeManager bufferSizeManager = null;

	public void startBufferSizeManager() {
		bufferSizeManager = BufferSizeManager.getInstance();
	}

	public void stopBufferSizeManager() {
		BufferSizeManager.deactivate();
		bufferSizeManager = null;
	}

	public int getBufferSize() throws Exception {
		if (bufferSizeManager == null) {
			throw new Exception("BufferSizeManager not yet active");
		}
		return bufferSizeManager.getLocalBufferSize();
	}

	public void setBufferSize(int localBufferSize) throws Exception {
		if (bufferSizeManager == null) {
			throw new Exception("BufferSizeManager not yet active");
		}
		bufferSizeManager.setLocalBufferSize(localBufferSize);
	}

	// Continuity Manager for Opportunistic Networking
	private ContinuityManager continuityManager = null;

	public void startContinuityManager() {
		continuityManager = ContinuityManager.getInstance(true);
	}

	public void stopContinuityManager() {
		ContinuityManager.deactivate();
		continuityManager = null;
	}

	public boolean isContinuityManagerActive() {
		if (continuityManager == null) {
			return false;
		} else
			return true;
	}

	// Credits:
	// http://stackoverflow.com/questions/862106/how-to-find-annotated-methods-in-a-given-package
	/**
	 * Scans all classes accessible from the context class loader which belong
	 * to the given package and subpackages.
	 *
	 * @param packageName
	 *            The base package
	 * @return The classes
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	private static Class<?>[] getClasses(String packageName) throws ClassNotFoundException, IOException {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String path = "./" + packageName.replace('.', '/');
		Enumeration<URL> resources = classLoader.getResources(path);
		List<File> dirs = new ArrayList<File>();
		while (resources.hasMoreElements()) {
			URL resource = resources.nextElement();
			String fileName = resource.getFile();
			fileName = fileName.replaceAll("%20", " ");
			dirs.add(new File(fileName));
		}
		ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
		for (File directory : dirs) {
			classes.addAll(findClasses(directory, packageName));
		}
		return classes.toArray(new Class[classes.size()]);
	}

	/**
	 * Recursive method used to find all classes in a given directory and
	 * subdirs.
	 *
	 * @param directory
	 *            The base directory
	 * @param packageName
	 *            The package name for classes found inside the base directory
	 * @return The classes
	 * @throws ClassNotFoundException
	 */
	private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
		List<Class<?>> classes = new ArrayList<Class<?>>();
		if (!directory.exists()) {
			return classes;
		}
		File[] files = directory.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				classes.addAll(findClasses(file, packageName + "." + file.getName()));
			} else if (file.getName().endsWith(".class")) {
				classes.add(
						Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
			}
		}
		return classes;
	}

//	 public void startUpnpProxyEntrypoint() {
//		 UpnpProxyEntrypoint.getInstance();
//	 }
//	 public void stopUpnpProxyEntrypoint() {
//		 UpnpProxyEntrypoint.deactivate();
//	 }

//	 public void startSecureJoinEntrypoint(String username) {
//		 try {
//			 SecureJoinEntrypoint.getInstance(username);
//		 } catch (Exception e) {
//			 e.printStackTrace();
//		 }
//	 }
//	 public void stopSecureJoinEntrypoint() {
//		 SecureJoinEntrypoint.deactivate();
//	 }

//	 public void startSocialObserver(String username, byte[] passHash) {
//		 try {
//			 SocialObserver.getInstance(username, passHash);
//		 } catch (Exception e) {
//			 e.printStackTrace();
//		 }
//	 }
//	 public void stopSocialObserver() {
//		 SocialObserver.deactivate();
//	 }

	private static Random random = GeneralUtils.getSecureRandom();

	public static int nextRandomInt() {
		return random.nextInt();
	}

	public static int nextRandomInt(int n) {
		return random.nextInt(n);
	}

	public static float nextRandomFloat() {
		return random.nextFloat();
	}

	public void sentNotifyToOpportunisticNetworkingManager() {
		OpportunisticNetworkingManager opportunisticNetworkingManager = OpportunisticNetworkingManager
				.getInstance(false);
		if (opportunisticNetworkingManager != null)
			opportunisticNetworkingManager.sentNotify();
	}

}
