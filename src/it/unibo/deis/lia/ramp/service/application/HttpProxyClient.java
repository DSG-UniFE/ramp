
package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.BoundReceiveSocket;
import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.internode.Resolver;
import it.unibo.deis.lia.ramp.core.internode.ResolverPath;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.BindException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.Vector;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.HtmlNode;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.TagNodeVisitor;

/**
 *
 * @author Luca Iannario
 */
public class HttpProxyClient extends Thread{
	
	// XXX 6004 e' la porta preferita da Telecom (gia' impostata nei loro due router)
	private static int HTTP_PROXY_CLIENT_PORT_MIN_PORT = 6004;
	private static int HTTP_PROXY_CLIENT_PORT_MAX_PORT = 6005;
	private static boolean active = false;
	private static HttpProxyClient httpProxyClient = null;
	
	private int listeningPort = -1;

	public static synchronized HttpProxyClient getInstance(){
		if(httpProxyClient == null){
			active = true;
			System.out.println("HttpProxyClient.activate");
			httpProxyClient = new HttpProxyClient();
			httpProxyClient.start();
		}
		return httpProxyClient;
	}
	
    public static boolean isActive(){
    	return active;
    }

	public void stopClient(){
		System.out.println("HttpProxyClient.deactivate");
		active = false;
		httpProxyClient = null;
	}
	
	int getListeningPort(){
		return this.listeningPort;
	}


	@Override
	public void run(){
		try{
			System.out.println("HTTPProxyClient START");
			ServerSocket ssFromTheLocalApplication = null;
			for(int port = HTTP_PROXY_CLIENT_PORT_MIN_PORT; port <= HTTP_PROXY_CLIENT_PORT_MAX_PORT; port++){
				try{
					ssFromTheLocalApplication = new ServerSocket(port);
					break;
				}catch (BindException e) {}
			}
			if(ssFromTheLocalApplication != null){
				synchronized (this) {
					listeningPort = ssFromTheLocalApplication.getLocalPort();
					notifyAll();
				}
				System.out.println("HTTPProxyClient listening on port " + listeningPort);
				ssFromTheLocalApplication.setReuseAddress(true);
				ssFromTheLocalApplication.setSoTimeout(5*1000);
				while(active){
					try{
						Socket s = ssFromTheLocalApplication.accept();
						//System.out.println("HTTPProxyClient new request");
						new HTTPProxyClientHandler(s).start();
					}
					catch(SocketTimeoutException ste){
						//System.out.println("InternetService SocketTimeoutException");
					}
					catch(SocketException se){
						//System.out.println("InternetService SocketTimeoutException");
					}
				}
				ssFromTheLocalApplication.close();
			}else{
				System.out.println("HTTPProxyClient cannot bind ssFromTheLocalApplication socket");
			}
			System.out.println("HTTPProxyClient FINISHED");
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

	// Connection parameters		
	int httpProxyServerNodeId = -1;
	String[] pathToHttpProxyServer = null;
	int protocolHttpProxyServer = -1;
	int portHttpProxyServer = -1;
	String ipAddressRemoteEndpoint = null;
	int portRemoteEndpoint = 80;
	
	private class HTTPProxyClientHandler extends Thread{
		private Socket sToTheLocalApplication;
		private HTTPProxyClientHandler(Socket s){
			this.sToTheLocalApplication = s;
		}
		
		@Override
		public void run(){
			try{
				// START HTTP REQUEST PARSING

				// receive data from local browser
				InputStream isLocalApplication = sToTheLocalApplication.getInputStream();

				// setting defaults
				int layer4Protocol = InternetRequest.TCP;
				StringBuilder text = new StringBuilder();
				int contentLength = -1;
				boolean connectionClose = false;
				boolean chunked = false;

				String line = InternetUtil.readLine(isLocalApplication); // first line
				line = URLDecoder.decode(line, "UTF-8").replaceFirst("ramp:/([^/])", "ramp://$1").replaceFirst("http:/([^/])", "http://$1"); // Fix for Facebook
				
				// parsing header HTTP
				while( line != null && !line.equals("") ){
					String lineLowerCase = line.toLowerCase();
					//System.out.println("HTTPProxyClient line " + line);
					if( lineLowerCase.startsWith("get") || lineLowerCase.startsWith("post") ){ 
						System.out.println("HTTPProxyClient line " + line);
						// need to modify the GET line accordingly
						// well-formed line pattern: 
						// |                  tokens[0]                  |        |               tokens[1]                |
						// GET /ramp://httpProxyServerNodeId:port:protocol/http://ipWebServer:port/path/to/resource HTTP/1.1
						try{
							String[] tokens = line.split("/http://");
							String[] httpProxyServerRampURLTokens = tokens[0].split("/ramp://")[1].split(":");
							String method = tokens[0].split(" ")[0];
							// extract httpProxyServerNodeId: if line is not like the pattern above, parseInt will
							// raise an exception. In this case, we continue to use previously extracted values of *HttpProxyServer
							// until a new well-formed line is received
							httpProxyServerNodeId = Integer.parseInt(httpProxyServerRampURLTokens[0]);
							portHttpProxyServer = Integer.parseInt(httpProxyServerRampURLTokens[1]);
							protocolHttpProxyServer = Integer.parseInt(httpProxyServerRampURLTokens[2]);
							// use Resolver to find the shortest path to httpProxyServerNodeId
							pathToHttpProxyServer = findShortestPathTo(httpProxyServerNodeId);
							if(pathToHttpProxyServer == null)
								throw new MalformedURLException();
							String remoteEndPointURL = tokens[1].split(" ")[0];
							
							// Reject the request if the target is /upnp/index.jsp 
							// XXX (this code drops all the requests containing /upnp/index.jsp in the request line)
//							if(remoteEndPointURL.contains("/upnp/index.jsp"))
//								return;
							
							String[] remoteEndPointAddrTokens = remoteEndPointURL.replaceFirst("/.*", "").split(":"); // extract ipWebServer:port (remove everything after the first /, included)
							ipAddressRemoteEndpoint = remoteEndPointAddrTokens[0];
							portRemoteEndpoint = Integer.parseInt(remoteEndPointAddrTokens[1]);
							String path = remoteEndPointURL.replaceFirst("[^/]*/", "/"); // extract /path/to/resource (remove everything before the first /)
//							line = "GET " + path + " HTTP/1.1"; // supporting only http/1.1 GET requests at the moment // TODO extend support to POST?
							line = method + " " + path + " HTTP/1.1";
							System.out.println("HTTPProxyClient line modified " + line);
						} catch (Exception e){
							//e.printStackTrace();
						}
					}
//					else if(line.toLowerCase().startsWith("post")){
//						// force GET (otherwise it is also possible to post)
//						line = "GET" + line.substring("post".length());
//					}
					else if( lineLowerCase.startsWith("host") ){
						// need to modify the Host line as well
						// it originally contains the address (ip:port) of this proxy client
						// it will contain the address (ip:port) of the actual destination web server
						// (usually this address is local to the proxy server)
						if(ipAddressRemoteEndpoint != null && ipAddressRemoteEndpoint != ""){ 
							line = "Host: " + ipAddressRemoteEndpoint + ":" + portRemoteEndpoint;
							System.out.println("HTTPProxyClient line modified " + line);
						}
					}
					else if( lineLowerCase.startsWith("layer4protocol") ){
						String stringLayer4Protocol = line.split(" ")[1];
						if(stringLayer4Protocol.toLowerCase().equals("tcp")){
							layer4Protocol = InternetRequest.TCP;
						}
						else if(stringLayer4Protocol.toLowerCase().equals("udp")){
							layer4Protocol = InternetRequest.UDP;
						}
						else{
							System.out.println("HTTPProxyClient: unsupported layer-4 protocol: " + layer4Protocol + " (using the default: TCP)");
						}
					}

					if( lineLowerCase.startsWith("connection: keep-alive") ){
						// removing "Connection: keep-alive" header
						//System.out.println("\t\tdeleted: " + line);
					}
					else{
						//							text += line + (char)0x0D + (char)0x0A;
						text.append(line + (char)0x0D + (char)0x0A);
					}

					if(lineLowerCase.startsWith("content-length")){
						String length = line.split(" ")[1];
						//System.out.println("HTTPProxyClient length " + length);
						contentLength = Integer.parseInt(length);
					}
					else if(lineLowerCase.startsWith("connection: close")){
						connectionClose = true;
					}
					else if(lineLowerCase.startsWith("transfer-encoding: chunked")){
						chunked = true;
					}

					line = InternetUtil.readLine(isLocalApplication);
				} // while

				if( ! connectionClose ){
					// manually adding "Connection: close" header
					//						text += "Connection: close" + (char)0x0D + (char)0x0A;
					text.append("Connection: close" + (char)0x0D + (char)0x0A);
				}

				//					text  += "" + (char)0x0D + (char)0x0A;
				text.append("" + (char)0x0D + (char)0x0A);

				// parsing payload HTTP
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				if(chunked){
					line = InternetUtil.readLine(isLocalApplication);
					//System.out.println("InternetUtil chunked line " + line);
					int lineLength = Integer.decode("0x" + line);
					//System.out.println("InterneInternetUtiltService chunked lineLength " + lineLength);
					for(int i = 0; i < line.length(); i++){
						baos.write(line.charAt(i));
					}
					baos.write(0x0D); // CR
					baos.write(0x0A); // LF
					while( ! line.equals("0") ){
						for(int i = 0; i < lineLength; i++){
							int temp = isLocalApplication.read();
							//System.out.print((char)temp);
							baos.write(temp);
						}
						//System.out.println();
						//System.out.println("InternetService chunked buf " + new String(buf));
						baos.write(isLocalApplication.read()); // (char)0x0D
						baos.write(isLocalApplication.read()); // (char)0x0A

						line = InternetUtil.readLine(isLocalApplication);
						//System.out.println("InternetService chunked line " + line);
						lineLength = Integer.decode("0x" + line);
						//System.out.println("InternetService chunked lineLength " + lineLength);
						for(int i = 0; i < line.length(); i++){
							baos.write(line.charAt(i));
						}
						baos.write(0x0D); // (char)0x0D
						baos.write(0x0A); // (char)0x0A
					}
					baos.write(0x0D); // (char)0x0D
					baos.write(0x0A); // (char)0x0A
				}
				byte[] chunkedArray = baos.toByteArray();
				byte[] sending = null;
				if(contentLength == -1 && chunkedArray.length == 0){ // no http payload
					sending = text.toString().getBytes();
				}
				else if( chunkedArray.length != 0 ){
					sending = new byte[text.length() + chunkedArray.length];
					System.arraycopy(text.toString().getBytes(), 0, sending, 0, text.length());
					System.arraycopy(chunkedArray, 0, sending, text.length(), chunkedArray.length);
				}
				else{
					sending = new byte[text.length() + contentLength];
					System.arraycopy(text.toString().getBytes(), 0, sending, 0, text.length());

					int temp = 0;
					for(int i = text.length(); temp != -1 && i < sending.length; i++){
						temp = isLocalApplication.read();
						//System.out.print("" + (char)temp);
						sending[i] = (byte)temp;
					}
				}
				baos.close();

				// END HTTP REQUEST PARSING

				// prepare local socket for receiving the answer
				final BoundReceiveSocket receiveSocket = E2EComm.bindPreReceive(layer4Protocol);

				// create the request
				InternetRequest ir = new InternetRequest(
						ipAddressRemoteEndpoint,
						portRemoteEndpoint,
						receiveSocket.getLocalPort(),
						layer4Protocol,
						sending
						);

				// send unicast to the proxy server with default bufferSize
				E2EComm.sendUnicast(
						pathToHttpProxyServer,
						portHttpProxyServer,
						protocolHttpProxyServer,
						//0, // default bufferSize
						E2EComm.serialize(ir)
						);

				// START HTTP RESPONSE PARSING

				float responseTotalSize = 0;

				PipedInputStream pis = new PipedInputStream();
				final PipedOutputStream pos = new PipedOutputStream(pis);
				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							// receive http response from the proxy server and write it to pos (piped to pis)
							// XXX tune timeout value...
							E2EComm.receive(
									receiveSocket,
									30*1000,
									pos
									);
						} catch (Exception e) {
							//e.printStackTrace();
						}
						try {
							pos.close();
							receiveSocket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
				long startTime = System.currentTimeMillis();
				thread.start();

				// The idea here is that we need to modify the http response (only
				// the html payload) because it contains links that are valid only in the
				// subnet of the answering webserver

				// get outputstream to the local application
				OutputStream osLocalApplication = sToTheLocalApplication.getOutputStream();

				//					text = "";
				boolean isHtml = false;
				contentLength = -1;
				chunked = false;

				line = InternetUtil.readLine(pis);
				
				// read http response headers
				// N.B. MUST compare strings in lowercase (or uppercase) to avoid error due by headers written in different ways
				while(line != null && !line.equals("")){
					String lineLowerCase = line.toLowerCase();
					//System.out.println("HTTPProxyClient response line " + line);
					if( lineLowerCase.startsWith("content-type: text/html") ){
						isHtml = true;
					}
					else if( lineLowerCase.startsWith("content-length") ){
						String length = line.split(" ")[1];
						//System.out.println("HTTPProxyClient length " + length);
						contentLength = Integer.parseInt(length);
					}
					else if( lineLowerCase.startsWith("transfer-encoding: chunked") ){
						chunked = true;
					}
					
					// copy http response header as is (except for the Content-Length header)
					else if( ! lineLowerCase.startsWith("content-length") ){
					//if( ! lineLowerCase.startsWith("content-length") ){
						osLocalApplication.write(line.getBytes());
						osLocalApplication.write(0x0D);
						osLocalApplication.write(0x0A);
						responseTotalSize += line.getBytes().length + 2;
					}
					line = InternetUtil.readLine(pis);
				}

				String newContentLength = null;

				// read http response payload

				if(isHtml){
					// Content-Type: text/html
					// clean the html payload and return a well-formed XML document
					HtmlCleaner cleaner = new HtmlCleaner();
					CleanerProperties props = cleaner.getProperties();

					// set some properties to non-default values
					props.setTranslateSpecialEntities(true);
					props.setTransResCharsToNCR(true);
					props.setOmitComments(true);

					// do parsing
					TagNode root = cleaner.clean(pis);

					// traverse each html node and modify
					// src and href attributes of each node (if present) as described below
					// (so that the browser will ask them through
					// the proxy with further http requests, instead of asking them
					// to the hosting webserver - that is not reachable directly)
					final int httpProxyServerNodeIdCopy = httpProxyServerNodeId;
					final int portHttpProxyServerCopy = portHttpProxyServer;
					final int protocolHttpProxyServerCopy = protocolHttpProxyServer;
					final String ipAddressRemoteEndpointCopy = ipAddressRemoteEndpoint;
					final int portRemoteEndpointCopy = portRemoteEndpoint; 
					root.traverse(new TagNodeVisitor() {
						public boolean visit(TagNode tagNode, HtmlNode htmlNode) {
//							System.out.println("HTTPProxyClient response content " + htmlNode.toString());
							if (htmlNode instanceof TagNode) {
								TagNode tag = (TagNode) htmlNode;
								
								/*String id = tag.getAttributeByName("id");	// id attribute
								String value = tag.getAttributeByName("value");

								if(value != null && value.equalsIgnoreCase("copy link to clipboard")) {		// Remove "copy link to clipboard" button from the view
									tagNode.removeChild(tag);
									return true;
								}
								else if(value != null && value.equalsIgnoreCase("Devices")) {	// Remove "Devices" button from the view
									tagNode.removeChild(tag);
									return true;
								}
								else if(value != null && value.equalsIgnoreCase("Parent")) {	// Remove "Parent" button from the view
									tagNode.removeChild(tag);
									return true;
								}
								else if(value != null && value.equalsIgnoreCase("Bookmark this")) {		// Remove "Bookmark this" button from the view
									tagNode.removeChild(tag);
									return true;
								}
								else if(value != null && value.equalsIgnoreCase("Remove from bookmarks")) {		// Remove "Remove from bookmarks" button from the view
									tagNode.removeChild(tag);
									return true;
								}
								else if(tag.getName().equalsIgnoreCase("iframe") && id != null && id.equalsIgnoreCase("mediaRenderer")) {	// Remove Media Renderer frame from the client view
									tagNode.removeChild(tag);
									return true;
								}*/
								
								String src = tag.getAttributeByName("src");
								String href = tag.getAttributeByName("href");
								String action = tag.getAttributeByName("action");	// Form action
								String classAttribute = tag.getAttributeByName("class");
								if(classAttribute != null && classAttribute.equalsIgnoreCase("skip"))
									return true;
								if(classAttribute != null && classAttribute.equalsIgnoreCase("toplevel")){
									TagNode brNode = new TagNode("br");
									TagNode backLinkNode = new TagNode("a");
									ContentNode backLinkNodeText = new ContentNode("Back");
									backLinkNode.setAttribute("class", "skip");
									backLinkNode.setAttribute("href", "javascript: window.history.go(-1);");
									backLinkNode.addChild(backLinkNodeText);
									tag.addChild(brNode);
									tag.addChild(backLinkNode);
								}else if(src != null){
									if(!src.toLowerCase().startsWith("http://")) // if the link is relative
										src = "http://" + ipAddressRemoteEndpointCopy + ":" + portRemoteEndpointCopy + src; // make it absolute
									// and transform it so that it will match the pattern described at the beginning of the first while loop
									src = "/ramp://" + httpProxyServerNodeIdCopy + ":" + portHttpProxyServerCopy + ":" + protocolHttpProxyServerCopy + "/" + src;
									tag.setAttribute("src", src);
								}else if(href != null){
									if(!href.toLowerCase().startsWith("http://")) // if the link is relative
										href = "http://" + ipAddressRemoteEndpointCopy + ":" + portRemoteEndpointCopy + href;
									// and transform it so that it will match the pattern described at the beginning of the first while loop
									href = "/ramp://" + httpProxyServerNodeIdCopy + ":" + portHttpProxyServerCopy + ":" + protocolHttpProxyServerCopy + "/" + href; // make it absolute
									tag.setAttribute("href", href);
								}else if(action != null) {
									if(!action.toLowerCase().startsWith("http://")) // if the link is relative
										action = "http://" + ipAddressRemoteEndpointCopy + ":" + portRemoteEndpointCopy + action;
									// and transform it so that it will match the pattern described at the beginning of the first while loop
									action = "/ramp://" + httpProxyServerNodeIdCopy + ":" + portHttpProxyServerCopy + ":" + protocolHttpProxyServerCopy + "/" + action; // make it absolute
									tag.setAttribute("action", action);
								}/*else if(id != null && id.equalsIgnoreCase("bookmarks")) {	// Remove bookmarks from the client view
									tagNode.removeChild(tag);
								}
								else if(id != null && id.equalsIgnoreCase("resourcePath")) {	// Remove resource path navigation from the client view
									for(Object child : tag.getChildTagList()) {
										if(child instanceof TagNode) {
											TagNode childTag = (TagNode) child;
											String childHref = childTag.getAttributeByName("href");
											if(childHref != null) {
												childTag.setAttribute("href", "#");
												childTag.setAttribute("class", "skip");	// The child is already been processed: next iterations have to skip it
											}
										}
									}
								}*/
							}
							// tells visitor to continue traversing the DOM tree
							return true;
						}
					});

					// if not set, the first line (?xml ...) will be serialized
					// leading to rendering issues for the browser
					props.setOmitXmlDeclaration(true);
					ByteArrayOutputStream temp = new ByteArrayOutputStream();
					SimpleHtmlSerializer serializer = 
							new SimpleHtmlSerializer(cleaner.getProperties());
					serializer.writeToStream(root, temp);
					// set the new contentLength
					contentLength = temp.toByteArray().length;
					newContentLength = "Content-Length: " + contentLength;
					// write the Content-Length header
					osLocalApplication.write(newContentLength.getBytes());
					osLocalApplication.write(0x0D);
					osLocalApplication.write(0x0A);
					// end the http header
					osLocalApplication.write(0x0D);
					osLocalApplication.write(0x0A);
					// write the http payload (with modified html) to the local application (e.g. browser)
					serializer.writeToStream(root, osLocalApplication);
				}else{
					// write the Content-Length header
					newContentLength = "Content-Length: " + contentLength;
					osLocalApplication.write(newContentLength.getBytes());
					osLocalApplication.write(0x0D);
					osLocalApplication.write(0x0A);
					// end the http header
					osLocalApplication.write(0x0D);
					osLocalApplication.write(0x0A);
					// Content-Type is not html (could be css, img, video, etc.)
					// no need to parse the http response payload. Return it as is.
					if(chunked){
						line = InternetUtil.readLine(isLocalApplication);
						//System.out.println("InternetUtil chunked line " + line);
						int lineLength = Integer.decode("0x" + line);
						//System.out.println("InterneInternetUtiltService chunked lineLength " + lineLength);
						for(int i = 0; i < line.length(); i++){
							osLocalApplication.write(line.charAt(i));
						}
						osLocalApplication.write(0x0D); // CR
						osLocalApplication.write(0x0A); // LF
						while( ! line.equals("0") ){
							for(int i = 0; i < lineLength; i++){
								int temp = isLocalApplication.read();
								//System.out.print((char)temp);
								osLocalApplication.write(temp);
							}
							//System.out.println();
							//System.out.println("InternetService chunked buf " + new String(buf));
							osLocalApplication.write(isLocalApplication.read()); // (char)0x0D
							osLocalApplication.write(isLocalApplication.read()); // (char)0x0A

							line = InternetUtil.readLine(isLocalApplication);
							//System.out.println("InternetService chunked line " + line);
							lineLength = Integer.decode("0x" + line);
							//System.out.println("InternetService chunked lineLength " + lineLength);
							for(int i = 0; i < line.length(); i++){
								osLocalApplication.write(line.charAt(i));
							}
							osLocalApplication.write(0x0D); // (char)0x0D
							osLocalApplication.write(0x0A); // (char)0x0A
						}
						osLocalApplication.write(0x0D); // (char)0x0D
						osLocalApplication.write(0x0A); // (char)0x0A
						osLocalApplication.flush();
					}else{
						int readByte;
						int totalRead = 0;
						byte[] buffer = new byte[E2EComm.DEFAULT_BUFFERSIZE];
						BufferedInputStream bis = new BufferedInputStream(pis, E2EComm.DEFAULT_BUFFERSIZE);
						while(totalRead < contentLength && (readByte = bis.read(buffer, 0, buffer.length)) != -1){
							osLocalApplication.write(buffer, 0, readByte);
							osLocalApplication.flush();
							totalRead += readByte;
						}
						//System.out.println("\tHTTPProxyClient total read: " + totalRead + " (of " + contentLength +")");
					}
				}

				responseTotalSize += newContentLength.getBytes().length + 4 + contentLength; // 4 is the double CR LF

				long stopTime = System.currentTimeMillis();
				long elapsed = stopTime - startTime;
				float speed = (responseTotalSize/1024)/(elapsed/1000); // KB/s
				String unit = "KB/s";
				if(speed < 1){
					speed *= 1024;
					unit = "B/s";
				}else if(speed > 1000){
					speed /= 1024;
					unit = "MB/s";
				}
				if(speed < Float.POSITIVE_INFINITY)
					System.out.println("\tHTTPProxyClient downloading speed: " + speed + " " + unit);

				// END HTTP RESPONSE PARSING

				//System.out.println("HTTPProxyClient: finished");
			}
			catch(Exception e){
				System.out.println("HTTPProxyClient: " + e.getMessage());
				//e.printStackTrace();
			}finally{
				try {
					sToTheLocalApplication.close();
				} catch (IOException e) {}
			}
		} // run
		
		private String[] findShortestPathTo(int httpProxyServerNodeId) {
			Vector<ResolverPath> resolved = Resolver.getInstance(false).resolveBlocking(httpProxyServerNodeId, 10000); // TODO tune timeout
			if(resolved == null){
				return null;
			}
			int minHops = Integer.MAX_VALUE;
			String[] shortestPath = null;
			for(int i = 0; i < resolved.size(); i++){
				int hops = resolved.get(i).getPath().length;
				if(hops < minHops){
					minHops = hops;
					shortestPath = resolved.get(i).getPath();
				}
			}
			return shortestPath;
		}
	}

}
