package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Luca Iannario
 */
public class ResourceProvider {
	
	private static boolean active = false;
	
	public static String TWONKY_MUSIC_PATH;
	public static String TWONKY_PHOTO_PATH;
	public static String TWONKY_VIDEO_PATH;
	
	private Set<MonitoredWebServer> monitoredWebServers;
	
	private static ResourceProvider resourceProvider = null;
	
	public static synchronized ResourceProvider getInstance(){
		try{
            if(resourceProvider == null){
            	active = true;
            	resourceProvider = new ResourceProvider();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return resourceProvider;
	}
	private ResourceProvider(){
		System.out.println("ResourceProvider.activate");
		
		// setting default Twonky directories
		TWONKY_MUSIC_PATH = RampEntryPoint.getRampProperty("TWONKY_MUSIC_PATH");
		if( TWONKY_MUSIC_PATH == null || TWONKY_MUSIC_PATH.equals("") ){
			TWONKY_MUSIC_PATH = "/webbrowse/O0$1$13";
		}
		System.out.println("ResourceProvider: TWONKY_MUSIC_PATH = " + TWONKY_MUSIC_PATH);
		TWONKY_PHOTO_PATH = RampEntryPoint.getRampProperty("TWONKY_PHOTO_PATH");
		if( TWONKY_PHOTO_PATH == null || TWONKY_PHOTO_PATH.equals("") ){
			TWONKY_PHOTO_PATH = "/webbrowse/O0$2$22";
		}
		System.out.println("ResourceProvider: TWONKY_PHOTO_PATH = " + TWONKY_PHOTO_PATH);
		TWONKY_VIDEO_PATH = RampEntryPoint.getRampProperty("TWONKY_VIDEO_PATH");
		if( TWONKY_VIDEO_PATH == null || TWONKY_VIDEO_PATH.equals("") ){
			TWONKY_VIDEO_PATH = "/webbrowse/O0$3$35";
		}
		System.out.println("ResourceProvider: TWONKY_VIDEO_PATH = " + TWONKY_VIDEO_PATH);
		
		monitoredWebServers = new HashSet<MonitoredWebServer>();
		boolean isDemo = Boolean.parseBoolean(RampEntryPoint.getRampProperty("demoVersion"));
		if(isDemo)
			addMonitoredWebServer("localhost", 9000, TWONKY_MUSIC_PATH, TWONKY_PHOTO_PATH, TWONKY_VIDEO_PATH);
		HttpProxyServerService.getInstance().setResourceProvider(this);
	}
	
	public static boolean isActive(){
    	return active;
    }
	
	public static synchronized void deactivate(){
		if(resourceProvider != null){
			System.out.println("ResourceProvider.deactivate");
			active = false;
			if(HttpProxyServerService.isActive())
				HttpProxyServerService.getInstance().stopService();
			resourceProvider = null;
		}
    }
	
	public boolean addMonitoredWebServer(String ipAddr, int port, String... paths){
    	return monitoredWebServers.add(new MonitoredWebServer(ipAddr, port, paths));
    }
    
    public Set<MonitoredWebServer> getMonitoredWebServers(){
    	return monitoredWebServers;
    }
    
    public boolean removeMonitoredWebServer(int hashCode){
    	for (MonitoredWebServer monitoredWebServer : monitoredWebServers) {
			if(monitoredWebServer.hashCode() == hashCode){
				monitoredWebServers.remove(monitoredWebServer);
				return true;
			}
		}
    	return false;
    }
	
    public static class HttpProxyServerMonitoredWebServers implements Serializable {
    	// used both for request (monitoredWebServers is null) and response (monitoredWebServers is not null)

    	private static final long serialVersionUID = -1029781769051140193L;
    	
    	private Set<MonitoredWebServer> monitoredWebServers;
    	private int clientPort;
    	private int protocol;
    	
    	public HttpProxyServerMonitoredWebServers(int clientPort, int protocol){
    		this.clientPort = clientPort;
    		this.protocol = protocol;
    	}
    	
    	public HttpProxyServerMonitoredWebServers(Set<MonitoredWebServer> monitoredWebServers){
    		this.monitoredWebServers = monitoredWebServers;
    	}
    	
    	public Set<MonitoredWebServer> getMonitoredWebServers() {
    		return monitoredWebServers;
    	}

    	public void setMonitoredWebServers(Set<MonitoredWebServer> monitoredWebServers) {
    		this.monitoredWebServers = monitoredWebServers;
    	}

    	public int getClientPort() {
    		return clientPort;
    	}

    	public int getProtocol() {
    		return protocol;
    	}

    }
    
    public static class MonitoredWebServer implements Serializable {
		private static final long serialVersionUID = 1806542059529079693L;
		
		private String ipAddr;
    	private int port;
    	private String[] paths; // paths[0] -> MUSIC, paths[1] -> PHOTO, paths[2] -> VIDEO
    	
    	public MonitoredWebServer(String ipAddr, int port, String... paths){
    		this.ipAddr = ipAddr;
    		this.port = port;
    		this.paths = paths;
    	}
    	
    	public String getIpAddr() {
			return ipAddr;
		}

		public int getPort() {
			return port;
		}
		
		public String[] getPaths() {
			return paths;
		}

		@Override
		public int hashCode() {
			return 57 + ipAddr.hashCode() + port;
		}

		@Override
    	public boolean equals(Object obj) {
    		if (this == obj)
    			return true;
    		if (obj == null)
    			return false;
    		if (getClass() != obj.getClass())
    			return false;
    		MonitoredWebServer other = (MonitoredWebServer) obj;
    		if(this.ipAddr.equals(other.getIpAddr()) && this.port == other.port)
    			return true;
    		return false;
    	}
		
		@Override
		public String toString() {
			return "MonitoredWebServer: ipAddr=" + ipAddr + ", port=" + port;
		}
    }
    
    public static class SharedDirectory implements Serializable{
		private static final long serialVersionUID = 6292270491405515227L;
    	
		public static final int MUSIC_TYPE = 0;
		public static final int PHOTO_TYPE = 1;
		public static final int VIDEO_TYPE = 2;
		
		private String url; // absolute url to the directory (e.g. http://ip:port/path/to/directory)
		private String name;
		private int type;
		
		public SharedDirectory(String url, String name, int type){
			this.url = url;
			this.name = name;
			this.type = type;
		}

		public String getUrl() {
			return url;
		}

		public String getName() {
			return name;
		}

		public int getType() {
			return type;
		}
		
		private String getTypeName(int type){
			switch (type) {
			case MUSIC_TYPE:
				return "Music";
			case PHOTO_TYPE:
				return "Photo";
			case VIDEO_TYPE:
				return "Video";
			default:
				return "";
			}
		}
		
		@Override
		public int hashCode() {
			return 31 + url.hashCode();
		}
		
		@Override
    	public boolean equals(Object obj) {
    		if (this == obj)
    			return true;
    		if (obj == null)
    			return false;
    		if (getClass() != obj.getClass())
    			return false;
    		SharedDirectory other = (SharedDirectory) obj;
    		if(this.url.equals(other.getUrl()))
    			return true;
    		return false;
    	}
		
		@Override
		public String toString() {
			return "SharedDirectory [name=" + name + ", url=" + url + ", type=" + getTypeName(type) +"]";
		}
    }
}
