
package it.unibo.deis.lia.ramp.core.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import it.unibo.deis.lia.ramp.RampEntryPoint;

/**
 *
 * @author Carlo Giannelli
 */
public class UnicastHeader extends GenericPacket {
    
	public UnicastHeader(){
		//
	}
	
	public static transient final byte PACKET_ID = (byte)1;
	
	private int id;                 										   					//Stefano Lanzone
	private int[] dest;				// 4 * #hop (bytes)
    private int[] source;			// 4 * #hop (bytes)
	private short destPort;			// 2 (bytes)
	private int destNodeId;			// 4 (bytes)
    private int sourceNodeId;		// 4 (bytes)
	private boolean ack;			// 1 (bytes)
    private short sourcePortAck;	// 2 (bytes)
    private byte currentHop;		// 1 (bytes)
    private int bufferSize;			// 4 (bytes)
    private byte retry; 			// -1 => no delay tolerant					// 1 (bytes)
    private int timeWait; 			// millis (only for delay tolerant packets)	// 4 (bytes)
    private int expiry;				// seconds (-1 => no opportunistic networking)				//Stefano Lanzone
    private short connectTimeout; 	// millis (only for TCP)					// 2 (bytes)
    
    public UnicastHeader(
            String[] destString,
            int destPort,
            int destNodeId,
            int sourceNodeId,
            boolean ack,
            int sourcePortAck,
            byte currentHop,
            int bufferSize,
            byte retry,
            int timewait,
            int expiry,
            short connectTimeout
            ) {
    	this.id = RampEntryPoint.nextRandomInt(); //Stefano Lanzone
        this.setDest(destString);
    	this.destPort = (short)(destPort + Short.MIN_VALUE);
        this.destNodeId = destNodeId;
        this.sourceNodeId = sourceNodeId;
        this.ack = ack;
        this.sourcePortAck = (short)(sourcePortAck + Short.MIN_VALUE);
        this.currentHop = currentHop;
        this.bufferSize = bufferSize;
        this.retry = retry;
        this.timeWait = timewait;
        this.expiry = expiry; //Stefano Lanzone
        this.connectTimeout = connectTimeout;
        this.source = new int[0];
    }
    
    protected UnicastHeader(
    		int[] dest,
    	    int[] source,
    		short destPort,
    		int destNodeId,
    		int sourceNodeId,
    		boolean ack,
    		short sourcePortAck,
    	    byte currentHop,
    	    int bufferSize,
    	    byte retry,
    	    int timeWait,
    	    int expiry,
    	    short connectTimeout
            ) {
    	this.id = RampEntryPoint.nextRandomInt(); //Stefano Lanzone
        this.dest = dest;
        this.source = source;
    	this.destPort = destPort;
        this.destNodeId = destNodeId;
        this.sourceNodeId = sourceNodeId;
        this.ack = ack;
        this.sourcePortAck = sourcePortAck;
        this.currentHop = currentHop;
        this.bufferSize = bufferSize;
        this.retry = retry;
        this.timeWait = timeWait;
        this.expiry = expiry; //Stefano Lanzone
        this.connectTimeout = connectTimeout;
    }
    
    //Stefano Lanzone
    //Id to detect duplicate
    public int getId() {
    	return id;
    }
    
    //Stefano Lanzone
    public void setId(int id) {
    	this.id = id;
    }
    
    public boolean isAck() {
        return ack;
    }
    protected void setAck(boolean ack) {
        this.ack = ack;
    }

    public byte getCurrentHop() {
        return this.currentHop;
    }
    public void setCurrentHop(byte currentHop) {
        this.currentHop = currentHop;
    }

    public String[] getDest() {
    	String[] resDest = new String[this.dest.length];
    	for(int i=0; i<this.dest.length; i++){
    		resDest[i] = GenericPacket.i2s(this.dest[i]);
    	}
    	return resDest;
    }
    public void setDest(String[] destString) {
    	if (destString==null ){
    		this.dest = new int[0];
    	}
    	else{
	    	this.dest = new int[destString.length];
	    	for(int i=0; i<destString.length; i++){
	    		this.dest[i] = GenericPacket.s2i(destString[i]);
	    	}
    	}
    }
    protected int[] getDestInt(){
    	return this.dest;
    }
    protected void setDestInt(int[] destInt){
    	this.dest = destInt;
    }

    public int getBufferSize() {
        return this.bufferSize;
    }
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getDestNodeId() {
        return this.destNodeId;
    }
    protected void setDestNodeId(int destNodeId) {
        this.destNodeId = destNodeId;
    }

    public int getSourceNodeId() {
        return sourceNodeId;
    }
    protected void setSourceNodeId(int sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }
    
    public int getDestPort() {
        return destPort - Short.MIN_VALUE;
    }
    protected void setDestPort(int destPort) {
        this.destPort = (short)(destPort + Short.MIN_VALUE);
    }
    protected short getDestPortShort(){
    	return destPort;
    }

    public int getSourcePortAck() {
        return this.sourcePortAck - Short.MIN_VALUE;
    }
    public void setSourcePortAck(int sourcePortAck) {
        this.sourcePortAck = (short)(sourcePortAck + Short.MIN_VALUE);
    }
    protected Short getSourcePortAckShort(){
    	return this.sourcePortAck;
    }

    public byte getRetry() {
        return this.retry;
    }
    public void setRetry(byte retry) {
        this.retry = retry;
    }
    public int getTimeWait() {
        return this.timeWait;
    }
    public void setTimeWait(int timeWait) {
        this.timeWait = timeWait;
    }
    
    //Stefano Lanzone
    public int getExpiry() {
    	return expiry;
    }
    
    //Stefano Lanzone
    public void setExpiry(int expiry) {
    	this.expiry = expiry;
    }
    
    public short getConnectTimeout() {
		return connectTimeout;
	}
    protected void setConnectTimeout(short connectTimeout) {
		this.connectTimeout = connectTimeout;
	}
    
	public String[] getSource() {
    	String[] resSource = new String[source.length];
    	for(int i=0; i<source.length; i++){
    		resSource[i] = GenericPacket.i2s(source[i]);
    	}
    	return resSource;
    }
    public void addSource(String aSource) {
    	int[] newSource = new int[source.length+1];
        System.arraycopy(this.source, 0, newSource, 0, this.source.length);
        newSource[newSource.length-1] = GenericPacket.s2i(aSource);
        this.source = newSource;
    }
    protected int[] getSourceInt(){
    	return this.source;
    }
    protected void setSourceInt(int[] source){
    	this.source = source;
    }
    
    @Override
	public String toString() {
		return "UnicastHeader [id=" + id + ", dest=" + Arrays.toString(dest) + ", source=" + Arrays.toString(source) + ", destPort=" + destPort + ", destNodeId=" + destNodeId + ", sourceNodeId=" + sourceNodeId
				+ ", ack=" + ack + ", sourcePortAck=" + sourcePortAck + ", currentHop=" + currentHop + ", bufferSize=" + bufferSize + ", retry=" + retry + ", timeWait=" + timeWait + ", expiry=" + expiry
				+ ", connectTimeout=" + connectTimeout + "]";
	}
    
    // following methods useful for Externalizable
    /*private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    	out.writeByte(dest.length);
    	for(int i=0; i<dest.length; i++)
    		out.writeInt(dest[i]);
    	
    	out.writeByte(source.length);
    	for(int i=0; i<source.length; i++)
    		out.writeInt(source[i]);
    	
    	out.writeShort(destPort);
    	out.writeInt(destNodeId);
    	out.writeInt(sourceNodeId);
    	out.writeBoolean(ack);
    	out.writeShort(sourcePortAck);
    	out.writeByte(currentHop);
    	out.writeInt(bufferSize);
    	out.writeByte(retry);
    	out.writeInt(timeWait);
    	out.writeShort(connectTimeout);
    	out.flush();
    }
	private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
		byte destLength = in.readByte();
		this.dest = new int[destLength];
    	for(int i=0; i<dest.length; i++)
    		this.dest[i] = in.readInt();
    	
    	byte sourceLength = in.readByte();
		this.source = new int[sourceLength];
    	for(int i=0; i<source.length; i++)
    		this.source[i] = in.readInt();
    	
    	this.destPort = in.readShort();
    	this.destNodeId = in.readInt();
    	this.sourceNodeId = in.readInt();
    	this.ack = in.readBoolean();
    	this.sourcePortAck = in.readShort();
    	this.currentHop = in.readByte();
    	this.bufferSize = in.readInt();
    	this.retry = in.readByte();
    	this.timeWait = in.readInt();
    	this.connectTimeout = in.readShort();
	}*/
    
    /*
	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		byte destLength = in.readByte();
		this.dest = new int[destLength];
    	for(int i=0; i<dest.length; i++)
    		this.dest[i] = in.readInt();
    	
    	byte sourceLength = in.readByte();
		this.source = new int[sourceLength];
    	for(int i=0; i<source.length; i++)
    		this.source[i] = in.readInt();
    	
    	this.destPort = in.readShort();
    	this.destNodeId = in.readInt();
    	this.sourceNodeId = in.readInt();
    	this.ack = in.readBoolean();
    	this.sourcePortAck = in.readShort();
    	this.currentHop = in.readByte();
    	this.bufferSize = in.readInt();
    	this.retry = in.readByte();
    	this.timeWait = in.readInt();
    	this.connectTimeout = in.readShort();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeByte(dest.length);
    	for(int i=0; i<dest.length; i++)
    		out.writeInt(dest[i]);
    	
    	out.writeByte(source.length);
    	for(int i=0; i<source.length; i++)
    		out.writeInt(source[i]);
    	
    	out.writeShort(destPort);
    	out.writeInt(destNodeId);
    	out.writeInt(sourceNodeId);
    	out.writeBoolean(ack);
    	out.writeShort(sourcePortAck);
    	out.writeByte(currentHop);
    	out.writeInt(bufferSize);
    	out.writeByte(retry);
    	out.writeInt(timeWait);
    	out.writeShort(connectTimeout);
	}
	*/
    
	// following methods useful for ProtoBuf
    public void writeToProtos(java.io.OutputStream os) throws java.io.IOException {
    	createProtosUnicastHeader().writeDelimitedTo(os);
    }
	public byte[] toProtosByteArray(){
		return createProtosUnicastHeader().toByteArray();
	}
	protected RampPacketsProtos.UnicastHeader createProtosUnicastHeader(){
		RampPacketsProtos.UnicastHeader.Builder uhProtobufBuilder = RampPacketsProtos.UnicastHeader.newBuilder();

		uhProtobufBuilder.setId(id); //1 Stefano Lanzone 
		
    	for(int i=0; i<dest.length; i++)
    		uhProtobufBuilder.addDest(dest[i]); //2
    	
    	for(int i=0; i<source.length; i++)
    		uhProtobufBuilder.addSource(source[i]); //3
    	
    	uhProtobufBuilder.setDestPort(destPort); //4
    	uhProtobufBuilder.setDestNodeId(destNodeId); //5
    	uhProtobufBuilder.setSourceNodeId(sourceNodeId); //6
    	uhProtobufBuilder.setAck(ack); //7
    	uhProtobufBuilder.setSourcePortAck(sourcePortAck); //8
    	uhProtobufBuilder.setCurrentHop(currentHop); //9
    	uhProtobufBuilder.setBufferSize(bufferSize); //10
    	uhProtobufBuilder.setRetry(retry); //11
    	uhProtobufBuilder.setTimeWait(timeWait); //12
    	uhProtobufBuilder.setExpiry(expiry); //13 Stefano Lanzone 
    	uhProtobufBuilder.setConnectTimeout(connectTimeout); //14
    	
		RampPacketsProtos.UnicastHeader uhProtobuf = uhProtobufBuilder.build();
		return uhProtobuf;
	}
	
	public static UnicastHeader parseFromProtos(InputStream is) throws IOException{
		RampPacketsProtos.UnicastHeader uhProtobuf = RampPacketsProtos.UnicastHeader.parseDelimitedFrom(is);
		return createUnicastHeader(uhProtobuf);
	}
	public static UnicastHeader parseFromProtos(byte[] bytes, int offset, int length) throws IOException{
		RampPacketsProtos.UnicastHeader uhProtobuf = RampPacketsProtos.UnicastHeader.newBuilder().mergeFrom(bytes,offset,length).build();
		return createUnicastHeader(uhProtobuf);
	}
	protected static UnicastHeader createUnicastHeader(RampPacketsProtos.UnicastHeader uhProtobuf){
		UnicastHeader uh = new UnicastHeader();
		
		uh.id = uhProtobuf.getId(); //Stefano Lanzone 
				
		uh.dest = new int[uhProtobuf.getDestCount()];
		for(int i=0; i<uhProtobuf.getDestCount(); i++)
			uh.dest[i] = uhProtobuf.getDest(i);

		uh.source = new int[uhProtobuf.getSourceCount()];
		for(int i=0; i<uhProtobuf.getSourceCount(); i++)
			uh.source[i] = uhProtobuf.getSource(i);
    	
    	uh.destPort = (short)uhProtobuf.getDestPort();
    	uh.destNodeId = uhProtobuf.getDestNodeId();
    	uh.sourceNodeId = uhProtobuf.getSourceNodeId();
    	uh.ack = uhProtobuf.getAck();
    	uh.sourcePortAck = (short)uhProtobuf.getSourcePortAck();
    	uh.currentHop = (byte)uhProtobuf.getCurrentHop();
    	uh.bufferSize = uhProtobuf.getBufferSize();
    	uh.retry = (byte)uhProtobuf.getRetry();
    	uh.timeWait = uhProtobuf.getTimeWait();
    	uh.expiry = uhProtobuf.getExpiry(); //Stefano Lanzone 
    	uh.connectTimeout = (short)uhProtobuf.getConnectTimeout();
    	
		return uh;
	}
	
	@Override
	public byte getPacketId() {
		return PACKET_ID;
	}
	
}
