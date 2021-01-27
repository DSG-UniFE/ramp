
package it.unibo.deis.lia.ramp.core.e2e;


/**
 *
 * @author Carlo Giannelli
 */

public abstract class GenericPacket{
	
	public static final transient byte UNUSED_FIELD = -1;
    public static final transient int MAX_UDP_PAYLOAD = 60*1024;
    public static final transient int MAX_UDP_PACKET = 65535;
    
    public abstract byte[] toProtosByteArray();
    public abstract byte getPacketId();
    public abstract void writeToProtos(java.io.OutputStream os) throws java.io.IOException;
    
    // method to transform IPv4 address from String to int
    static public int s2i(String stringIpAddress){
    	int temp;
    	int ipI = 0;
    	String[] tokens = stringIpAddress.split("[.]");
    	
    	temp = Integer.parseInt(tokens[0]); 
    	ipI += temp*256*256*256;
    	//ipI = ipI << 8;
    	
    	temp = Integer.parseInt(tokens[1]); 
    	ipI += temp*256*256;
    	//ipI = ipI << 8;
    	
    	temp = Integer.parseInt(tokens[2]); 
    	ipI += temp*256;
    	//ipI = ipI << 8;
    	
    	temp = Integer.parseInt(tokens[3]); 
    	ipI += temp;
    	
    	return ipI;
    }
    
    // method to transform IPv4 address from int to String
    static public String i2s(int intIpAddress){
    	String ipS = "";
    	ipS += ( ( intIpAddress >> 24 ) & 0xFF );
    	ipS += ".";
    	ipS += ( ( intIpAddress >> 16 ) & 0xFF );
    	ipS += ".";
    	ipS += ( ( intIpAddress >> 8 ) & 0xFF );
    	ipS += ".";
    	ipS += ( ( intIpAddress >> 0 ) & 0xFF );
    	return ipS;
    }
    
}
