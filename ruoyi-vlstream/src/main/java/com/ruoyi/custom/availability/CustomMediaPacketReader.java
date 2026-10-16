package com.ruoyi.custom.availability;

import com.alibaba.fastjson2.*;
import com.ruoyi.wvp.media.bean.MediaServer;
import com.ruoyi.wvp.media.service.IMediaServerService;
import org.springframework.stereotype.Component;
import com.ruoyi.common.deviceavailability.SocketDeadline;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static com.ruoyi.custom.availability.CustomStreamAddressPolicy.remaining;

/** Trusted configured ZLM only. A ready track alone is not evidence of current video reception. */
@Component
public class CustomMediaPacketReader {
    private final IMediaServerService media;
    public CustomMediaPacketReader(IMediaServerService media){this.media=media;}
    public boolean receiving(CustomMediaBindings.Binding binding,long deadline) {
        MediaServer server=media.getOne(binding.serverId);if(server==null||!server.isStatus())return false;
        try {JSONObject firstSample=sample(server,binding,deadline);long first=videoFrames(firstSample);if(first<0||!originMatches(firstSample,binding))return false;
            Thread.sleep(Math.min(250,remaining(deadline)));JSONObject secondSample=sample(server,binding,deadline);long second=videoFrames(secondSample);
            if(!originMatches(secondSample,binding))return false;
            return second>first;
        } catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();return false;}
    }
    private boolean originMatches(JSONObject sample,CustomMediaBindings.Binding binding){return binding.originHash==null||sample!=null&&binding.originHash.equals(CustomMediaBindings.addressHash(sample.getString("originUrl")));}
    protected JSONObject sample(MediaServer server,CustomMediaBindings.Binding binding,long deadline)throws Exception {
        String host=server.getIp();
        // Registered media server addresses are operator-owned numeric IPs, never source URLs.
        if(host==null||!host.matches("[0-9a-fA-F:.]+"))return null;
        String body="secret="+URLEncoder.encode(server.getSecret(),"UTF-8")+"&schema=rtsp&vhost=__defaultVhost__&app="+URLEncoder.encode(binding.app,"UTF-8")+"&stream="+URLEncoder.encode(binding.stream,"UTF-8");
        byte[] request=body.getBytes(StandardCharsets.UTF_8);
        try(Socket socket=new Socket();SocketDeadline guard=new SocketDeadline(socket,deadline)){
            socket.connect(new InetSocketAddress(InetAddress.getByName(host),server.getHttpPort()),remaining(deadline));
            String authority=host.contains(":")?"["+host+"]":host;
            socket.getOutputStream().write(("POST /index/api/getMediaInfo HTTP/1.1\r\nHost: "+authority+":"+server.getHttpPort()+"\r\nConnection: close\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: "+request.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(request);socket.getOutputStream().flush();
            InputStream input=new BufferedInputStream(socket.getInputStream());String status=CustomHttpStreamProbe.line(input,socket,deadline);
            String[] statusParts=status.split(" ",3);
            if(statusParts.length<2||!("HTTP/1.1".equals(statusParts[0])||"HTTP/1.0".equals(statusParts[0]))||!"200".equals(statusParts[1]))return null;
            java.util.Map<String,String> headers=new java.util.HashMap<>();int headerSize=status.length();
            for(String line;!(line=CustomHttpStreamProbe.line(input,socket,deadline)).isEmpty();){headerSize+=line.length();if(headerSize>16384)return null;int colon=line.indexOf(':');if(colon>0)headers.put(line.substring(0,colon).toLowerCase(java.util.Locale.ROOT),line.substring(colon+1).trim());}
            try(ByteArrayOutputStream output=new ByteArrayOutputStream()){
                if("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))){while(true){int length=Integer.parseInt(CustomHttpStreamProbe.line(input,socket,deadline).split(";",2)[0],16);if(length==0)break;if(length<0||length>65536-output.size())return null;read(input,socket,deadline,output,length);if(!CustomHttpStreamProbe.line(input,socket,deadline).isEmpty())return null;}}
                else {int length=Integer.parseInt(headers.getOrDefault("content-length","65536"));if(length<0||length>65536)return null;try{read(input,socket,deadline,output,length);}catch(EOFException eof){if(headers.containsKey("content-length"))throw eof;}}
                return JSON.parseObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
            }
        }
    }
    private static void read(InputStream input,Socket socket,long deadline,ByteArrayOutputStream output,int length)throws Exception{
        byte[] buffer=new byte[4096];while(length>0){socket.setSoTimeout(remaining(deadline));int count=input.read(buffer,0,Math.min(length,buffer.length));if(count<0)throw new EOFException();output.write(buffer,0,count);length-=count;}
    }
    static long videoFrames(JSONObject media) {
        if(media==null||media.getIntValue("code")!=0||!media.containsKey("code"))return -1;
        JSONArray tracks=media.getJSONArray("tracks");if(tracks==null)return -1;
        for(int i=0;i<tracks.size();i++){JSONObject track=tracks.getJSONObject(i);
            if(track.containsKey("codec_type")&&track.getIntValue("codec_type")==0&&Boolean.TRUE.equals(track.getBoolean("ready"))&&track.containsKey("frames"))return track.getLongValue("frames");}
        return -1;
    }
}
