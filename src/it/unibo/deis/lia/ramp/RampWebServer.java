
package it.unibo.deis.lia.ramp;

import it.unibo.deis.lia.ramp.util.GeneralUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;

import org.bouncycastle.jce.X509Principal;
import org.eclipse.jetty.http.ssl.SslContextFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlConfiguration;

public class RampWebServer extends Thread {
	
	public static final int SSL_PORT = 9443;
	
	private static String pathToWebServerDirectory = "./resources/webserver/";
	private static String cipheredCredentialsFile = pathToWebServerDirectory + "cred.ciphered";
	private static String securityCertificateFile = pathToWebServerDirectory + "keystore.jks";
	private static String jettyXmlFile = pathToWebServerDirectory + "jetty.xml";

	private static RampWebServer rampWebServer = null;
	private static Server jettyServer;
	
	private static String certificatePassword = "12345678";

	private static ContextHandlerCollection contextHandlerCollection;

	public static RampWebServer getInstance(){
		if(rampWebServer == null)
			rampWebServer = new RampWebServer();
		return rampWebServer;
	}

	private RampWebServer() {
		super();
		if(!isValidCertificate()){
			System.out.println("RampWebServer: creating a new certificate");
			createNewCertificate();
		}
		xAuthTokens = new HashMap<String, Long>();
		rampWebServer = this;
	}

	void deactivate() {
		if (rampWebServer != null) {
			try {
				System.out.println("RampWebServer.deactivate");
				if(jettyServer.isRunning()){ 
					jettyServer.setGracefulShutdown(0);
					for (String webAppContext : getContextPathList()) { // otherwise stop could be blocking
						removeWar(webAppContext);
					}
					jettyServer.stop();
				}
				rampWebServer = null;
			} catch (Exception e) {
				//e.printStackTrace();
			}
		}
	}

	public void run() {
		try {
			System.out.println("RampWebServer STARTING");

			// very verbose debug
			/*
			System.setProperty("org.eclipse.jetty.LEVEL", "DEBUG");
			org.eclipse.jetty.util.log.Logger stdErrLogger = new org.eclipse.jetty.util.log.StdErrLog(); 
			org.eclipse.jetty.util.log.Log.setLog(stdErrLogger); 
			/**/

			String keystoreFile = securityCertificateFile;
			KeyStore keystore = KeyStore.getInstance("JKS");
			keystore.load(new FileInputStream(keystoreFile), certificatePassword.toCharArray());
			
			SelectChannelConnector channel = new SelectChannelConnector();
			channel.setPort(SSL_PORT - 1);
			channel.setName("plainChannel");

			SslSelectChannelConnector sslChannel = new SslSelectChannelConnector();
			sslChannel.setPort(SSL_PORT);
			sslChannel.setName("sslChannel");
			
			SslContextFactory sslContextFactory = sslChannel.getSslContextFactory();
			sslContextFactory.addExcludeProtocols("SSLv2Hello"); //@author Jacopo De Benedetto
			sslContextFactory.addExcludeProtocols("SSLv3"); //@author Jacopo De Benedetto
			sslContextFactory.setKeyStore(keystore);
			sslContextFactory.setKeyStorePassword(certificatePassword);
			sslContextFactory.setKeyManagerPassword(certificatePassword);
			
			
			jettyServer = new Server();
			XmlConfiguration configuration = new XmlConfiguration(new FileInputStream(jettyXmlFile));
			configuration.configure(jettyServer);

			contextHandlerCollection = new ContextHandlerCollection();
			jettyServer.setHandler(contextHandlerCollection);
			jettyServer.setConnectors(new Connector[] { sslChannel, channel });
			
			System.out.println("RampWebServer READY");
			
			jettyServer.start();
			synchronized(rampWebServer){
				rampWebServer.notifyAll();
			}
			
			// Simulate first connection to speed-up initial (real) browser request
			URL url = new URL("http://localhost:" + channel.getPort());
			try { url.openConnection().setConnectTimeout(2000); } catch (SocketTimeoutException e) {}
			
			jettyServer.join();
			
			System.out.println("RampWebServer STOP");
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	// --------------------
	// Certificate management
	// --------------------

	private boolean isValidCertificate(){
		KeyStore keystore = null;
		FileInputStream fis = null;
		try {
			// Try to load the existing keystore
			keystore = KeyStore.getInstance("JKS");
			fis = new FileInputStream(securityCertificateFile);
			keystore.load(fis, certificatePassword.toCharArray());
			// Check certificate validity
			X509Certificate cert = (X509Certificate) keystore.getCertificate("ramp-unibo");
			cert.checkValidity();
		} catch (Exception e) {
			// Certificate does not exists or is not valid (probably is expired)
			//e.printStackTrace();
			return false;
		} finally {
			try { if(fis != null) fis.close(); } catch (IOException e) {}
		}
		return true;
	}

	@SuppressWarnings("deprecation")
	private void createNewCertificate(){
		try {
			// Create RSA private/public key pair with RSA 1024 bit
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(1024);
			KeyPair KPair = keyPairGenerator.generateKeyPair();

			// Instantiate an X.509 cert. generator (only from bouncy castle provider)
			org.bouncycastle.x509.X509V3CertificateGenerator v3CertGen = 
					new org.bouncycastle.x509.X509V3CertificateGenerator(); 

			// Define certificate mandatory parameters
			BigInteger serialNumber = BigInteger.valueOf(GeneralUtils.getSecureRandom().nextInt()).abs();
			v3CertGen.setSerialNumber(serialNumber);
			String cn = "CN=RAMP-" + serialNumber.toString(16); // CN=RAMP-serialNumber
			v3CertGen.setIssuerDN(new X509Principal(cn + ", OU=deis, O=unibo, L=bologna, ST=bo, C=it")); 
			v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 1));
			v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 90))); // expires in 90 days
			v3CertGen.setSubjectDN(new X509Principal(cn + ", OU=deis, O=unibo, L=bologna, ST=bo, C=it"));

			// Set the public key and signature algorithm
			v3CertGen.setPublicKey(KPair.getPublic());
			v3CertGen.setSignatureAlgorithm("SHA1withRSA");

			// Generate the certificate using the private key
			X509Certificate PKCertificate = v3CertGen.generateX509Certificate(KPair.getPrivate());

			// Create an empty jks keystore
			KeyStore keystore = KeyStore.getInstance("JKS");
			char[] password = certificatePassword.toCharArray();
			keystore.load(null, password);

			// Add private key and certificate to the keystore
			keystore.setKeyEntry("ramp-unibo", KPair.getPrivate(),
					password,
					new java.security.cert.Certificate[]{PKCertificate});

			// Save keystore to filesystem
			File certFile = new File(securityCertificateFile);
			if(certFile.exists()) certFile.delete(); // overwrite
			FileOutputStream fos = new FileOutputStream(certFile);
			keystore.store(fos, password);
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// --------------------
	// War management
	// --------------------

	public void addWar(String warFile, String contextPath) throws Exception{
		addWar(warFile, contextPath, true);
	}
	
	public void addWar(String warFile, String contextPath, boolean SSLOnly) throws Exception{
		WebAppContext webapp = new WebAppContext();
		webapp.setContextPath(contextPath);
		if(SSLOnly)
			webapp.setConnectorNames(new String[]{ "sslChannel" });
		webapp.setWar(warFile);

		contextHandlerCollection.addHandler(webapp);
		webapp.start();
	}

	public void removeWar(String contextPath){
		//get the WebAppContext to undeploy
		WebAppContext toUndeploy = getWebAppContext(contextPath);
		//stop it
		try {
			toUndeploy.stop();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		//and remove it from the handler collection
		contextHandlerCollection.removeHandler(toUndeploy);
	}

	public String[] getContextPathList(){
		String[] res;
		Handler[] handlers = contextHandlerCollection.getHandlers();
		res = new String[handlers.length];
		for( int i = 0; i < handlers.length; i++ ){
			ContextHandler ch = (ContextHandler) handlers[i];
			res[i] = ch.getContextPath();
		}
		return res;
	}

	private WebAppContext getWebAppContext(String contextPath){
		WebAppContext res = null;
		Handler[] handlers = contextHandlerCollection.getHandlers();
		for( int i = 0; res == null && i < handlers.length; i++ ){
			ContextHandler ch = (ContextHandler) handlers[i];
			if(ch.getContextPath().equals(contextPath) && ch instanceof WebAppContext){
				res = (WebAppContext)ch;
				break;
			}
		}
		return res;
	}

	public static String getCertificatePassword(){
		return certificatePassword;
	}
	
	public static String getPathToSecurityCert() {
		return securityCertificateFile;
	}
	
	public static String getPathToCipheredCredentialsFile() {
		return cipheredCredentialsFile;
	}

	/*public static String[] getAvailableWars(){
		ArrayList<String> wars = null;
		File directory = new File(pathToWebServerDirectory);
        if ( directory.exists() ) {
        	wars = new ArrayList<String>();
	        File[] files = directory.listFiles();
	        for (File file : files) {
	            if ( ! file.isDirectory() ) {
	            	String fileName = file.getName();
	            	wars.add(fileName);
	            }
	        }
        }
        return wars.toArray(new String[0]);
	}*/

	private static HashMap<String, Long> xAuthTokens; // token, creation time stamp
	private static long xAuthTokensValidPeriod = 1000 * 60L * 5L;

	public static boolean isTokenValid(String tokenToValidate){
		if(tokenToValidate == null) return false;
		if(xAuthTokens.containsKey(tokenToValidate)){
			long creationTime = xAuthTokens.get(tokenToValidate);
			long currentTime = System.currentTimeMillis();
			if(currentTime - creationTime > xAuthTokensValidPeriod){ // too old (token is valid only for xAuthTokensValidPeriod minutes from creation)
				xAuthTokens.remove(tokenToValidate);
				return false;
			}
			return true;
		}
		return false;
	}

	public static void addToken(String token){
		if(token == null) return;
		xAuthTokens.put(token, System.currentTimeMillis());
	}

}
