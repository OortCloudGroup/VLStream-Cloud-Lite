package com.ruoyi.custom.availability;

import com.ruoyi.common.deviceavailability.RtspDescribeProbe;
import org.springframework.stereotype.Component;
import java.net.*;
import static com.ruoyi.custom.availability.CustomStreamSpec.ProbeFailure;
import static com.ruoyi.custom.availability.CustomStreamAddressPolicy.remaining;

@Component
public class CustomStreamProbe {
    private final CustomStreamAddressPolicy policy;
    private final CustomHttpStreamProbe http;
    private final CustomRtmpStreamProbe rtmp;
    private final CustomMediaBindings bindings;
    private final CustomMediaPacketReader packets;
    @org.springframework.beans.factory.annotation.Autowired private CustomCameraRtcProbe camera;
    public CustomStreamProbe(CustomStreamAddressPolicy policy,CustomHttpStreamProbe http,CustomRtmpStreamProbe rtmp,CustomMediaBindings bindings,CustomMediaPacketReader packets){
        this.policy=policy;this.http=http;this.rtmp=rtmp;this.bindings=bindings;this.packets=packets;
    }
    public Result check(CustomStreamSpec source,int timeout) {
        long deadline=System.nanoTime()+timeout*1_000_000L;
        try {
            for(CustomMediaBindings.Binding binding:bindings.matching(source))if(packets.receiving(binding,deadline))return new Result("AVAILABLE","CUSTOM_MEDIA_RECEIVING",true);
            if("CAMERA_RTC".equals(source.kind))return new Result(camera==null?"UNSUPPORTED_PRESENCE":camera.check(CustomStreamSpec.cameraTarget(source.device.getStreamUrl()),deadline),"CUSTOM_STREAM_PROBE",false);
            if((deadline-System.nanoTime())/1_000_000L<500)return new Result("MONITOR_UNAVAILABLE","CUSTOM_STREAM_PROBE",false);
            String result;
            if("RTSP".equals(source.kind)){InetAddress address=policy.pin(source.uri,deadline);result=new RtspDescribeProbe().check(source.uri.toASCIIString(),remaining(deadline),address);}
            else if("RTMP".equals(source.kind))result=rtmp.check(source.uri,deadline);
            else result=http.check(source.uri,deadline);
            return new Result(result,"CUSTOM_STREAM_PROBE",false);
        } catch(ProbeFailure failure){return new Result(failure.reason,"CUSTOM_STREAM_PROBE",false);}
        catch(RuntimeException unavailable){return new Result("MONITOR_UNAVAILABLE","CUSTOM_STREAM_PROBE",false);}
    }
    public java.util.Set<Long> consumeMediaChanges(){return bindings.consumeChanged();}
    public boolean supportsCameraPresence(){return camera!=null&&camera.enabled();}
    public void validateDirect(CustomStreamSpec source,int timeout){if(!"CAMERA_RTC".equals(source.kind))policy.pin(source.uri,System.nanoTime()+timeout*1_000_000L);}
    /** Media proxies connect to a prevalidated numeric address; stream credentials are source-specific. */
    public String proxyUrl(CustomStreamSpec source,int timeout) {
        InetAddress pin=policy.pin(source.uri,System.nanoTime()+timeout*1_000_000L);
        String host=pin.getHostAddress();if(host.contains(":"))host="["+host+"]";
        String authority=(source.uri.getRawUserInfo()==null?"":source.uri.getRawUserInfo()+"@")+host+(source.uri.getPort()<0?"":":"+source.uri.getPort());
        try{return new URI(source.uri.getScheme()+"://"+authority+source.uri.getRawPath()+(source.uri.getRawQuery()==null?"":"?"+source.uri.getRawQuery())).toASCIIString();}
        catch(URISyntaxException invalid){throw new ProbeFailure("CONFIGURATION_ERROR");}
    }
    public static final class Result {
        public final String reason,source;public final boolean receiving;
        public Result(String reason,String source,boolean receiving){this.reason=reason;this.source=source;this.receiving=receiving;}
    }
}
