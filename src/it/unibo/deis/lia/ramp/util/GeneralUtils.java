package it.unibo.deis.lia.ramp.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;

public class GeneralUtils {

	private static String myPublicIpAddressString = null;
	private static int myPublicIpAddressInt;

	private static SecureRandom sr;

	public static synchronized SecureRandom getSecureRandom() {
		if (GeneralUtils.sr == null) {
			try {
				GeneralUtils.sr = SecureRandom.getInstance("SHA1PRNG");
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		return GeneralUtils.sr;
	}

	// --------------------
	// localhost method
	// --------------------

	private static String localHost = null;

	public static String getLocalHost(boolean forceRefresh) throws UnknownHostException {
		if (localHost == null || forceRefresh) {
			localHost = InetAddress.getLocalHost().getHostAddress().replaceAll("/", "");
		}
		return localHost;
	}

	public static String getLocalHost() throws UnknownHostException {
		return getLocalHost(false);
	}

	// --------------------
	// Find my ip methods
	// --------------------

	private static String[] urlIpRresolvers;
	/*
	 * = {
	 * 		"http://deis170.deis.unibo.it:8080/getPublicIpAddr",
	 * 		"http://myip.dnsdynamic.org/" };
	 */

//	static{
//		Properties properties = new Properties();
//		try {
//			properties.load(new FileInputStream("./resources/relay.props"));
//			String protocol = properties.getProperty("publicip.protocol");
//			String ip = properties.getProperty("publicip.ip");
//			String port = properties.getProperty("publicip.port");
//			urlIpRresolvers = new String[2];
//			urlIpRresolvers[0] = protocol+"://"+ip+":"+port+"/fbrelay";
//			urlIpRresolvers[1] = "http://myip.dnsdynamic.org/";
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

	public static synchronized String getMyPublicIpString(boolean forceRefresh) {
		if (myPublicIpAddressString == null || forceRefresh) {
			try {
				refreshMyPublicIpAddress();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return myPublicIpAddressString;
	}

	public static synchronized String getMyPublicIpString() {
		return getMyPublicIpString(false);
	}

	public static synchronized int getMyPublicIpInt(boolean forceRefresh) {
		if (myPublicIpAddressString == null || forceRefresh) {
			try {
				refreshMyPublicIpAddress();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return myPublicIpAddressInt;
	}

	public static synchronized int getMyPublicIpInt() {
		return getMyPublicIpInt(false);
	}

	public static synchronized void refreshMyPublicIpAddress() throws Exception {
		String myIp = null;
		int index = 0;
		do {
			try {
				System.out
						.println("Util.myPublicIp index=" + index + " urlIpRresolvers.length=" + urlIpRresolvers.length
								+ " urlIpRresolvers[" + index + "]=" + GeneralUtils.urlIpRresolvers[index]);
				URL url = new URL(GeneralUtils.urlIpRresolvers[index]);
				HttpURLConnection con = (HttpURLConnection) url.openConnection();
				con.setConnectTimeout(5000);
				InputStream inStream = con.getInputStream();
				InputStreamReader isr = new InputStreamReader(inStream);
				BufferedReader br = new BufferedReader(isr);

				myIp = br.readLine();

				myPublicIpAddressString = myIp;
				myPublicIpAddressInt = GenericPacket.s2i(myPublicIpAddressString);

				System.out.println("Util.myPublicIp myIp=" + myIp);
			} catch (Exception e) {
				System.out.println("Util.myPublicIp trying next urlIpRresolver");
			} finally {
				index++;
			}
		} while (myIp == null && index < urlIpRresolvers.length);
		if (myIp == null)
			throw new Exception();
	}

	// --------------------
	// Hashing methods
	// --------------------

	public static byte[] computeHash(String x) throws Exception {
		return computeHash(x.getBytes());
	}

	public static byte[] computeHash(int x) throws Exception {
		return computeHash(intToByteArray(x));
	}

	public static byte[] computeHash(byte[] x) throws Exception {
		java.security.MessageDigest d = null;
		d = java.security.MessageDigest.getInstance("SHA-1");
		return d.digest(x); // update(x) + reset()
	}

	// --------------------
	// Conversion methods
	// --------------------

	public static String byteArrayToHexString(byte[] b) {
		StringBuffer sb = new StringBuffer(b.length * 2);
		for (int i = 0; i < b.length; i++) {
			int v = b[i] & 0xff;
			if (v < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
		}
		return sb.toString().toUpperCase();
	}

	public static byte[] hexStringToByteArray(String s) {
		byte[] b = new byte[s.length() / 2];
		for (int i = 0; i < b.length; i++) {
			int index = i * 2;
			int v = Integer.parseInt(s.substring(index, index + 2), 16);
			b[i] = (byte) v;
		}
		return b;
	}

	public static final byte[] intToByteArray(int value) {
		return new byte[] { (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value };
	}

	public static final int byteArrayToInt(byte[] b) {
		return ((b[0] & 0xFF) << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF);
	}

	public static final byte[] unsignedShortToByteArray(int value) {
		// unsigned types does not exist in Java, therefore we use a larger type
		// value must be between 0 and 2^16 - 1 (=65535) included
		if (value < 0 || value > 65535)
			throw new IllegalArgumentException("value must be between 0 and 2^16 - 1 (=65535) included");
		// return 2 bytes representing (unsigned short) value
		return new byte[] { (byte) (value >> 8), (byte) value };
	}

	public static final int byteArrayToUnsignedShort(byte[] b) {
		// returned int is between 0 and 2^16 - 1 (=65535) included
		return (((b[0] & 0xFF) << 8) + (b[1] & 0xFF)) & 0xFFFF;
	}

	public static String byteArrayToStringAsIntArray(byte[] bytes) {
		String res = "[";
		for (int i = 0; i < bytes.length; i++) {
			res += new Integer(bytes[i]) + ((i < bytes.length - 1) ? " " : "]");
		}
		return res;
	}

	// --------------------
	// SSL methods
	// --------------------

	// credits:
	// http://javaswamy.blogspot.com/2004/12/jsse-how-to-ignore-certificateexceptio.html
	public static SSLContext createEasySSLContext() throws IOException {
		try {
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {

				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				@Override
				public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
					// no need to implement.
				}

				@Override
				public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
					// no need to implement.
				}
			} };

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, trustAllCerts, getSecureRandom());
			return context;
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	// ---------------------
	// load/store properties
	// ---------------------

	private static String propertiesPath = null;
	static {
		propertiesPath = "./resources/ramp.props";
		try {
			if (isAndroidContext()) {
				propertiesPath = android.os.Environment.getExternalStorageDirectory() + "/ramp/ramp.props";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static synchronized Properties loadProperties() {
		prepareAndroidContext();

		Properties properties = new Properties();
		try {
			FileInputStream file = new FileInputStream(propertiesPath);
			properties.load(new FileReader(propertiesPath));
			file.close();
		} catch (FileNotFoundException e) {
			System.out.println("GeneralUtils, loadProperties(): impossible to find, " + propertiesPath);
			System.out.println("Cannot load RAMP properties: initializing...");
		} catch (Exception e) {
			System.out.println("Cannot load RAMP properties: initializing...");
		}
		return properties;
	}

	public static synchronized void storeProperties(Properties properties) throws Exception {
		prepareAndroidContext();

		StringBuilder sb = new StringBuilder();
		try {
			// Read the properties file first
			FileReader fr = new FileReader(propertiesPath);
			BufferedReader br = new BufferedReader(fr);

			String line = br.readLine();
			while (line != null) {
				String toBeProcessed = "";
				// nor comment line or empty line
				if (!line.startsWith("#") && !line.startsWith("!") && line.length() > 0) {
					// Multiple line entry
					while (line != null && line.endsWith("\\")) {
						line = line.replace("\\", "");
						toBeProcessed += line;
						line = br.readLine();
					}
					toBeProcessed += line;
					if (toBeProcessed.contains(":")) {
						// Use only = to separate key/value
						toBeProcessed = toBeProcessed.replace(":", "=");
					} else if (!toBeProcessed.contains("=")) {
						toBeProcessed += "=";
					}
					if (!toBeProcessed.matches("^[^ ]+ = [^ ]*$"))
						// Normalize format: key = value
						toBeProcessed = toBeProcessed.replaceFirst("([^ ]+).*=.*([^ ]*)", "$1 = $2");
					sb.append(toBeProcessed + "\n");
				} else {
					// Keep comments and empty lines as is
					sb.append(line + "\n");
				}
				line = br.readLine();
			}
			// Now sb contains a normalized string copy of the file
			fr.close();
			br.close();
		} catch (FileNotFoundException e) {
			System.out.println("GeneralUtils, storeProperties(): impossible to find " + propertiesPath);
		}

		try {
			HashMap<String, String> propertiesToAppend = new HashMap<String, String>();
			for (String key : properties.stringPropertyNames()) {
				// foreach key
				String value = properties.getProperty(key);
				// Find start position of the key in the sb
				int valueStartIndex = sb.indexOf(key + " = ");
				if (valueStartIndex == -1) {
					// Property not found in the properties file
					System.out.println("valueStartIndex: Property not found in the properties file, valueStartIndex "
							+ valueStartIndex);
					// Append later at the end
					propertiesToAppend.put(key, value);
					continue;
				}
				// property found in the properties file
				int valueEndIndex = sb.indexOf("\n", valueStartIndex);
				valueStartIndex = sb.indexOf("=", valueStartIndex) + 2;
				// Update the actual value in the sb
				sb.replace(valueStartIndex, valueEndIndex, value);
			}
			if (propertiesToAppend.size() > 0) {
				sb.append("\n# Appended configuration \n");
				for (String key : propertiesToAppend.keySet()) {
					sb.append(key + " = " + propertiesToAppend.get(key) + "\n");
				}
			}
			// Write back to the properties file
			FileOutputStream fos = new FileOutputStream(propertiesPath, false);
			fos.write(sb.toString().getBytes());
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ---------------------
	// log
	// ---------------------

	public static synchronized void appendLog(String text) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (isAndroidContext()) {
					prepareAndroidContext();

					try {
						String logDirectory = android.os.Environment.getExternalStorageDirectory() + "/ramp/logs";
						File logFile = new File(logDirectory + "/log.txt");
						if (!logFile.exists()) {
							logFile.createNewFile();
						}

						// BufferedWriter for performance, true to set append to
						// file flag
						BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));

						Calendar date = Calendar.getInstance();
						SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						String log = format.format(date.getTime()) + ": " + text;

						// Date date = new Date(System.currentTimeMillis());
						// @SuppressWarnings("deprecation")
						// String log = date.toLocaleString() +": "+text;

						buf.append(log);
						buf.newLine();
						buf.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	public static synchronized void prepareAndroidContext() {
		if (isAndroidContext()) {
			try {
				File androidShareDirectory = new File(android.os.Environment.getExternalStorageDirectory() + "/ramp");
				if (!androidShareDirectory.exists())
					androidShareDirectory.mkdirs();

				// This prevents media scanner from reading your media
				// files and
				// providing them to other apps through the MediaStore
				// content
				// provider.
				File file = new File(androidShareDirectory.getAbsolutePath(), "/.nomedia");
				if (!file.exists()) {
					FileOutputStream out = new FileOutputStream(file);
					out.flush();
					out.close();
				}

				File dir = new File(androidShareDirectory.getAbsolutePath() + "/logs");
				if (!dir.exists())
					dir.mkdir();

				propertiesPath = androidShareDirectory.getAbsolutePath() + "/ramp.props";
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static boolean isAndroidContext() {
		if (RampEntryPoint.getAndroidContext() != null) {
			if (android.os.Environment.getExternalStorageDirectory() != null) {
				return true;
			}
		}
		return false;
	}

}
