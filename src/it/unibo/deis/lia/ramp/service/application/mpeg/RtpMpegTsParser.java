/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.unibo.deis.lia.ramp.service.application.mpeg;

import it.unibo.deis.lia.ramp.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author useruser
 */
public class RtpMpegTsParser extends AbstractRtpParser{

    private ArrayBlockingQueue<RTP> queue;
    private boolean active = true;

    private int startingTimeout;    // seconds
    private int workingTimeout;     // seconds
    private int queueTimeout;       // seconds

    private InputStream is;
    public RtpMpegTsParser(InputStream is)  throws Exception{
        this.is = is;
        this.queue = new ArrayBlockingQueue<RTP>(100);
        this.startingTimeout = 15;
        this.workingTimeout = 3;
        queueTimeout = startingTimeout;
    }
    public RtpMpegTsParser(InputStream is, int startingTimeout, int workingTimeout)  throws Exception{
        this.is = is;
        this.queue = new ArrayBlockingQueue<RTP>(100);
        this.startingTimeout = startingTimeout;
        this.workingTimeout = workingTimeout;
        queueTimeout = startingTimeout;
    }
	
	@Override
    public RTP getRtp() throws Exception{
        RTP res = queue.poll(queueTimeout, TimeUnit.SECONDS);
        queueTimeout = this.workingTimeout;
        //queueTimeout = 2;
        /*if(res==null){
            throw new EndOfStreamException();
        }*/
        return res;
    }
    
    @Override
    public void run(){

        Vector<Long> times = null;
        int rtpTsPackets = -1;
        if( RampEntryPoint.isLogging() ){
            times = new Vector<Long>();
        }
        
        try{
            byte[] rtpHeader = null;
            while(rtpHeader==null){
                rtpHeader = getRtpHeader();
            }
            
            while(active){
                //System.out.println("\nRtpMpegTsParser NEW RTP");

                // create an RTP object with an already gathered RTP header
                RTP rtp = new RTP(rtpHeader);
                // parse the RTP object to update the sequence number
                parseRtpHeader(rtp);
                // check if there is am RTP header or not
                rtpHeader = getRtpHeader();
                
                /**/long startParsing = -1;
                if( RampEntryPoint.isLogging() ){
                    startParsing = System.nanoTime();
                }
                long endParsing = -1;/**/

                while(active && rtpHeader==null){
                    //System.out.println("RtpMpegTsParser NEW TS");
                    byte[] tsPacketByte = getTsPacket();
                    TSPacket tsPacket = null;
                    if(isParsePayload()){
	                    tsPacket = parseTsPacketHeader(tsPacketByte);
	                    if(tsPacket.isPayloadUnitStart()){
	                        //System.out.println("RtpMpegTsParser PayloadUnitStart");
                                try{
                                    parseTsPacketPayload(tsPacket);
                                }
                                catch(Exception e){
                                    e.printStackTrace();
                                }
	                    }
                    }
                    else{
                    	tsPacket = new TSPacket(tsPacketByte);
                    }
                    //System.out.println("RtpMpegTsParser pid "+tsPacket.getPid());
                    //System.out.println("RtpMpegTsParser frameType "+tsPacket.getFrameType());
                    //System.out.println();
                    if(tsPacket.getPid() == 0x1FFF){
                        // null packet, ignore it
                    }
                    else{
                        rtp.addTsPacket(tsPacket);
                    }

                    /**/if( RampEntryPoint.isLogging() ){
                    //if(RampProperties.logging && rtpHeader==null){
                        endParsing = System.nanoTime();
                    }/**/

                    rtpHeader = getRtpHeader();
                }

                if( RampEntryPoint.isLogging() && startParsing!=-1 && endParsing!=-1 ){
                    long elapsedParsing = endParsing - startParsing;
                    times.addElement(elapsedParsing);
                    if(rtpTsPackets < rtp.getTsPackets().length){
                        rtpTsPackets = rtp.getTsPackets().length;
                    }
                    //System.out.println("RtpMpegTsParser elapsedParsing (ns): "+elapsedParsing+"   TS packets (#): "+rtp.getTsPackets().length);
                }

                if(queue.remainingCapacity()==0){
                    queue.remove();
                    System.out.println("RtpMpegTsParser dropping old RTP packet due to full queue");
                }
                queue.add(rtp);
            }// end while
        }
        catch(EndOfStreamException eofe){
            //eofe.printStackTrace();
            //System.out.println("RtpMpegTsParser EndOfStreamException");
        }
        catch(Exception e){
            e.printStackTrace();
        }

        if( RampEntryPoint.isLogging() ){
            if(times != null){
                if(times.size()>0) times.removeElementAt(0);
                if(times.size()>0) times.removeElementAt(0);
                if(times.size()>0) times.removeElementAt(0);
            }

            Collections.sort(times);
            Long[] elapsedArray = times.toArray(new Long[0]);

            try{
                FileWriter fw = new FileWriter("./temp/parsing.csv");
                fw.write("Parsing time (ms)\n");
                for(int i=0; i<elapsedArray.length; i++){
                    fw.write(""+elapsedArray[i]/(1000.0F)/(1000.0F)+"\n");
                }
                fw.close();
            }
            catch(Exception e){
                e.printStackTrace();
            }

            int maxValue = Math.round(times.size()*0.95F);
            float countMean = 0;
            for(int i=0; i<maxValue; i++){
                countMean += elapsedArray[i];
                //System.out.println("RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            }
            float mean = countMean/((float)maxValue);

            //for(int i=maxValue; i<elapsedArray.length; i++){
            //    System.out.println("DISCARDING RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            //}
            
            float countStddev = 0;
            for(int i=0; i<maxValue; i++){
                float x = elapsedArray[i] - mean;
                countStddev += ( x * x );
            }
            float y = countStddev/((float)maxValue);
            float stddev = (float)Math.sqrt(y);
            System.out.println(
                    "95% RtpMpegTsParser mean (ms): "+mean/(1000.0F)/(1000.0F)+"   " +
                    "stddev (ms): "+stddev/(1000.0F)/(1000.0F)+"   " +
                    "elapsedArray.length "+maxValue+"   " +
                    "rtpTsPackets "+rtpTsPackets);


            maxValue = Math.round(times.size()*0.90F);
            //elapsedArray = times.toArray(new Long[0]);
            countMean = 0;
            for(int i=0; i<maxValue; i++){
                countMean += elapsedArray[i];
                //System.out.println("RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            }
            mean = countMean/((float)maxValue);

            //for(int i=maxValue; i<elapsedArray.length; i++){
            //    System.out.println("DISCARDING RtpMpegTsParser times["+i+"] (ms): "+elapsedArray[i]/(1000.0F)/(1000.0F));
            //}

            countStddev = 0;
            for(int i=0; i<maxValue; i++){
                float x = elapsedArray[i] - mean;
                countStddev += ( x * x );
            }
            y = countStddev/((float)maxValue);
            stddev = (float)Math.sqrt(y);
            System.out.println(
                    "90% RtpMpegTsParser mean (ms): "+mean/(1000.0F)/(1000.0F)+"   " +
                    "stddev (ms): "+stddev/(1000.0F)/(1000.0F)+"   " +
                    "elapsedArray.length "+maxValue+"   " +
                    "rtpTsPackets "+rtpTsPackets);
        }

        System.out.println("RtpMpegTsParser FINISHED");
    }

    protected static int fromSigned2Unsigned(int i){
        return ((i+256)%256);
    }

    private int previousByte=-1;
    private byte getByte() throws Exception{
        int res;
        if(previousByte!=-1){
            //System.out.println("MpegTsParser.getByte previousByte "+previousByte);
            res = previousByte;
            previousByte = -1;
        }
        else{
            //System.out.println("MpegTsParser.getByte is.read");
            res = is.read();
            if(res==-1){
                throw new EndOfStreamException();
            }
        }
        //System.out.println("MpegTsParser.getByte res "+res);
        return (byte)res;
    }
    private void putByte(int b) throws Exception{
        if(previousByte!=-1){
            throw new Exception("previousByte full");
        }
        previousByte = b;
    }
    private int getBytes(byte[] buffer, int offset, int length) throws Exception{
        int res;
        if(previousByte!=-1){
            buffer[offset+0]=(byte)previousByte;
            previousByte=-1;
            res=1+is.read(buffer, offset+1, length-1);
        }
        else{
            res=is.read(buffer, offset, length);
        }
        return res;
    }

    private byte[] getRtpHeader() throws Exception{
        int firstByte = getByte();
        byte[] rtpHeader = null;
        //System.out.println("MpegTsParser.getRtpHeader firstByte "+firstByte);
        if(firstByte == (int)0x47){
            putByte(firstByte);
        }
        else{
            int cc = firstByte & 15;
            //System.out.println("RtpMpegTsParser.getRtpHeader cc "+cc+" ("+(total-preRtp)+")");
            rtpHeader = new byte[12+4*cc];
            rtpHeader[0] = (byte)firstByte;
            for(int i=1; i<rtpHeader.length; i++){
                rtpHeader[i] = getByte();
            }
        }
        return rtpHeader;
    }

    private void parseRtpHeader(RTP rtp){
        /*byte[] rtpHeader = rtp.getRtpHeader();
        short sequenceNumber = (short)(fromSigned2Unsigned(rtpHeader[2])*256 
        						+ fromSigned2Unsigned(rtpHeader[3]));
        int timestamp = ( fromSigned2Unsigned(rtpHeader[4]) << 24 )
							+ ( fromSigned2Unsigned(rtpHeader[5]) << 16 )
							+ ( fromSigned2Unsigned(rtpHeader[6]) << 8 )
							+ ( fromSigned2Unsigned(rtpHeader[7]) );
        rtp.setSequenceNumber(sequenceNumber);
        rtp.setTimestamp(timestamp);*/
    }

    int fixedTsPacketSize = -1;
    private byte[] getTsPacket() throws Exception{
        byte[] res;

        if(fixedTsPacketSize==-1){
            res = new byte[204];
        }
        else{
            res = new byte[fixedTsPacketSize];
        }

        int count=0;
        byte b = getByte();
        while(b != (byte)0x47){
            // skip spare bytes
            //System.out.println("skip "+Integer.toString((int)((b+256)%256), 16));
            System.out.println("skip "+Integer.toString(b, 16));
            b = getByte();
        }

        res[count]=b;
        count++;

        //System.out.println("count "+count+"   b "+Integer.toString(b, 16));
        if(fixedTsPacketSize==-1){
            // TS packet size still unknown
            b = getByte();
            // looking for next TS packet first byte
            while( //(fixedTsPacketSize!=-1 && count<fixedTsPacketSize) &&
                    count<res.length &&
                    (b!=(byte)0x47 || (count!=188 && count!=192 && count!=204)) ){
                //System.out.println("count "+count+"   b "+Integer.toString(b, 16));
                res[count]=b;
                count++;
                b = getByte();
            }
            putByte(b);
            if(count!=204){
                byte[] temp = new byte[count];
                System.arraycopy(res,0,temp,0,count);
                res=temp;
            }
            fixedTsPacketSize=count;
        }
        else{
            // known TS packet size
            int read = getBytes(res, count, fixedTsPacketSize-count);
            if(read!=fixedTsPacketSize-count){
                System.out.println("RtpMpegTsParse.getBytes read="+read+" while (fixedTsPacketSize-count)="+(fixedTsPacketSize-count));
                throw new Exception("read="+read+" while (fixedTsPacketSize-count)="+(fixedTsPacketSize-count));
            }
        }
        return res;
    }
    
    private TSPacket parseTsPacketHeader(byte[] tsPacket){
        TSPacket res = null;
        int fourth = tsPacket[3];
        boolean adaptationFieldExist = (fourth&32)==32;
        //boolean payloadBit  = (fourth&16)==16;
        boolean payloadUnitStart = (tsPacket[1]&64)==64;
        //System.out.println("RtpMpegTsParser.parseTsPacketHeader: payloadUnitStart = "+payloadUnitStart);
        short pid = (short)((tsPacket[1]&(31))*256 + tsPacket[2]);
        int continuityCounter = fromSigned2Unsigned(tsPacket[3])&15;

        int adaptationFieldLength=0;
        if(adaptationFieldExist){
            adaptationFieldLength = fromSigned2Unsigned(tsPacket[4]) +1;
        }
        int payloadStart = 4+adaptationFieldLength;
        //System.out.println("adaptationFieldLength "+adaptationFieldLength);

        res = new TSPacket(
                payloadUnitStart,
                pid,
                continuityCounter,
                tsPacket,
                payloadStart,
                adaptationFieldExist,
                adaptationFieldLength
            );

        return res;
    }

    // following PIDs are associated with PMTs (pid ==> program number)
    Hashtable<Integer,Integer> pid2ProgNumber = new Hashtable<Integer,Integer>();
    //HashSet<Integer> pidOfPmt = new HashSet<Integer>();
    private void  parseTsPacketPayload(TSPacket tsPacket) throws Exception{
    	//System.out.println("\nRtpMpegTsParser parseTsPacketPayload PID="+tsPacket.getPid());
        if(!tsPacket.isPayloadUnitStart()){
        	System.out.println("RtpMpegTsParser: PayloadUnitStart must be active");
            throw new Exception("PayloadUnitStart must be active");
        }
        else{
            //String frameType = null;
        	byte frameType = TSPacket.UNDEFINED;
            int payloadStart = tsPacket.getPayloadStart();
            byte[] tsPacketByte = tsPacket.getTsPacketByte();
            
            //System.out.println("payloadStart "+payloadStart);
            /* reserved PID
            0x0000 : PAT
            0x0001 : CAT
            0x0002 : TSDT
            0x1FFF (8191): NULL PACKET
             */
            int pid = tsPacket.getPid();
            //System.out.println("pid "+pid);
            if( 
                    pid!= 0 // 0x00 PSI-PAT
                    && pid!= 1 // 0x01 PSI-CAT
                    && pid!= 2 // 0x02 PSI-TSDT
                    //&& progNumb2Stream.containsValue(pid) // PSI-PMT
                    //&& !pidOfPmt.contains(pid)
                    && !pid2ProgNumber.containsKey(pid)
                    && fromSigned2Unsigned(tsPacketByte[payloadStart+0])==0
                    && fromSigned2Unsigned(tsPacketByte[payloadStart+1])==0
                    && fromSigned2Unsigned(tsPacketByte[payloadStart+2])==1
                    ){

                //System.out.println("PES");
                int streamId = fromSigned2Unsigned(tsPacketByte[payloadStart+3]);
                //System.out.println("streamId "+streamId);
                if(streamId>=192 && streamId<223){
                    //System.out.println("audio stream");
                    frameType = TSPacket.AUDIO; //"Audio";
                }
                else if(streamId>=224 && streamId<239){
                    //System.out.println("\nvideo stream");
                }
                /*
                boolean extensionPresent = false;
                if(streamId==189 || streamId>=192){
                    extensionPresent=true; 
                }
                int pesPacketLength = fromSigned2Unsigned(tsPacketByte[payloadStart+4])*256
                        + fromSigned2Unsigned(tsPacketByte[payloadStart+5]);
				*/
                
                /*
                boolean pts_dts_left = (tsPacket[payloadStart+7] & 128)==128;
                boolean pts_dts_right = (tsPacket[payloadStart+7] & 64)==64;
                boolean escr = (tsPacket[payloadStart+7] & 32)==32;
                boolean es = (tsPacket[payloadStart+7] & 16)==16;
                boolean copy = (tsPacket[payloadStart+7] & 4)==4;
                boolean crc = (tsPacket[payloadStart+7] & 2)==2;
                boolean extension = (tsPacket[payloadStart+7] & 1)==1;
                */
                
                int headerDataLength = fromSigned2Unsigned(tsPacketByte[payloadStart+8]);

                //int mpegPayloadStart=9+headerDataLength;
                int mpegPayloadStart=payloadStart+headerDataLength;
                
                // skipping 0xFF (padding/filler data)
                while( fromSigned2Unsigned(tsPacketByte[mpegPayloadStart]) == 0xFF ){
                    mpegPayloadStart++;
                    //System.out.println("mpegPayloadStart "+mpegPayloadStart);
                }

                while( ( frameType==TSPacket.UNDEFINED || frameType==TSPacket.GENERIC_SLICE )
                		&& mpegPayloadStart<tsPacketByte.length-payloadStart-2 ){
                    //System.out.println("PES header");

                    // looking for 0x00 0x00 0x01
                	while( mpegPayloadStart<tsPacketByte.length-payloadStart-2 &&
                    		! (
                                fromSigned2Unsigned(tsPacketByte[mpegPayloadStart+0])==0 &&
                                fromSigned2Unsigned(tsPacketByte[mpegPayloadStart+1])==0 &&
                                fromSigned2Unsigned(tsPacketByte[mpegPayloadStart+2])==1
                              )
                          ){
                        mpegPayloadStart++;
                    }
                    if(mpegPayloadStart<tsPacketByte.length-payloadStart-2){
                        //System.out.println(mpegPayloadStart+" "+(tsPacketByte[mpegPayloadStart+0]+256)%256);
                        //System.out.println(mpegPayloadStart+" "+(tsPacketByte[mpegPayloadStart+1]+256)%256);
                        //System.out.println(mpegPayloadStart+" "+(tsPacketByte[mpegPayloadStart+2]+256)%256);
                        if( frameType==TSPacket.UNDEFINED ){
                    		frameType = parseMpeg2(mpegPayloadStart, tsPacketByte);
                        }
                    	//if(frameType==null || frameType.equals("slice")){ // XXX
                    	if(/*frameType==TSPacket.UNDEFINED ||*/ frameType==TSPacket.GENERIC_SLICE){ // XXX
                    		byte sliceType = parseH264(mpegPayloadStart, tsPacketByte);
                    		//tsPacket.sliceAt(sliceType, (short)mpegPayloadStart);
                    		if( sliceType == TSPacket.IDR_FRAME ){
                    			// Instantaneous Decoder Refresh (IDR)
                    			frameType = TSPacket.IDR_FRAME;
                    		}
                    		else if( sliceType == TSPacket.P_SLICE 
                    				|| sliceType == TSPacket.B_SLICE ){
                    			frameType = TSPacket.NON_IDR_FRAME;
                    		}
                    		//System.out.println("sliceType="+sliceType+" frameType="+frameType);
                    	}
                        mpegPayloadStart++;
                        //System.out.println("mpegPayloadStart="+mpegPayloadStart+" tsPacketByte.length="+tsPacketByte.length);
                    }
                }
                //res = new PES(streamId, extensionPresent, pesPacketLength, startCode, frameTypeInt, frameType, headerDataLength+9);
            }// end PES
            else{
                //int pointerField = fromSigned2Unsigned(tsPacketByte[payloadStart+0]); // first byte
                //System.out.println("PSI (pointerField="+pointerField+")");
                int tableId = fromSigned2Unsigned(tsPacketByte[payloadStart+1]); // second byte
                //System.out.println("PSI (tableId="+tableId+")");
                /* Table ID
                    0x00 : PAT
                    0x01 : CAT
                    0x02 : PMT
                    0x40 : NIT
                    0x41 : NIT other
                    0x42 : SDT
                    0x43 : SDT other
                    0x4E : EIT
                    0x70 : TDT
                 */
                if(pid == 0x00 && tableId == 0x00){
                    //frameType = "PAT";
                    frameType = TSPacket.PAT;
                    //System.out.println("PAT (Program Association Table)");
                    // TS Packets containing PAT information always have PID 0x0000.
                    int thirdByte = fromSigned2Unsigned(tsPacketByte[payloadStart+2]);
                    //System.out.println("thirdByte "+thirdByte);
                    int fourthByte = fromSigned2Unsigned(tsPacketByte[payloadStart+3]);
                    //System.out.println("fourthByte "+fourthByte);
                    int sectionLength = (thirdByte & 3)*256 + fourthByte;
                    //System.out.println("sectionLength "+sectionLength);
                    int numberOfPrograms = (sectionLength-4-5)/4;

                    int programsStart = 9;
                    //System.out.println("numberOfPrograms "+numberOfPrograms);
                    for(int i=0; i<numberOfPrograms; i++){
                        int programNum = fromSigned2Unsigned(tsPacketByte[payloadStart+programsStart+(4*i)+0]) * 256 +
                                fromSigned2Unsigned(tsPacketByte[payloadStart+programsStart+(4*i)+1]);
                        //System.out.println("programNum "+programNum);
                        int programPid = fromSigned2Unsigned(tsPacketByte[payloadStart+programsStart+(4*i)+2]&(1+2+4+8+16)) * 256 +
                                fromSigned2Unsigned(tsPacketByte[payloadStart+programsStart+(4*i)+3]);
                        /*if(programNum==0){
                            pid_nit=programPid;
                            System.out.println("pid_nit "+pid_nit);
                        }
                        else if(programNum==1){*/
                            //progNumb2Stream.put(programNum, programPid);
                            //pidOfPmt.add(programPid);
                            pid2ProgNumber.put(programPid, programNum);
                            //System.out.println("programPid "+programPid);
                        //
                    }
                }
                else if(/*pid2ProgNumber.containsKey(pid) &&*/ tableId == 0x02){
                    //frameType = "PMT";
                    frameType = TSPacket.PMT;
                    //System.out.println("PMT (Program Map Table)");
                    // The PID value is 0x0014. (17)
                    int thirdByte = fromSigned2Unsigned(tsPacketByte[payloadStart+2]);
                    int fourthByte = fromSigned2Unsigned(tsPacketByte[payloadStart+3]);
                    int sectionLength = (thirdByte & 3)*256 + fourthByte;
                    //System.out.println("sectionLength "+sectionLength);

                    //int programNum = fromSigned2Unsigned(tsPacketByte[payloadStart+4]) * 256 +
                    //        fromSigned2Unsigned(tsPacketByte[payloadStart+5]);
                    //System.out.println("programNum "+programNum);
                    //System.out.println("progNumb2Stream.get(programNum) "+progNumb2Stream.get(programNum));
                    //System.out.println("pid2ProgNumber.get(pid) "+pid2ProgNumber.get(pid));

                    int twelfthByte = fromSigned2Unsigned(tsPacketByte[payloadStart+11]);
                    int thirteenthByte = fromSigned2Unsigned(tsPacketByte[payloadStart+12]);
                    int programInfoLength = (twelfthByte & 3)*256 + thirteenthByte;
                    //System.out.println("programInfoLength "+programInfoLength);

                    //int fourteenthByte = (tsPacketByte[payloadStart+13]+256)%256;

                    int startDescriptors = 13+programInfoLength;
                    //for(int i=0; i<sectionLength; i++){
                    int count = startDescriptors; // crc length is 4 bytes
                    while(count<sectionLength){
                        //System.out.println("section "+i);
                        //System.out.println("count="+count+" sectionLength="+sectionLength);
                        //int streamType = fromSigned2Unsigned(tsPacketByte[payloadStart+startDescriptors+0]);
                        //System.out.println("streamType "+streamType);
                        /*if(streamType==2){
                            System.out.println("streamType "+streamType+" video");
                        }
                        else if(streamType==3){
                            System.out.println("streamType "+streamType+" audio");
                        }
                        else if(streamType==4){
                            System.out.println("streamType "+streamType+" audio");
                        }
                        else if(streamType==0x81){
                            System.out.println("streamType "+streamType+" AC-3 audio");
                        }
                        else{
                            System.out.println("streamType "+streamType+" ???");
                        }*/

                        //int elementaryPid = fromSigned2Unsigned(tsPacketByte[payloadStart+startDescriptors+1]&(1+2+4+8+16)) * 256 +
                        //    fromSigned2Unsigned(tsPacketByte[payloadStart+startDescriptors+2]);
                        //System.out.println("elementaryPid "+elementaryPid);

                        int esInfoLength = fromSigned2Unsigned(tsPacketByte[payloadStart+startDescriptors+3]&(1+2)) * 256 +
                            fromSigned2Unsigned(tsPacketByte[payloadStart+startDescriptors+4]);
                        /*System.out.println("esInfoLength "+esInfoLength);
                        String esDescriptor = "";
                        for(int i=0; i<esInfoLength; i++){
                            char ch = (char)tsPacketByte[payloadStart+startDescriptors+4+i];
                            esDescriptor+=ch;
                            System.out.println("payloadStart+startDescriptors+4+i "+(payloadStart+startDescriptors+4+i)+" "+ch);
                        }
                        System.out.println("esDescriptor "+esDescriptor);*/

                        startDescriptors = startDescriptors + 5 +esInfoLength;
                        count += 5 + esInfoLength;
                    }
                    //System.out.println();
                    //System.out.println("count="+count+" sectionLength="+sectionLength);
                }
                else if(tableId == 0x40){
                    System.out.println("NIT (Network Information Table) this network");
                    //frameType = "NIT this network";
                    frameType = TSPacket.NIT_THIS_NET;
                }
                else if(tableId == 0x41){
                    System.out.println("NIT (Network Information Table) other networks");
                    //frameType = "NIT other network";
                    frameType = TSPacket.NIT_OTHER_NET;
                }
                else if(tableId == 0x46){
                    System.out.println("SDT");
                    //frameType = "SDT";
                    frameType = TSPacket.SDT;
                }
                else if(tableId == 0x4a){
                    System.out.println("BAT");
                    //frameType = "BAT";
                    frameType = TSPacket.BAT;
                }
                else if(tableId >= 0x4e && tableId <= 0x6f){
                    System.out.println("EIS (Event Information Section)");
                    //frameType = "EIS";
                    frameType = TSPacket.EIS;
                }
                else if(tableId == 0x70){
                    //frameType = "TDT";
                    frameType = TSPacket.TDT;
                    System.out.println("TDT (Time and Date Table)");
                }
                else if(tableId == 0x73){
                    //frameType = "TOT";
                    frameType = TSPacket.TOT;
                    System.out.println("TOT");
                }
                else if(tableId == 0xc7){
                    //frameType = "MGT";
                    frameType = TSPacket.MGT;
                    System.out.println("MGT (Master Guide Table)");
                }
                else if(tableId == 0xc8){
                    //frameType = "TVCT";
                    frameType = TSPacket.TVCT;
                    System.out.println("TVCT (Terrestrial Virtual Channel Table)");
                }
                else if(tableId == 0xcb){
                    //frameType = "EIT";
                    frameType = TSPacket.EIT;
                    System.out.println("EIT (Event Information Table)");
                }
                else if(tableId == 0xcc){
                    //frameType = "ETT";
                    frameType = TSPacket.ETT;
                    System.out.println("ETT (Extended Text Table)");
                }
                else if(tableId == 0xcd){
                    //frameType = "STT";
                    frameType = TSPacket.STT;
                    System.out.println("STT (System Time Table)");
                }
                else{
                    System.out.println("Unknown table: "+tableId+" 0x"+Integer.toString(tableId, 16));
                    //frameType = "Unknown table: "+tableId+" 0x"+Integer.toString(tableId, 16);
                    frameType = TSPacket.UNKNOWN_TABLE;
                }
            }
            tsPacket.setFrameType(frameType);
        	//System.out.println("RtpMpegTsParser parseTsPacketPayload END frameType="+frameType);
        }
    }
    
    @Override
    public void stopRtpMpegParser() {
        System.out.println("RtpMpegTsParser STOP");
        queue.add(new RTP(null));
        active = false;
    }
    
    private byte parseMpeg2(int mpegPayloadStart, byte[] tsPacketByte){
    	//String frameType = null;
    	byte frameType = TSPacket.UNDEFINED;
    	
    	int startCode = fromSigned2Unsigned(tsPacketByte[mpegPayloadStart+3]);
        //System.out.println("startCode  0x"+Integer.toString(startCode,16));
    	
    	// see http://dvd.sourceforge.net/dvdinfo/mpeghdrs.html
        if(startCode==0){
            //System.out.println("Picture");
            int frameTypeInt = (fromSigned2Unsigned(tsPacketByte[mpegPayloadStart+5])&56)>>3;
            //System.out.println("frameTypeInt "+frameTypeInt);
            if(frameTypeInt==1){
                //frameType = "VideoI";
            	frameType = TSPacket.I_FRAME;
            }
            else if(frameTypeInt==2){
                //frameType = "VideoP";
            	frameType = TSPacket.P_FRAME;
            }
            else if(frameTypeInt==3){
                //frameType = "VideoB";
            	frameType = TSPacket.B_FRAME;
            }
            else if(frameTypeInt==4){
                //frameType = "VideoD";
            	frameType = TSPacket.D_FRAME;
            }
            //System.out.println("mpeg2 frameType "+frameType);
            mpegPayloadStart += 4;
        }
        else if(startCode>=0x01 && startCode<=0xaf){
            //System.out.println("Slice"); // XXX
            //frameType = "slice";
        	frameType = TSPacket.GENERIC_SLICE;
        }
        else if(startCode==0xB2){
            //System.out.println("user data");
        }
        else if(startCode==0xB3){
            //System.out.println("Sequence header");
            mpegPayloadStart += 8;
        }
        else if(startCode==0xB5){
            //System.out.println("extension");
            mpegPayloadStart += 6;
        }
        else if(startCode==0xB6){
            //System.out.println("reserved");
            mpegPayloadStart += 3;
        }
        else if(startCode==0xB8){
            //System.out.println("GOP");
            mpegPayloadStart += 4;
        }
        else if( startCode>=0xC0 && startCode<=0xDF ){
            //System.out.println("MPEG-1 or MPEG-2 audio stream");
            mpegPayloadStart += 3;
        }
        else if( startCode>=0xE0 && startCode<=0xEF ){
            //System.out.println("MPEG-1 or MPEG-2 video stream");
            mpegPayloadStart += 3;
        }
        else{
            System.out.println("unsupported startCode "+startCode+" 0x"+Integer.toString(startCode,16));
        }
        
    	return frameType;
    }
    
    //private String parseH264(int mpegPayloadStart, byte[] tsPacketByte){
    private byte parseH264(int mpegPayloadStart, byte[] tsPacketByte){
    	//System.out.println("parseH264 START "+System.currentTimeMillis());
        //String sliceType = null;
    	byte sliceType = TSPacket.UNDEFINED;
    	
    	byte nalu = tsPacketByte[mpegPayloadStart+3];
        //int forbiddenBit = (nalu>>7) & 1;
        //int referenceIdc = (nalu>>5) & 3;
        int unitType = (nalu) & 0x1f; 
        //System.out.println("forbiddenBit="+forbiddenBit+" referenceIdc="+referenceIdc+" unitType="+unitType);
        /*
         * unitType == 9 => access unit delimiter
         * unitType == 7 ==> sequence parameter set
         * unitType == 5 ==> idr picture
         * unitType == 1 ==> codec slice (key frame with referenceIdc = 2) ???
         * idc - type
         * 0 - 6 ==> I frame???
         * 2 - 1 ==> P frame???
         * 0 - 1 ==> B frame???
         */
        
        /*
        PicType = fetch();
        If (PicType == 0)
          PictureType = P-mult;
        Else if (PicType == 1)
          PictureType = P;
        Else if (PicType == 2)
          PictureType = I;
        Else if (PicType == 3)
          PictureType = B;
        Else if (PicType == 4)
          PictureType = B-mult;
        Else if (PicType == 5)
          PictureType = SP;
        Else if (PicType == 6)
          PictureType = SP-mult;
        Else
          Abort();
        */
        
        /*if( referenceIdc==0 && unitType==6 ){
        	sliceType = "VideoI"; // ???
        }
        else if( referenceIdc==2 && unitType==1 ){
        	frameType = "VideoP"; // ???
        }
        else if( referenceIdc==0 && unitType==1 ){
        	frameType = "VideoB"; // ???
        }*/
        
        //else if( unitType==1 || unitType==5 ){
        if( unitType == 9 ){
        	//System.out.println("h264 Access Unit Delimiter");
        	sliceType = TSPacket.AUD_SLICE;
        }
        else if( unitType == 6 ){
        	//System.out.println("h264 SEI Information");
        	sliceType = TSPacket.IDR_FRAME; //"VideoI"; // ???
        } 
        else if( unitType>=6 && unitType<=12 ){
        	//sliceType = "non-vcl";
        	sliceType = TSPacket.NON_VCL_SLICE;
        	//System.out.println("h264 non-vcl");
        }
        else if( unitType==5 ){
        	//sliceType = "non-vcl";
        	//System.out.println("h264 IDR");
        	sliceType = TSPacket.IDR_FRAME;
        }
        else if( unitType==1 ){ // coded slice
        	byte sliceHeader = tsPacketByte[mpegPayloadStart+4];
        	BitSet bits = new BitSet();
            for (int i=0; i<8; i++) {
                if ((sliceHeader & (1<<(i%8))) > 0) {
                    bits.set(i);
                }
            }
            
        	//boolean firstBit = bits.get(7);
        	//System.out.println("h264 firstBit "+firstBit);
        	//if( firstBit==1){
//        	if(firstBit){
        		//System.out.println("h264 StartPicture (first_mb_in_slice)");
            	//System.out.println("h264 the rest of the byte "+ (tsPacketByte[mpegPayloadStart+4] & 0x7F) );
            	int count = 0;
            	int i = 6;
            	//for(int k=7; k>=0; k--){
            	//	System.out.println("h264 "+k+" "+ bits.get(k));
            	//}
            	while( ! bits.get(i) ){
            		count++;
            		i--;
            	}
            	//System.out.println("h264 count="+count+" i="+i);
            	//i--;
            	int sliceTypeInt = 0;
            	for(int j=0; j<count; j++){
            		if(bits.get(i-count+j)){
            			sliceTypeInt += Math.pow(2,j);
            			//System.out.println("h264 frameTypeInt="+sliceTypeInt+" j="+j);
            		}
            	}
            	//System.out.println("h264 count="+count+" sliceTypeInt="+ sliceTypeInt+" 0b"+Integer.toString(sliceTypeInt,2));
            	if( count == 0 ){ // XXX
                	sliceType = TSPacket.P_SLICE;//"VideoP";
            	}
            	else if ( count == 1 ){
            		if( sliceTypeInt == 0 ){
            			sliceType = TSPacket.B_SLICE; // "VideoB";
            		}
            		else if( sliceTypeInt == 1 ){
            			sliceType = TSPacket.I_SLICE;//"VideoI";
            		}
            	}
            	else if( count == 2 ){
            		if( sliceTypeInt == 0 ){
            			sliceType = TSPacket.SP_SLICE;//"VideoSP";
            		}
            		else if( sliceTypeInt == 1 ){
            			sliceType = TSPacket.SI_SLICE;//"VideoSI";
            		}
            		else if( sliceTypeInt == 2 ){
            			sliceType = TSPacket.P_SLICE;//"VideoP";
            		}
            		else if( sliceTypeInt == 3 ){
            			sliceType = TSPacket.B_SLICE;//"VideoB";
            		}
            	}
            	else if( count == 3 ){
            		if( sliceTypeInt == 0 ){
            			sliceType = TSPacket.I_SLICE;//"VideoI";
            		}
            		else if( sliceTypeInt == 1 ){
            			sliceType = TSPacket.SP_SLICE;//"VideoSP";
            		}
            		else if( sliceTypeInt == 2 ){
            			sliceType = TSPacket.SI_SLICE;//"VideoSI";
            		}
            	}
            	//System.out.println("h264 sliceType="+sliceType);
/*        	}
        	else{ // no start picture
        		System.out.println("h264 (no start picture) sliceHeader="+Integer.toString(sliceHeader,2));
        	}
 */       }
        
        //System.out.println(Integer.toString(fromSigned2Unsigned(tsPacketByte[mpegPayloadStart+4]),2));
        //System.out.println(Integer.toString(fromSigned2Unsigned(tsPacketByte[mpegPayloadStart+5]),2));
        if(sliceType != TSPacket.UNDEFINED){
 //       	System.out.println("h264 sliceType "+sliceType);
        }
        
        /*
        int nal_type = Data[1] & 0x1F;
        int start_bit = Data[1] & 0x80;
        int end_bit = Data[1] & 0x40;
        If fragment_type == 28 then payload following it is one fragment of IDR
        */
        
        
        /*
        if(nal_unit_type==1 or nal_unit_type==5){
         // current NAL contains picture data
         // determine whether current NAL is the first NAL of current picture
         // read single bit in order to determine whether first_mb_in_slice is zero or not

         Bit=PeekOneBit()
         If (bit==1)
            Return 1  // in exp-Golomb if first bit is one then code-word is zero
        }
        */

    	//System.out.println("parseH264 END "+System.currentTimeMillis());
    	return sliceType;
    }
    
    
}
