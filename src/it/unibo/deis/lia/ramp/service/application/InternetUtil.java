
package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.e2e.GenericPacket;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;


/**
 *
 * @author Carlo Giannelli
 */
public class InternetUtil extends Thread{
	
	private static boolean useStringBuilder = Boolean.parseBoolean(RampEntryPoint.getRampProperty("stringBuilder"));
	
	// methods shared by InternetService and InternetClient
	protected static String readLine(InputStream is) throws Exception{
		if( useStringBuilder ){
			return readLineStringBuilder(is);
		}
		else{
			return readLineStringConcat(is);
		}
	}
	private static String readLineStringConcat(InputStream is) throws Exception{
		String res = "";
		int temp = is.read();
		while( temp != 0x0D ){
			res += (char)temp;
			temp = is.read();
		}
		is.read(); // skip (char)0x0A
		return res;
	}
	private static String readLineStringBuilder(InputStream is) throws Exception{
		// FIXME con StringBuilder OutOfMemoryError sul NAS di Telecom
		String res = null;
		StringBuilder sb = new StringBuilder(192); // most of the lines are shorter than 192 chars
		int temp = is.read();
		while( temp != 0x0D ){
			if(sb.capacity() >= 4000){ 
//				System.out.println("InternetUltil.readLineStringBuilder: sb.toString() " + sb.toString());
//				System.out.println("InternetUltil.readLineStringBuilder: sb.length() " + sb.length() + " sb.capacity() " + sb.capacity());
				sb.setLength(0);
				sb = null;
				return null; // reset the sb to avoid the out of memory exception
			}
			sb.append((char)temp);
			temp = is.read();
		}
		is.read(); // skip (char)0x0A
		
//		if(sb.length() > 1000){
//			System.out.println("InternetUltil.readLineStringBuilder: sb.toString() " + sb.toString());
//			System.out.println("InternetUltil.readLineStringBuilder: sb.length() " + sb.length() + " sb.capacity() " + sb.capacity());
//		}
		res = sb.toString();
		sb = null;
		return res;
	}
    
    protected static byte[] performInternetConnection(InternetRequest internetRequest) throws Exception{// connecting to the remote server
        String serverAddress = internetRequest.getServerAddress();
        int serverPort = internetRequest.getServerPort();
        int serverProtocol = internetRequest.getLayer4Protocol();
        //System.out.println("\tInternetUtil.performInternetConnection to "+serverAddress+":"+serverPort);

        byte[] res = null;
        if(serverProtocol == InternetRequest.UDP){
            DatagramSocket destS = new DatagramSocket();
            DatagramPacket destDp = new DatagramPacket(
                    internetRequest.internetPayload,
                    internetRequest.internetPayload.length,
                    InetAddress.getByName(serverAddress),
                    serverPort
            );

            destS.send(destDp);

            byte[] buffer = new byte[GenericPacket.MAX_UDP_PACKET];
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            destS.receive(dp);

            res = dp.getData();
            destS.close();
        }
        else if (serverProtocol == InternetRequest.TCP){
            Socket s = null;
            try{
                s = new Socket(
                    serverAddress,
                    serverPort
                );
            }
            catch(UnknownHostException uhe){
                res = "InternetUtil.performInternetConnection: unknown host".getBytes();
            }
            if(res==null){
                OutputStream os = s.getOutputStream();
                InputStream is=s.getInputStream();

                // send data to the remote server
                byte[] internetPayload = internetRequest.getInternetPayload();
                os.write(internetPayload);
                os.flush();
                //System.out.println("InternetUtil internetPayload sent to remote server");

                // receive HEADERS from remote server
                String line = readLine(is); // first line
//                String text="";
                StringBuilder text = new StringBuilder();
                int contentLength = -1;
                boolean chunked = false;
                boolean connectionClose = false;
                //System.out.println("InternetUtil line "+line);
                while( line!=null && !line.equals("") ){
//                    text += line + (char)0x0D+(char)0x0A;
                	text.append(line + (char)0x0D+(char)0x0A);
                    //System.out.println("\tInternetUtil.performInternetConnection line "+line);
                    if( line.contains("Content-Length") ){
                        String length = line.split(" ")[1];
                        //System.out.println("InternetUtil length "+length);
                        contentLength = Integer.parseInt(length);
                    }
                    else if(line.contains("Transfer-Encoding: chunked")){
                        chunked = true;
                    }
                    else if( line.contains("Connection: close") ){
                        connectionClose=true;
                    }
                    line = readLine(is);
                    //System.out.println("InternetUtil line "+line);
                }
                if( ! connectionClose ){
//                    text += "Connection: close" + (char)0x0D + (char)0x0A;
                	text.append("Connection: close" + (char)0x0D + (char)0x0A);
                }
//                text += "" + (char)0x0D + (char)0x0A;
                text.append("" + (char)0x0D + (char)0x0A);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if( chunked ){
                    line = readLine(is);
                    //System.out.println("\tInternetUtil.performInternetConnection chunked line "+line);
                    int lineLength = Integer.decode("0x"+line);
                    //System.out.println("\tInternetUtil.performInternetConnection chunked lineLength "+lineLength);
                    for(int i=0; i<line.length(); i++){
                        baos.write(line.charAt(i));
                    }
                    baos.write(0x0D); // (char)0x0D
                    baos.write(0x0A); // (char)0x0A
                    while(!line.equals("0")){
                        for(int i=0; i<lineLength; i++){
                            int temp=is.read();
                            //System.out.print((char)temp);
                            baos.write(temp);
                        }
                        //System.out.println();
                        //System.out.println("InternetUtil chunked buf "+new String(buf));
                        baos.write(is.read()); // (char)0x0D
                        baos.write(is.read()); // (char)0x0A

                        line = readLine(is);
                        //System.out.println("\tInternetUtil.performInternetConnection chunked line "+line);
                        lineLength = Integer.decode("0x"+line);
                        //System.out.println("\tInternetUtil.performInternetConnection chunked lineLength "+lineLength);
                        for(int i=0; i<line.length(); i++){
                            baos.write(line.charAt(i));
                        }
                        baos.write(0x0D); // (char)0x0D
                        baos.write(0x0A); // (char)0x0A
                    }
                    baos.write(0x0D); // (char)0x0D
                    baos.write(0x0A); // (char)0x0A
                }

                byte[] chunkedArray = baos.toByteArray();
                if(contentLength==-1 && chunkedArray.length==0){
                    res=text.toString().getBytes();
                }
                else if(chunkedArray.length!=0){
                    res=new byte[text.length()+chunkedArray.length];
                    System.arraycopy(text.toString().getBytes(), 0, res, 0, text.length());
                    System.arraycopy(chunkedArray, 0, res, text.length(), chunkedArray.length);
                }
                else{
                    // receiving DATA from remote server
                    res = new byte[text.length()+contentLength];
                    System.arraycopy(text.toString().getBytes(), 0, res, 0, text.length());

                    int temp=0;
                    for(int i = text.length(); temp != -1 && i < res.length; i++){
                        temp = is.read();
                        res[i] = (byte)temp;
                        //System.out.print(""+(char)temp);
                    }
                }
                s.close();
            }
        }
        else{
            System.out.println("\tInternetUtil.performInternetConnection: unsupported layer-4 protocol: "+internetRequest.getLayer4Protocol());
            res="InternetUtil.performInternetConnection: unknown protocol ".getBytes();
        }
        return res;
    }

    protected static void performInternetConnection(InternetRequest internetRequest, OutputStream writeResponseTo) throws Exception{// connecting to the remote server
        String serverAddress = internetRequest.getServerAddress();
        int serverPort = internetRequest.getServerPort();
        int serverProtocol = internetRequest.getLayer4Protocol();
        //System.out.println("\tInternetUtil.performInternetConnection to "+serverAddress+":"+serverPort);

        if(serverProtocol == InternetRequest.UDP){
            DatagramSocket destS = new DatagramSocket();
            DatagramPacket destDp = new DatagramPacket(
                    internetRequest.internetPayload,
                    internetRequest.internetPayload.length,
                    InetAddress.getByName(serverAddress),
                    serverPort
            );

            destS.send(destDp);

            byte[] buffer = new byte[GenericPacket.MAX_UDP_PACKET];
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
            destS.receive(dp);

            writeResponseTo.write(dp.getData());
            writeResponseTo.flush();
            
            destS.close();
        }
        else if (serverProtocol == InternetRequest.TCP){
            Socket destS = null;
            
            // connect to the final destination (e.g. web server)
            try{
                destS = new Socket(
                    serverAddress,
                    serverPort
                );
            }
            catch(UnknownHostException uhe){
                uhe.printStackTrace();
            }
            
            if(destS.isConnected()){
                OutputStream destOs = destS.getOutputStream();
                InputStream destIs=destS.getInputStream();

                // send http request to the remote server
                byte[] internetPayload = internetRequest.getInternetPayload();
                destOs.write(internetPayload);
                destOs.flush();
                //System.out.println("InternetUtil internetPayload sent to remote server");

                // receive http response from remote server
                
                // forward http headers
                String line = readLine(destIs); // first line
                int contentLength = -1;
                boolean chunked = false;
                boolean connectionClose = false;
                //System.out.println("InternetUtil line "+line);
                while( line!=null && !line.equals("") ){
                    writeResponseTo.write(line.getBytes());
                    writeResponseTo.write(0x0D);
                    writeResponseTo.write(0x0A);
                    //System.out.println("\tInternetUtil.performInternetConnection line "+line);
                    if( line.contains("Content-Length") ){
                        String length = line.split(" ")[1];
                        //System.out.println("InternetUtil length "+length);
                        contentLength = Integer.parseInt(length);
                    }
                    else if(line.contains("Transfer-Encoding: chunked")){
                        chunked = true;
                    }
                    else if( line.contains("Connection: close") ){
                        connectionClose=true;
                    }
                    line = readLine(destIs);
                    //System.out.println("InternetUtil line "+line);
                }
                if( ! connectionClose ){
                    writeResponseTo.write("Connection: close".getBytes()); 
                    writeResponseTo.write(0x0D);
                    writeResponseTo.write(0x0A);
                }
                writeResponseTo.write(0x0D);
                writeResponseTo.write(0x0A);
                writeResponseTo.flush();
                
                // forward http payload                
                if( chunked ){
                    line = readLine(destIs);
                    System.out.println("\tInternetUtil.performInternetConnection chunked line "+line);
                    int lineLength = Integer.decode("0x"+line);
                    //System.out.println("\tInternetUtil.performInternetConnection chunked lineLength "+lineLength);
                    for(int i=0; i<line.length(); i++){
                    	writeResponseTo.write(line.charAt(i));
                    }
                    writeResponseTo.write(0x0D); // (char)0x0D
                    writeResponseTo.write(0x0A); // (char)0x0A
                    while(!line.equals("0")){
                        for(int i=0; i<lineLength; i++){
                            int temp=destIs.read();
                            //System.out.print((char)temp);
                            writeResponseTo.write(temp);
                        }
                        //System.out.println();
                        //System.out.println("InternetUtil chunked buf "+new String(buf));
                        writeResponseTo.write(destIs.read()); // (char)0x0D
                        writeResponseTo.write(destIs.read()); // (char)0x0A

                        line = readLine(destIs);
                        //System.out.println("\tInternetUtil.performInternetConnection chunked line "+line);
                        lineLength = Integer.decode("0x"+line);
                        //System.out.println("\tInternetUtil.performInternetConnection chunked lineLength "+lineLength);
                        for(int i=0; i<line.length(); i++){
                        	writeResponseTo.write(line.charAt(i));
                        }
                        writeResponseTo.write(0x0D); // (char)0x0D
                        writeResponseTo.write(0x0A); // (char)0x0A
                    }
                    writeResponseTo.write(0x0D); // (char)0x0D
                    writeResponseTo.write(0x0A); // (char)0x0A
                    writeResponseTo.flush();
                }else{
                	int readByte;
                	int totalRead = 0;
                	byte[] buffer = new byte[E2EComm.DEFAULT_BUFFERSIZE];
                	BufferedInputStream bis = new BufferedInputStream(destIs, E2EComm.DEFAULT_BUFFERSIZE);
                	// XXX In jetty, sometimes content-length isn't present 
                	while(/*totalRead < contentLength &&*/ (readByte = bis.read(buffer, 0, buffer.length)) != -1){
                		writeResponseTo.write(buffer, 0, readByte);
                		writeResponseTo.flush();
                		//System.out.println("\tInternetUtil.performInternetConnection read partial payload " + readByte + " bytes");
                		totalRead += readByte;
                	}
                	//System.out.println("\tInternetUtil.performInternetConnection total read: " + totalRead + " (of " + contentLength +")");
                }
                //System.out.println("\tInternetUtil.performInternetConnection end");
            	destS.close();
            }
        }
        else{
            System.out.println("\tInternetUtil.performInternetConnection: unsupported layer-4 protocol: "+internetRequest.getLayer4Protocol());
        }
    }
    
}
