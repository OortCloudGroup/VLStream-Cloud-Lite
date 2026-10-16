package com.ruoyi.custom.availability;

import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.ruoyi.common.deviceavailability.SocketDeadline;
import static com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure;
import static com.ruoyi.custom.availability.CustomStreamAddressPolicy.remaining;

/** A bounded RTMP play subscriber: actual video NAL required; socket close releases the subscriber. */
@Component
public class CustomRtmpStreamProbe {
    private final CustomStreamAddressPolicy policy;
    public CustomRtmpStreamProbe(CustomStreamAddressPolicy policy) { this.policy=policy; }
    public String check(URI uri,long deadline) {
        if(uri.getUserInfo()!=null)return "AUTHENTICATION_UNSUPPORTED"; // Vendor challenge authentication is not guessed.
        String path=uri.getRawPath()==null?"":uri.getRawPath(); int separator=path.indexOf('/',1);
        if(separator<2||separator==path.length()-1)return "CONFIGURATION_ERROR";
        String app=path.substring(1,separator),stream=path.substring(separator+1);
        if(uri.getRawQuery()!=null)stream+="?"+uri.getRawQuery();
        boolean playing=false;int configuredCodec=0;
        try(Socket socket=new Socket();SocketDeadline guard=new SocketDeadline(socket,deadline)) {
            socket.connect(new InetSocketAddress(policy.pin(uri,deadline),uri.getPort()<0?1935:uri.getPort()),remaining(deadline));
            Wire wire=new Wire(socket,deadline,guard); byte[] handshake=new byte[1537];handshake[0]=3;
            new java.security.SecureRandom().nextBytes(handshake);handshake[0]=3;Arrays.fill(handshake,1,9,(byte)0);
            socket.getOutputStream().write(handshake);socket.getOutputStream().flush();byte[] server=wire.bytes(3073);
            if(server[0]!=3)return "UNSUPPORTED";
            socket.getOutputStream().write(server,1,1536);socket.getOutputStream().flush();
            Map<String,Object> options=new LinkedHashMap<>();options.put("app",app);options.put("tcUrl","rtmp://"+uri.getRawAuthority()+"/"+app);
            options.put("flashVer","FMLE/3.0");options.put("fpad",false);options.put("capabilities",15d);
            options.put("audioCodecs",0d);options.put("videoCodecs",128d);options.put("objectEncoding",0d);
            wire.send(20,0,amf("connect",1d,options)); boolean connected=false;
            while(!connected) { Message message=wire.next();if(message.type!=20&&message.type!=17)continue;
                List<Object> command=decode(message); String name=commandName(command);
                if("_error".equals(name)){String code=statusCode(command);return code!=null&&(code.contains("Auth")||code.contains("Denied")||code.contains("Rejected"))?"AUTHENTICATION_FAILED":"STREAM_UNAVAILABLE";}
                if("_result".equals(name)&&transaction(command,1))connected=true; }
            wire.send(20,0,amf("createStream",2d,null));int streamId=0;
            while(streamId==0) {Message message=wire.next();if(message.type!=20&&message.type!=17)continue;
                List<Object> command=decode(message);if("_error".equals(commandName(command)))return "STREAM_UNAVAILABLE";
                if("_result".equals(commandName(command))&&transaction(command,2)&&command.size()>3&&command.get(3) instanceof Number)
                    streamId=((Number)command.get(3)).intValue();
                if(streamId<0||streamId>65535)return "STREAM_UNAVAILABLE"; }
            wire.send(20,streamId,amf("play",0d,null,stream));playing=true;
            while(true) {
                Message message=wire.next();
                if(message.stream!=streamId)continue;
                if(message.type==9){if(message.data.length>0&&((message.data[0]&128)!=0||!Arrays.asList(7,12).contains(message.data[0]&15)))return "UNSUPPORTED_CODEC";
                    if(videoConfig(message.data))configuredCodec=message.data[0]&15;if(message.data.length>0&&configuredCodec==(message.data[0]&15)&&videoPacket(message.data))return "AVAILABLE";}
                if(message.type==22) {byte[] flv=new byte[13+message.data.length];flv[0]='F';flv[1]='L';flv[2]='V';flv[3]=1;flv[4]=1;flv[8]=9;
                    System.arraycopy(message.data,0,flv,13,message.data.length);if(CustomHttpStreamProbe.flv(flv,configuredCodec))return "AVAILABLE";return "UNSUPPORTED_AGGREGATE"; }
                if(message.type==20||message.type==17) {
                    String code=statusCode(decode(message));
                    if(code!=null&&(code.contains("Unauthorized")||code.contains("Denied")||code.contains("Auth")))return "AUTHENTICATION_FAILED";
                    if(code!=null&&(code.contains("StreamNotFound")||code.contains("Failed")||code.contains("Stop")||code.contains("Unpublish")))return "STREAM_UNAVAILABLE";
                }
            }
        } catch(ProbeFailure failure) {if(failure.reason.startsWith("UNSUPPORTED"))return failure.reason;if(playing&&"UNREACHABLE".equals(failure.reason))return "STREAM_TIMEOUT";throw failure;}
        catch(SocketTimeoutException timeout){return playing?"STREAM_TIMEOUT":"UNREACHABLE";}
        catch(IOException unavailable){return playing?(System.nanoTime()>=deadline?"STREAM_TIMEOUT":"STREAM_UNAVAILABLE"):"UNREACHABLE";}
        catch(RuntimeException malformed){return "STREAM_UNAVAILABLE";}
    }
    static boolean videoPacket(byte[] bytes) {
        if(bytes.length<6)return false;int codec=bytes[0]&15;
        if(codec==7||codec==12) {
            if(bytes[1]!=1||bytes.length<10)return false;int length=CustomHttpStreamProbe.integer(bytes,5);
            if(length<2||length>bytes.length-9||(bytes[9]&128)!=0)return false;int nal=codec==7?(bytes[9]&31):((bytes[9]>>1)&63);
            return codec==7?nal>=1&&nal<=5:nal<=21&&(bytes[10]&7)!=0;
        }
        return false; // Legacy/enhanced codecs require a separate validated configuration parser.
    }
    static boolean videoConfig(byte[] packet) {
        if(packet.length<12||packet[1]!=0||packet[5]!=1)return false;
        int codec=packet[0]&15;
        try{
            if(codec==7){if((packet[9]&3)!=3)return false;int at=11,count=packet[10]&31;if(count<1)return false;
                for(int i=0;i<count;i++){int length=((packet[at]&255)<<8)|(packet[at+1]&255);at+=2;if(length<4||at+length>packet.length||(packet[at]&31)!=7)return false;at+=length;}
                count=packet[at++]&255;if(count<1)return false;for(int i=0;i<count;i++){int length=((packet[at]&255)<<8)|(packet[at+1]&255);at+=2;if(length<2||at+length>packet.length||(packet[at]&31)!=8)return false;at+=length;}return true;
            }
            if(codec==12&&packet.length>=28&&(packet[26]&3)==3){int at=28,count=packet[27]&255;Set<Integer> types=new HashSet<>();
                for(int i=0;i<count;i++){int type=packet[at++]&63,n=((packet[at]&255)<<8)|(packet[at+1]&255);at+=2;for(int j=0;j<n;j++){int length=((packet[at]&255)<<8)|(packet[at+1]&255);at+=2;if(length<3||at+length>packet.length||((packet[at]>>1)&63)!=type)return false;at+=length;}types.add(type);}return types.containsAll(Arrays.asList(32,33,34));
            }
        }catch(IndexOutOfBoundsException invalid){return false;}return false;
    }
    private static String commandName(List<Object> values){return values.isEmpty()?"":String.valueOf(values.get(0));}
    private static boolean transaction(List<Object> values,int id){return values.size()>1&&values.get(1) instanceof Number&&((Number)values.get(1)).intValue()==id;}
    private static String statusCode(List<Object> values){for(Object value:values)if(value instanceof Map&&((Map<?,?>)value).get("code") instanceof String)return (String)((Map<?,?>)value).get("code");return null;}
    static byte[] amf(Object... values)throws IOException {ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);for(Object value:values)writeValue(out,value);return bytes.toByteArray();}
    private static void writeValue(DataOutputStream out,Object value)throws IOException {
        if(value==null){out.writeByte(5);return;} if(value instanceof Number){out.writeByte(0);out.writeDouble(((Number)value).doubleValue());return;}
        if(value instanceof Boolean){out.writeByte(1);out.writeBoolean((Boolean)value);return;}
        if(value instanceof Map){out.writeByte(3);for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()){writeText(out,String.valueOf(entry.getKey()));writeValue(out,entry.getValue());}out.writeShort(0);out.writeByte(9);return;}
        out.writeByte(2);writeText(out,String.valueOf(value));
    }
    private static void writeText(DataOutputStream out,String text)throws IOException {byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length>8192)throw new IOException();out.writeShort(bytes.length);out.write(bytes);}
    private static List<Object> decode(Message message)throws IOException {
        ByteArrayInputStream bytes=new ByteArrayInputStream(message.data);DataInputStream in=new DataInputStream(bytes);if(message.type==17&&in.readUnsignedByte()!=0)throw new IOException();
        List<Object> values=new ArrayList<>();while(bytes.available()>0){if(values.size()>32)throw new IOException();values.add(readValue(in,0));}return values;
    }
    private static Object readValue(DataInputStream in,int depth)throws IOException {
        if(depth>6)throw new IOException();int type=in.readUnsignedByte();
        if(type==0)return in.readDouble();if(type==1)return in.readBoolean();if(type==2)return text(in,in.readUnsignedShort());if(type==5||type==6)return null;
        if(type==12){int length=in.readInt();return text(in,length);}
        if(type==3||type==8){if(type==8)in.readInt();Map<String,Object> values=new HashMap<>();for(int i=0;i<64;i++){int length=in.readUnsignedShort();if(length==0){if(in.readUnsignedByte()!=9)throw new IOException();return values;}String key=text(in,length);values.put(key,readValue(in,depth+1));}throw new IOException();}
        if(type==10){int count=in.readInt();if(count<0||count>64)throw new IOException();List<Object> values=new ArrayList<>();while(count-->0)values.add(readValue(in,depth+1));return values;}
        throw new IOException();
    }
    private static String text(DataInputStream in,int length)throws IOException{if(length<0||length>8192)throw new IOException();byte[] bytes=new byte[length];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
    static class Message {final int type,stream;final byte[] data;Message(int type,int stream,byte[] data){this.type=type;this.stream=stream;this.data=data;}}
    static class Chunk {int length,type,stream,read;boolean extended;byte[] bytes;}
    static class Wire implements AutoCloseable {
        final byte[] buffer=new byte[8192];int position,limit;
        final Socket socket;final InputStream input;final OutputStream output;final long deadline;final SocketDeadline guard;int size=128,total;
        final Map<Integer,Chunk> chunks=new HashMap<>();
        Wire(Socket socket,long deadline)throws IOException{this(socket,deadline,new SocketDeadline(socket,deadline));}
        Wire(Socket socket,long deadline,SocketDeadline guard)throws IOException{this.socket=socket;this.deadline=deadline;this.guard=guard;input=socket.getInputStream();output=socket.getOutputStream();}
        @Override public void close()throws IOException{guard.close();socket.close();}
        int octet()throws IOException {remaining(deadline);if(position==limit){socket.setSoTimeout(remaining(deadline));limit=input.read(buffer);position=0;if(limit<0)throw new EOFException();}if(++total>2097152)throw new ProbeFailure("UNSUPPORTED_CONTENT_LIMIT");return buffer[position++]&255;}
        byte[] bytes(int count)throws IOException {byte[] result=new byte[count];for(int i=0;i<count;i++)result[i]=(byte)octet();return result;}
        int uint24()throws IOException{return(octet()<<16)|(octet()<<8)|octet();}
        Message next()throws IOException {
            while(true) {
                int basic=octet(),format=basic>>6,id=basic&63;if(id==0)id=64+octet();else if(id==1)id=64+octet()+(octet()<<8);
                Chunk chunk=chunks.get(id);if(chunk==null){if(format!=0||chunks.size()>=16)throw new IOException();chunk=new Chunk();chunks.put(id,chunk);}
                if(format<3) {int timestamp=uint24();chunk.extended=timestamp==0xffffff;
                    if(format<2){chunk.length=uint24();chunk.type=octet();if(chunk.length>1048576)throw new ProbeFailure("UNSUPPORTED_CONTENT_LIMIT");}
                    if(format==0)chunk.stream=octet()|(octet()<<8)|(octet()<<16)|(octet()<<24);
                    if(chunk.read!=0)throw new IOException();
                }
                if(chunk.extended)bytes(4);
                if(chunk.bytes==null)chunk.bytes=new byte[chunk.length];
                int count=Math.min(size,chunk.length-chunk.read);byte[] part=bytes(count);System.arraycopy(part,0,chunk.bytes,chunk.read,count);chunk.read+=count;
                if(chunk.read==chunk.length) {Message message=new Message(chunk.type,chunk.stream,chunk.bytes);chunk.read=0;chunk.bytes=null;
                    if(message.type==1&&message.stream==0){if(message.data.length!=4)throw new IOException();size=CustomHttpStreamProbe.integer(message.data,0)&0x7fffffff;if(size<1||size>65536)throw new IOException();continue;}
                    if(message.type==4&&message.stream==0&&message.data.length>=6&&message.data[0]==0&&message.data[1]==6){byte[] pong=message.data.clone();pong[1]=7;send(4,0,pong);continue;}
                    return message;
                }
            }
        }
        void send(int type,int stream,byte[] data)throws IOException {
            for(int offset=0;offset<data.length;offset+=128){remaining(deadline);if(offset==0){output.write(3);output.write(new byte[3]);output.write(new byte[]{(byte)(data.length>>16),(byte)(data.length>>8),(byte)data.length,(byte)type});output.write(new byte[]{(byte)stream,(byte)(stream>>8),(byte)(stream>>16),(byte)(stream>>24)});}else output.write(0xc3);
                output.write(data,offset,Math.min(128,data.length-offset));}remaining(deadline);output.flush();
        }
    }
}
