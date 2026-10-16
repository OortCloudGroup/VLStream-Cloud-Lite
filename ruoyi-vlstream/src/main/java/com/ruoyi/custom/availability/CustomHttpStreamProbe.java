package com.ruoyi.custom.availability;

import org.springframework.stereotype.Component;
import com.ruoyi.common.deviceavailability.SocketDeadline;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import static com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure;
import static com.ruoyi.custom.availability.CustomStreamAddressPolicy.remaining;

/** Bounded, pinned HTTP transport with no cookie jar, platform headers, automatic redirects or decompression. */
@Component
public class CustomHttpStreamProbe {
    private static final int LIMIT = 131072;
    private final CustomStreamAddressPolicy policy;
    protected SSLSocketFactory tlsFactory(){return (SSLSocketFactory)SSLSocketFactory.getDefault();}
    public CustomHttpStreamProbe(CustomStreamAddressPolicy policy) { this.policy = policy; }
    public String check(URI uri, long deadline) {
        Response first = fetch(uri, deadline, null);
        String text = new String(first.body, StandardCharsets.UTF_8);
        if (text.trim().startsWith("#EXTM3U")) return hls(first,deadline,0);
        if (flv(first.body)) return "AVAILABLE";
        if (CustomVideoContent.mp4(first.body)) return "AVAILABLE";
        if (hasBox(first.body,"ftyp")) return "UNSUPPORTED_VIDEO_METADATA";
        return "UNSUPPORTED_CONTENT";
    }
    private String hls(Response response, long deadline, int level) {
        if (level>1) return "UNSUPPORTED_CONTENT";
        String text = new String(response.body,StandardCharsets.UTF_8);
        for(String line:text.split("\\r?\\n"))if(line.startsWith("#EXT-X-KEY:")&&!line.matches("#EXT-X-KEY:METHOD=NONE\\s*"))return "UNSUPPORTED_ENCRYPTED";
        String[] lines = text.split("\\r?\\n"); String target = null, init = null; boolean master = text.contains("#EXT-X-STREAM-INF:");
        for (String line : lines) {
            line=line.trim();
            if (line.startsWith("#EXT-X-MAP:")) { Matcher match = Pattern.compile("URI=\"([^\"]+)\"").matcher(line); if(match.find()) init=match.group(1); }
            if (!line.isEmpty() && !line.startsWith("#")) { target=line; if(master) break; }
        }
        if (target==null) return "STREAM_UNAVAILABLE";
        Response child=fetch(response.uri.resolve(target),deadline,null,response.boundary);
        if(master) return new String(child.body,StandardCharsets.UTF_8).trim().startsWith("#EXTM3U") ? hls(child,deadline,level+1) : "STREAM_UNAVAILABLE";
        if (transportVideo(child.body)) return "AVAILABLE";
        if(CustomVideoContent.tsState(child.body)==1)return "UNSUPPORTED_VIDEO_METADATA";
        if (init!=null){byte[] metadata=fetch(response.uri.resolve(init),deadline,null,response.boundary).body;
            if(CustomVideoContent.fragmented(metadata,child.body))return "AVAILABLE";
            if(CustomVideoContent.videoTrack(metadata)!=null)return "UNSUPPORTED_VIDEO_METADATA";
        }
        return "NO_VIDEO_CONTENT";
    }
    Response fetch(URI initial,long deadline,String range) {
        return fetch(initial,deadline,range,policy.pin(initial,deadline));
    }
    private Response fetch(URI initial,long deadline,String range,InetAddress boundary) {
        URI uri=initial;
        for(int redirect=0;redirect<3;redirect++) {
            String scheme=uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)) || uri.getUserInfo()!=null || uri.getFragment()!=null) throw new ProbeFailure("ADDRESS_BLOCKED");
            InetAddress address=policy.pin(uri,deadline);
            // A public URL cannot enter a camera LAN through redirects/manifests; a camera LAN
            // source cannot scan other LAN hosts. Same host/changed port remains supported.
            boolean rootPrivate=privateAddress(boundary), nextPrivate=privateAddress(address);
            if(rootPrivate!=nextPrivate || rootPrivate&&!boundary.equals(address))throw new ProbeFailure("ADDRESS_BLOCKED");
            int port=uri.getPort()<0?("https".equalsIgnoreCase(scheme)?443:80):uri.getPort();
            Socket raw=new Socket();
            try (SocketDeadline guard=new SocketDeadline(raw,deadline)) {
                raw.connect(new InetSocketAddress(address,port),remaining(deadline)); raw.setSoTimeout(remaining(deadline));
                Socket connected=raw;
                if ("https".equalsIgnoreCase(scheme)) {
                    SSLSocket tls=(SSLSocket)tlsFactory().createSocket(raw,uri.getHost(),port,true);
                    SSLParameters settings=tls.getSSLParameters(); settings.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(settings);
                    tls.setSoTimeout(remaining(deadline)); tls.startHandshake(); connected=tls;
                }
                try(Socket socket=connected) {
                    String path=uri.getRawPath()==null||uri.getRawPath().isEmpty()?"/":uri.getRawPath();
                    if(uri.getRawQuery()!=null) path+="?"+uri.getRawQuery();
                    String host=uri.getHost(); if(host.contains(":")&&!host.startsWith("[")) host="["+host+"]";
                    if(uri.getPort()>=0)host+=":"+port;
                    socket.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: "+host+"\r\nConnection: close\r\nAccept-Encoding: identity\r\nUser-Agent: VLStream-source-probe\r\nRange: "+(range==null?"bytes=0-131071":range)+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush(); InputStream input=new BufferedInputStream(socket.getInputStream());
                    String status=line(input,socket,deadline); int code=Integer.parseInt(status.split(" ")[1]);
                    Map<String,String> headers=new HashMap<>(); int headerBytes=status.length();
                    for(String header;(header=line(input,socket,deadline)).length()>0;) {
                        headerBytes+=header.length(); if(headerBytes>16384)throw new ProbeFailure("STREAM_UNAVAILABLE");
                        int separator=header.indexOf(':'); if(separator>0)headers.put(header.substring(0,separator).toLowerCase(Locale.ROOT),header.substring(separator+1).trim());
                    }
                    if(code>=300&&code<400) {
                        String next=headers.get("location"); if(next==null)throw new ProbeFailure("STREAM_UNAVAILABLE");
                        URI destination=uri.resolve(next); if("https".equalsIgnoreCase(uri.getScheme())&&"http".equalsIgnoreCase(destination.getScheme()))throw new ProbeFailure("ADDRESS_BLOCKED");
                        uri=destination; continue;
                    }
                    if(code==401||code==403)throw new ProbeFailure("AUTHENTICATION_FAILED");
                    if(code!=200&&code!=206)throw new ProbeFailure("STREAM_UNAVAILABLE");
                    if(headers.containsKey("content-encoding")&&!"identity".equalsIgnoreCase(headers.get("content-encoding")))throw new ProbeFailure("UNSUPPORTED_CONTENT");
                    ByteArrayOutputStream body=new ByteArrayOutputStream();
                    try {
                        if("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                            while(body.size()<LIMIT) {
                                String length=line(input,socket,deadline).split(";",2)[0]; int chunk=Integer.parseInt(length.trim(),16);
                                if(chunk<0)throw new ProbeFailure("STREAM_UNAVAILABLE"); if(chunk==0)break;
                                read(input,socket,deadline,body,Math.min(chunk,LIMIT-body.size()));
                                if(body.size()>=LIMIT||flv(body.toByteArray()))break;
                                if(!line(input,socket,deadline).isEmpty())throw new ProbeFailure("STREAM_UNAVAILABLE");
                            }
                        } else {
                            long length=headers.containsKey("content-length")?Long.parseLong(headers.get("content-length")):LIMIT;
                            if(length<0)throw new ProbeFailure("STREAM_UNAVAILABLE");
                            try{read(input,socket,deadline,body,(int)Math.min(LIMIT,length));}catch(EOFException eof){if(headers.containsKey("content-length"))throw new ProbeFailure("STREAM_UNAVAILABLE");}
                        }
                    } catch(EOFException incomplete){throw new ProbeFailure(System.nanoTime()>=deadline?"STREAM_TIMEOUT":"STREAM_UNAVAILABLE");}
                    catch(SocketTimeoutException stalled) { throw new ProbeFailure("STREAM_TIMEOUT"); }
                    catch(IOException aborted){if(System.nanoTime()>=deadline)throw new ProbeFailure("STREAM_TIMEOUT");throw aborted;}
                    catch(ProbeFailure failed){if("UNREACHABLE".equals(failed.reason))throw new ProbeFailure("STREAM_TIMEOUT");throw failed;}
                    return new Response(uri,headers,body.toByteArray(),boundary);
                }
            } catch(ProbeFailure failure) { throw failure; }
            catch(SSLException untrusted) { throw new ProbeFailure(System.nanoTime()>=deadline?"UNREACHABLE":"TLS_VALIDATION_FAILED"); }
            catch(SocketTimeoutException timeout) { throw new ProbeFailure("UNREACHABLE"); }
            catch(IOException unavailable) { throw new ProbeFailure("UNREACHABLE"); }
            catch(RuntimeException malformed) { throw new ProbeFailure("STREAM_UNAVAILABLE"); }
            finally { try{raw.close();}catch(IOException ignored){} }
        }
        throw new ProbeFailure("REDIRECT_LIMIT");
    }
    static String line(InputStream input,Socket socket,long deadline)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); int value;
        while(true) { socket.setSoTimeout(remaining(deadline)); value=input.read(); if(value<0)throw new EOFException();
            if(value=='\n')break; if(bytes.size()>=8192)throw new ProbeFailure("STREAM_UNAVAILABLE"); if(value!='\r')bytes.write(value); }
        return new String(bytes.toByteArray(),StandardCharsets.ISO_8859_1);
    }
    private static void read(InputStream input,Socket socket,long deadline,ByteArrayOutputStream output,int length)throws IOException {
        byte[] buffer=new byte[4096]; while(length>0) { socket.setSoTimeout(remaining(deadline));int count=input.read(buffer,0,Math.min(buffer.length,length));
            if(count<0)throw new EOFException(); output.write(buffer,0,count); length-=count; if(flv(output.toByteArray()))break; }
    }
    static boolean flv(byte[] bytes) {
        return flv(bytes,0);
    }
    static boolean flv(byte[] bytes,int priorCodec) {
        if(bytes.length<13||bytes[0]!='F'||bytes[1]!='L'||bytes[2]!='V'||bytes[3]!=1||(bytes[4]&1)==0)return false;
        int header=integer(bytes,5);if(header<9||header>bytes.length-4||integer(bytes,header)!=0)return false;
        int offset=header+4,configuredCodec=priorCodec;
        while(offset+15<=bytes.length){int size=((bytes[offset+1]&255)<<16)|((bytes[offset+2]&255)<<8)|(bytes[offset+3]&255);
            if(size<1||offset+11L+size+4>bytes.length||integer(bytes,offset+11+size)!=11+size)return false;
            if((bytes[offset]&31)==9){byte[] packet=Arrays.copyOfRange(bytes,offset+11,offset+11+size);
                if(CustomRtmpStreamProbe.videoConfig(packet))configuredCodec=packet[0]&15;
                if(configuredCodec==(packet[0]&15)&&CustomRtmpStreamProbe.videoPacket(packet))return true;
            }offset+=11+size+4;
        }return false;
    }
    static int integer(byte[] bytes,int index) {return ((bytes[index]&255)<<24)|((bytes[index+1]&255)<<16)|((bytes[index+2]&255)<<8)|(bytes[index+3]&255);}
    static boolean hasBox(byte[] bytes,String type) {return CustomVideoContent.root(bytes,type)!=null;}
    static boolean mp4Video(byte[] bytes) {return CustomVideoContent.videoTrack(bytes)!=null;}
    static boolean transportVideo(byte[] bytes) {return CustomVideoContent.ts(bytes);}
    private static boolean privateAddress(InetAddress address){return address.isSiteLocalAddress()||CustomStreamAddressPolicy.inNetwork(address,"fc00::/7");}
    static class Response { final URI uri; final Map<String,String> headers; final byte[] body; final InetAddress boundary;
        Response(URI uri,Map<String,String> headers,byte[] body,InetAddress boundary){this.uri=uri;this.headers=headers;this.body=body;this.boundary=boundary;} }
}
