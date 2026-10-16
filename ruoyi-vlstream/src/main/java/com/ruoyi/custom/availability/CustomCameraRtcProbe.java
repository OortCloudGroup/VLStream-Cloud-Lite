package com.ruoyi.custom.availability;

import com.alibaba.fastjson2.*;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.ruoyi.custom.availability.CustomStreamAddressPolicy.remaining;

/** Configured signaling gateway; query _create only. Never send __call, credentials or media negotiation. */
@Component
public class CustomCameraRtcProbe {
    private final String gateway;
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(2,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).build();
    public CustomCameraRtcProbe(@Value("${video.custom-availability.camera-rtc-signal-url:}") String gateway) {
        this.gateway=gateway.trim().replaceAll("/+$","");
        if(!this.gateway.isEmpty()) {URI uri=URI.create(this.gateway);
            if(!Arrays.asList("ws","wss").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
                throw new IllegalArgumentException("Invalid configured CameraRTC gateway");}
    }
    public boolean enabled(){return !gateway.isEmpty();}
    public String check(String target,long deadline) {
        if(!enabled())return "UNSUPPORTED_PRESENCE";
        if(target==null||!target.matches("[A-Za-z0-9_-]{1,100}"))return "CONFIGURATION_ERROR";
        String session="PCWEB-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        CountDownLatch response=new CountDownLatch(1),closed=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();AtomicBoolean opened=new AtomicBoolean();
        WebSocket socket=client.newWebSocket(new Request.Builder().url(gateway+"/wswebclient/"+UUID.randomUUID()).build(),new WebSocketListener(){
            private void complete(String value){if(result.compareAndSet(null,value))response.countDown();}
            @Override public void onOpen(WebSocket ws,Response handshake){opened.set(true);ws.send(message("__connectto",target,session));}
            @Override public void onMessage(WebSocket ws,String text){
                if(text.length()>65536){complete("STREAM_UNAVAILABLE");return;}
                try{JSONObject message=JSON.parseObject(text);JSONObject data=message.getJSONObject("data");
                    if(data==null||!session.equals(data.getString("sessionId")))return;
                    if("_create".equals(message.getString("eventName"))){String state=data.getString("state");
                        complete("online".equals(state)?"UNSUPPORTED_SIGNAL_ONLY":"sleep".equals(state)?"UNSUPPORTED_STANDBY":"offline".equals(state)?"UNREACHABLE":"STREAM_UNAVAILABLE");}
                    else if("_session_failed".equals(message.getString("eventName")))complete("STREAM_UNAVAILABLE");
                }catch(RuntimeException malformed){complete("STREAM_UNAVAILABLE");}
            }
            @Override public void onFailure(WebSocket ws,Throwable failure,Response handshake){complete(handshake!=null&&(handshake.code()==401||handshake.code()==403)?"AUTHENTICATION_FAILED":"UNREACHABLE");closed.countDown();}
            @Override public void onClosed(WebSocket ws,int code,String reason){closed.countDown();complete("STREAM_UNAVAILABLE");}
        });
        // Reserve bounded transport time for the queued disconnect/close frames to flush.
        try {if(!response.await(Math.max(1,remaining(deadline)-150),TimeUnit.MILLISECONDS))return opened.get()?"STREAM_TIMEOUT":"UNREACHABLE";return result.get();}
        catch(InterruptedException cancelled){Thread.currentThread().interrupt();return "MONITOR_UNAVAILABLE";}
        finally {
            if(opened.get())socket.send(message("__disconnect",target,session));socket.close(1000,"source probe complete");
            try{closed.await(Math.max(0,Math.min(150,(deadline-System.nanoTime())/1_000_000L)),TimeUnit.MILLISECONDS);}catch(InterruptedException cancelled){Thread.currentThread().interrupt();}
            socket.cancel();
        }
    }
    static String message(String event,String target,String session){JSONObject data=new JSONObject();data.put("to",target);data.put("sessionId",session);JSONObject message=new JSONObject();message.put("eventName",event);message.put("data",data);return message.toJSONString();}
    @PreDestroy public void close(){client.dispatcher().executorService().shutdownNow();client.connectionPool().evictAll();}
}
