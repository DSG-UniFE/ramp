/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application.mpeg;

import java.io.*;

//import java.util.*;

/**
 *
 * @author useruser
 */
public class RTP implements 
					//java.io.Serializable, 
					Cloneable 
					, java.io.Externalizable 
					{
	
	public RTP(){} // required by Externalizable
	
    /**
	 * 
	 */
	private static final long serialVersionUID = -4489165882329288129L;
	
	private String simpleProgramName = null;
//	private short sequenceNumber = -1;
//  private int timestamp = -1;
    private byte splitterAmount;
    private byte[] rtpHeader;
    private TSPacket[] tsPackets;
    private byte[] rtpPayload;

    public RTP(byte[] rtpHeader){
        this.rtpHeader = rtpHeader;
        this.rtpPayload = new byte[0];
        this.tsPackets = new TSPacket[0];
        splitterAmount = 0;
    }
    
    public RTP(byte[] rtpHeader, byte[] rtpPayload){
        this.rtpHeader = rtpHeader;
        this.rtpPayload = rtpPayload;
        this.tsPackets = new TSPacket[0];
        splitterAmount = 0;
    }

    public short getSequenceNumber() {
    	short sequenceNumber = (short)
			(RtpMpegTsParser.fromSigned2Unsigned(rtpHeader[2])*256 
			+ RtpMpegTsParser.fromSigned2Unsigned(rtpHeader[3]));
        return sequenceNumber;
    }
    
	//public void setSequenceNumber(short sequenceNumber) {
    //    this.sequenceNumber = sequenceNumber;
    //}

    public int getTimestamp() {
    	int timestamp = 
    		( RtpMpegTsParser.fromSigned2Unsigned(rtpHeader[4]) << 24 )
			+ ( RtpMpegTsParser.fromSigned2Unsigned(rtpHeader[5]) << 16 )
			+ ( RtpMpegTsParser.fromSigned2Unsigned(rtpHeader[6]) << 8 )
			+ ( RtpMpegTsParser.fromSigned2Unsigned(rtpHeader[7]) );
		return timestamp;
	}

	//public void setTimestamp(int timestamp) {
	//	this.timestamp = timestamp;
	//}
	
    public byte[] getRtpHeader() {
        return rtpHeader;
    }
    
    public void addTsPacket(TSPacket tsPacket){
        TSPacket[] newTSPackets = new TSPacket[tsPackets.length+1];
        System.arraycopy(tsPackets, 0, newTSPackets, 0, tsPackets.length);
        newTSPackets[newTSPackets.length-1] = tsPacket;
        this.tsPackets = newTSPackets;
    }

    public void removeTsPacket(int i){
        TSPacket[] newTSPackets = new TSPacket[tsPackets.length-1];
        System.arraycopy(tsPackets, 0, newTSPackets, 0, i);
        System.arraycopy(tsPackets, i+1, newTSPackets, i, tsPackets.length-i-1);
        this.tsPackets = newTSPackets;
    }

    public TSPacket[] getTsPackets() {
        return tsPackets;
    }

    public byte[] getBytes(){
        byte[] res;
        if( rtpPayload.length != 0 ){
        	res = new byte[rtpHeader.length+rtpPayload.length];
            System.arraycopy(rtpHeader, 0, res, 0, rtpHeader.length);
            System.arraycopy(rtpPayload, 0, res, rtpHeader.length, rtpPayload.length);
        }
        else if(tsPackets.length==0){
            res = rtpHeader;
        }
        else{
            /*int packetSize = tsPackets[0].getTsPacketByte().length;
            int resLength = rtpHeader.length + packetSize*tsPackets.length;
            res = new byte[resLength];
            System.arraycopy(rtpHeader, 0, res, 0, rtpHeader.length);
            for(int i=0; i<tsPackets.length; i++){
                System.arraycopy(tsPackets[i].getTsPacketByte(), 0, res, rtpHeader.length+i*packetSize, packetSize);
            }*/
        	int resLength = rtpHeader.length;
            for(int i=0; i<tsPackets.length; i++){
                resLength += tsPackets[i].getTsPacketByte().length;
            }
            res = new byte[resLength];
            System.arraycopy(rtpHeader, 0, res, 0, rtpHeader.length);
            
            int count = 0;
            for(int i=0; i<tsPackets.length; i++){
            	int packetSize = tsPackets[i].getTsPacketByte().length;
            	//System.out.println("i "+i);
            	///System.out.println("tsPackets.length "+tsPackets.length);
            	//System.out.println("count "+count);
            	//System.out.println("packetSize "+packetSize);
            	//System.out.println("resLength "+resLength);
                System.arraycopy(tsPackets[i].getTsPacketByte(), 0, res, rtpHeader.length+count, packetSize);
                count += packetSize;
            }
        }
        return res;
    }

    public String getSimpleProgramName() {
        return simpleProgramName;
    }

    public void setSimpleProgramName(String simpleProgramName) {
        this.simpleProgramName = simpleProgramName;
    }

    public byte getSplitterAmount() {
        return splitterAmount;
    }

    public void setSplitterAmount(byte splitterAmount) {
        this.splitterAmount = splitterAmount;
    }

	@Override
    public Object clone() throws CloneNotSupportedException {
        RTP clonedRtp = (RTP)super.clone();
        clonedRtp.rtpHeader = this.rtpHeader.clone();
        //clonedRtp.sequenceNumber = this.sequenceNumber;
        //clonedRtp.timestamp = this.timestamp;
        clonedRtp.tsPackets = (TSPacket[])this.getTsPackets().clone();
        clonedRtp.simpleProgramName = new String(simpleProgramName);
        clonedRtp.splitterAmount = this.splitterAmount;
        clonedRtp.rtpPayload = this.rtpPayload.clone();
        return clonedRtp;
    }

	/**/@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		this.simpleProgramName = (String)in.readObject();
	    this.splitterAmount = in.readByte();
	    this.rtpHeader = new byte[12];
	    in.readFully(this.rtpHeader);
	    int payloadLength = in.readInt();
	    this.rtpPayload = new byte[payloadLength];
	    in.readFully(this.rtpPayload);
	    
	    short tsPacketsLength = in.readShort();
	    //System.out.println("RTP.readExternal tsPacketsLength="+tsPacketsLength);
		if(tsPacketsLength>=0){
		    this.tsPackets = new TSPacket[tsPacketsLength];
		    for(int i=0; i<tsPackets.length; i++){
		    	//tsPackets[i] = (TSPacket)in.readObject();
				boolean payloadUnitStart = in.readBoolean();
				short pid = in.readShort();
				byte frameType = in.readByte();
				byte[] tsPacketByte = new byte[188];
				in.readFully(tsPacketByte);
		    	tsPackets[i] = new TSPacket(
		    			payloadUnitStart,
		    			pid,
		                tsPacketByte
	                );
		    	tsPackets[i].setFrameType(frameType);
		    }
	    }
	    else{
	    	this.tsPackets = null;
	    }
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(simpleProgramName);
		out.writeByte(splitterAmount);
		//System.out.println("RTP.writeExternal rtpHeader.length="+rtpHeader.length);
		out.write(rtpHeader);
		out.writeInt(rtpPayload.length);
		out.write(rtpPayload);
		
		out.writeShort((short)tsPackets.length);
		//System.out.println("RTP.writeExternal (short)tsPackets.length="+(short)tsPackets.length);
		//System.out.println("RTP.writeExternal (short)tsPackets.length="+(short)tsPackets.length);
		for(int i=0; i<tsPackets.length; i++){
			//out.writeObject(tsPackets[i]);
			out.writeBoolean(tsPackets[i].isPayloadUnitStart());
		    out.writeShort(tsPackets[i].getPid());
		    out.write(tsPackets[i].getFrameType());
		    out.write(tsPackets[i].getTsPacketByte());
		}
		
		out.flush();
	}/**/
	
}
