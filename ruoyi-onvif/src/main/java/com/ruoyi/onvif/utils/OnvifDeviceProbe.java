package com.ruoyi.onvif.utils;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.FetchMainAndSubStreamUris;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Read actual ONVIF services and media profiles; identity descriptions may be unreported. */
public class OnvifDeviceProbe {
    static final String DEVICE = "http://www.onvif.org/ver10/device/wsdl";
    static final String MEDIA = "http://www.onvif.org/ver10/media/wsdl";
    static final String MEDIA2 = "http://www.onvif.org/ver20/media/wsdl";
    static final String SCHEMA = "http://www.onvif.org/ver10/schema";
    interface Transport { OnvifPtzClient.Response post(String url, String body, FetchMainAndSubStreamUrisBo bo, boolean digest); }
    private static final int MANAGEMENT_MAX_BYTES = 65536;
    private static final ScheduledThreadPoolExecutor DEADLINES = deadlines();
    private final Transport transport;
    private final int timeoutMillis;
    public OnvifDeviceProbe() { this(4000); }
    public OnvifDeviceProbe(int timeout) { this.transport = null; this.timeoutMillis = Math.max(1, timeout); }

    /** Authenticated, small management response; never reload profiles on a monitoring tick. */
    public String checkManagement(FetchMainAndSubStreamUrisBo bo) {
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            validate(bo);
            try { management(bo, false, deadline); }
            catch (ServiceException error) {
                if (!authenticationError(error)) throw error;
                management(bo, true, deadline);
            }
            remainingMillis(deadline);
            return "AVAILABLE";
        } catch (ServiceException error) {
            if (authenticationError(error)) return "AUTHENTICATION_FAILED";
            return error.getMessage().contains("连接") ? "UNREACHABLE" : "MANAGEMENT_UNAVAILABLE";
        }
    }
    private void management(FetchMainAndSubStreamUrisBo bo, boolean digest, long deadline) {
        remainingMillis(deadline);
        String url = base(bo)+"/onvif/device_service";
        String body = "<GetDeviceInformation xmlns=\""+DEVICE+"\"/>";
        OnvifPtzClient.Response response = transport == null
            ? post(url, body, bo, digest, remainingMillis(deadline), deadline)
            : transport.post(url, body, bo, digest);
        remainingMillis(deadline);
        OnvifPtzClient.checked(response, "GetDeviceInformation", DEVICE);
    }
    private static boolean authenticationError(ServiceException error) {
        String message = error.getMessage();
        return message != null && (message.contains("401") || message.contains("403")
            || message.contains("NotAuthorized") || message.contains("FailedAuthentication") || message.contains("认证"));
    }

    OnvifDeviceProbe(Transport transport) { this.transport = transport; this.timeoutMillis = 4000; }

    public FetchMainAndSubStreamUris read(FetchMainAndSubStreamUrisBo bo, boolean digest) {
        validate(bo);
        Document information = request(base(bo)+"/onvif/device_service", "GetDeviceInformation", DEVICE,
                "<GetDeviceInformation xmlns=\""+DEVICE+"\"/>", bo, digest);
        FetchMainAndSubStreamUris result = new FetchMainAndSubStreamUris();
        result.setIp(bo.getIp());
        Element info = response(information, "GetDeviceInformation", DEVICE);
        result.setFirm(text(info, "Manufacturer"));
        result.setModel(text(info, "Model"));
        result.setFirmwareVersion(text(info, "FirmwareVersion"));
        Service media = media(bo, digest);
        for (String token : profiles(media, bo, digest)) result.addStreamUri(token, stream(media, token, bo, digest));
        if (result.getStreamUris().isEmpty()) throw new ServiceException("设备未返回可用视频通道，请检查 ONVIF 配置与账号");
        return result;
    }
    public List<String> profiles(FetchMainAndSubStreamUrisBo bo) { validate(bo);return profiles(media(bo,false),bo,false); }
    public String stream(FetchMainAndSubStreamUrisBo bo, String token) { validate(bo);return stream(media(bo,false),token,bo,false); }

    private Service media(FetchMainAndSubStreamUrisBo bo, boolean digest) {
        Document caps = request(base(bo)+"/onvif/device_service", "GetCapabilities", DEVICE,
                "<GetCapabilities xmlns=\""+DEVICE+"\"><Category>All</Category></GetCapabilities>",bo,digest);
        NodeList services = caps.getElementsByTagNameNS(SCHEMA,"Media");
        if (services.getLength()>0) {
            String url = text((Element)services.item(0),"XAddr");
            if (url != null) return new Service(OnvifPtzClient.validateEndpoint(base(bo),url),MEDIA);
        }
        Document serviceList = request(base(bo)+"/onvif/device_service","GetServices",DEVICE,
                "<GetServices xmlns=\""+DEVICE+"\"><IncludeCapability>false</IncludeCapability></GetServices>",bo,digest);
        NodeList nodes = serviceList.getElementsByTagNameNS(DEVICE,"Service");
        for (String namespace : Arrays.asList(MEDIA,MEDIA2)) for (int i=0;i<nodes.getLength();i++) {
            Element node=(Element)nodes.item(i);
            if (namespace.equals(text(node,"Namespace")) && text(node,"XAddr")!=null)
                return new Service(OnvifPtzClient.validateEndpoint(base(bo),text(node,"XAddr")),namespace);
        }
        throw new ServiceException("设备未公布可用 ONVIF Media 服务");
    }
    private List<String> profiles(Service media, FetchMainAndSubStreamUrisBo bo, boolean digest) {
        Document doc=request(media.url,"GetProfiles",media.namespace,"<GetProfiles xmlns=\""+media.namespace+"\"/>",bo,digest);
        NodeList nodes=response(doc,"GetProfiles",media.namespace).getElementsByTagNameNS("*","Profiles");
        List<String> tokens=new ArrayList<>();
        for(int i=0;i<nodes.getLength();i++) {
            String token=((Element)nodes.item(i)).getAttribute("token").trim();
            if(!token.isEmpty()&&!tokens.contains(token))tokens.add(token);
        }
        if(tokens.isEmpty())throw new ServiceException("设备未返回可用视频通道，请检查 ONVIF 配置与账号");
        return tokens;
    }
    private String stream(Service media, String token, FetchMainAndSubStreamUrisBo bo, boolean digest) {
        if(token==null||token.trim().isEmpty())throw new ServiceException("缺少 ONVIF 视频通道 Token");
        String setup=MEDIA.equals(media.namespace)?"<StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></StreamSetup>":"<Protocol>RTSP</Protocol>";
        String body="<GetStreamUri xmlns=\""+media.namespace+"\" xmlns:tt=\""+SCHEMA+"\">"+setup+"<ProfileToken>"+OnvifPtzClient.escape(token)+"</ProfileToken></GetStreamUri>";
        Document doc=request(media.url,"GetStreamUri",media.namespace,body,bo,digest);
        String url=text(response(doc,"GetStreamUri",media.namespace),"Uri");
        return authenticatedUri(url,bo);
    }
    static String authenticatedUri(String address, FetchMainAndSubStreamUrisBo bo) {
        try {
            URI uri=new URI(address);
            if(!("rtsp".equalsIgnoreCase(uri.getScheme())||"rtsps".equalsIgnoreCase(uri.getScheme()))
                    ||uri.getHost()==null||!uri.getHost().equalsIgnoreCase(new URI(base(bo)).getHost()))throw new IllegalArgumentException();
            String authority=uri.getRawAuthority();
            if(authority.contains("@"))authority=authority.substring(authority.lastIndexOf('@')+1);
            String auth=new URI("rtsp",bo.getUsername()+":"+bo.getPassword(),"localhost",-1,null,null,null).getRawUserInfo();
            return new URI(uri.getScheme()+"://"+auth+"@"+authority+(uri.getRawPath()==null?"":uri.getRawPath())
                    +(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())).toASCIIString();
        }catch(Exception e){throw new ServiceException("设备返回无效或不属于本设备的 RTSP 播放地址");}
    }
    private Document request(String url,String operation,String namespace,String body,FetchMainAndSubStreamUrisBo bo,boolean digest) {
        return OnvifPtzClient.checked(transport == null ? post(url,body,bo,digest,timeoutMillis)
            : transport.post(url,body,bo,digest),operation,namespace);
    }
    static Element response(Document document,String operation,String namespace) {
        return (Element)document.getElementsByTagNameNS(namespace,operation+"Response").item(0);
    }
    static String text(Element parent,String name) {
        NodeList nodes=parent.getElementsByTagNameNS("*",name);
        if(nodes.getLength()==0)return null;
        String value=nodes.item(0).getTextContent().trim();return value.isEmpty()?null:value;
    }
    private static String base(FetchMainAndSubStreamUrisBo bo){return "http://"+bo.getIp();}
    static void validate(FetchMainAndSubStreamUrisBo bo) {
        if(bo==null||bo.getIp()==null||bo.getIp().trim().isEmpty()||bo.getUsername()==null||bo.getUsername().trim().isEmpty()
                ||bo.getPassword()==null||bo.getPassword().trim().isEmpty())throw new ServiceException("请输入完整 ONVIF 地址、用户名和密码");
    }
    private static OnvifPtzClient.Response post(String url,String body,FetchMainAndSubStreamUrisBo bo,boolean digest,int timeout) {
        return post(url, body, bo, digest, timeout, 0);
    }
    private static OnvifPtzClient.Response post(String url,String body,FetchMainAndSubStreamUrisBo bo,boolean digest,int timeout,long deadline) {
        try {
            URI uri=new URI(url);
            BasicCredentialsProvider credentials=new BasicCredentialsProvider();
            int port=uri.getPort()<0?("https".equalsIgnoreCase(uri.getScheme())?443:80):uri.getPort();
            if(digest)credentials.setCredentials(new AuthScope(uri.getHost(),port),new UsernamePasswordCredentials(bo.getUsername(),bo.getPassword()));
            RequestConfig config=RequestConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).setConnectionRequestTimeout(timeout)
                    .setTargetPreferredAuthSchemes(Arrays.asList("Digest")).setRedirectsEnabled(false).build();
            try(CloseableHttpClient client=HttpClients.custom().setDefaultCredentialsProvider(credentials).setDefaultRequestConfig(config).disableRedirectHandling().build()) {
                HttpPost post=new HttpPost(url);
                OnvifDevice device=new OnvifDevice();device.setUserName(bo.getUsername());device.setPassword(bo.getPassword());
                String soap=digest?"<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body>"+body+"</s:Body></s:Envelope>":OnvifPtzClient.envelope(device,body);
                post.setEntity(new StringEntity(soap,StandardCharsets.UTF_8));post.setHeader("Content-Type","application/soap+xml; charset=utf-8");
                // Abort the underlying connection, not just a waiting Future. This also interrupts
                // HTTP Digest retries and blocking header/body reads when a peer drip-feeds data.
                ScheduledFuture<?> abort = deadline == 0 ? null : DEADLINES.schedule(post::abort,
                    Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                try {
                    if (deadline != 0) remainingMillis(deadline);
                    try(CloseableHttpResponse response=client.execute(post)) {
                        String reply = deadline == 0
                            ? (response.getEntity()==null?"":EntityUtils.toString(response.getEntity(),StandardCharsets.UTF_8))
                            : boundedBody(response.getEntity(), post, deadline);
                        return new OnvifPtzClient.Response(response.getStatusLine().getStatusCode(), reply);
                    }
                } finally { if (abort != null) abort.cancel(false); }
            }
        }catch(ServiceException e){throw e;}
        catch(Exception e){throw new ServiceException("ONVIF 服务连接失败或超时");}
    }

    private static String boundedBody(HttpEntity entity, HttpPost post, long deadline) throws Exception {
        try {
            if (entity == null) return "";
            if (entity.getContentLength() > MANAGEMENT_MAX_BYTES)
                throw new ServiceException("ONVIF 管理响应超出大小限制");
            InputStream input = entity.getContent();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (;;) {
                remainingMillis(deadline);
                int count = input.read(buffer);
                remainingMillis(deadline);
                if (count < 0) return new String(output.toByteArray(), StandardCharsets.UTF_8);
                if (output.size() + count > MANAGEMENT_MAX_BYTES)
                    throw new ServiceException("ONVIF 管理响应超出大小限制");
                output.write(buffer, 0, count);
            }
        } finally {
            // Abort before response cleanup can attempt to drain a slow/oversized entity.
            // Every management request owns its client, so no connection reuse is lost.
            post.abort();
        }
    }

    private static int remainingMillis(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new ServiceException("ONVIF 服务连接失败或超时");
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos)));
    }

    private static ScheduledThreadPoolExecutor deadlines() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "onvif-probe-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
    private static class Service { final String url,namespace;Service(String url,String namespace){this.url=url;this.namespace=namespace;} }
}
