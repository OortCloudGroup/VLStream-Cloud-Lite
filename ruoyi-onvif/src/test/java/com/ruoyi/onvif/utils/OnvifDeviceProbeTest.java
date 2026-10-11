package com.ruoyi.onvif.utils;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.FetchMainAndSubStreamUris;
import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OnvifDeviceProbeTest {
    private FetchMainAndSubStreamUrisBo account() {
        FetchMainAndSubStreamUrisBo bo=new FetchMainAndSubStreamUrisBo();bo.setIp("192.168.1.13");bo.setUsername("admin");bo.setPassword("test-only");return bo;
    }
    private static String soap(String body){return "<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Body>"+body+"</s:Body></s:Envelope>";}
    private static String info(){return soap("<GetDeviceInformationResponse xmlns='"+OnvifDeviceProbe.DEVICE+"'><Manufacturer/><Model/><FirmwareVersion>V4.0</FirmwareVersion></GetDeviceInformationResponse>");}
    private static String caps(){return soap("<GetCapabilitiesResponse xmlns='"+OnvifDeviceProbe.DEVICE+"'><Capabilities xmlns:t='"+OnvifDeviceProbe.SCHEMA+"'><t:Media><t:XAddr>http://192.168.1.13/onvif/Media</t:XAddr></t:Media></Capabilities></GetCapabilitiesResponse>");}
    private static String profiles(String ns,boolean empty){return soap("<GetProfilesResponse xmlns='"+ns+"'>"+(empty?"":"<Profiles token='CH01'/><Profiles token='CH01_sub'/>")+"</GetProfilesResponse>");}
    private static String uri(String ns){return soap("<GetStreamUriResponse xmlns='"+ns+"' xmlns:t='"+OnvifDeviceProbe.SCHEMA+"'><MediaUri><t:Uri>rtsp://192.168.1.13:554/ch01.264</t:Uri></MediaUri></GetStreamUriResponse>");}
    private void rejected(Runnable action,String part){try{action.run();fail("Expected error");}catch(ServiceException e){assertTrue(e.getMessage(),e.getMessage().contains(part));}}
    @Test public void emptyIdentityDescriptionsDoNotHideUsableMedia1Channels() {
        List<String> urls=new ArrayList<>();
        OnvifDeviceProbe probe=new OnvifDeviceProbe((url,body,bo,digest)->{
            urls.add(url);String reply=body.contains("GetDeviceInformation")?info():body.contains("GetCapabilities")?caps():body.contains("GetProfiles")?profiles(OnvifDeviceProbe.MEDIA,false):uri(OnvifDeviceProbe.MEDIA);
            return new OnvifPtzClient.Response(200,reply);
        });
        FetchMainAndSubStreamUris result=probe.read(account(),false);
        assertNull(result.getFirm());assertNull(result.getModel());assertEquals("V4.0",result.getFirmwareVersion());
        assertEquals(2,result.getStreamUris().size());assertTrue(result.getStreamUris().containsKey("CH01_sub"));
        assertEquals("http://192.168.1.13/onvif/device_service",urls.get(0));
        assertEquals("http://192.168.1.13/onvif/Media",urls.get(2));
    }
    @Test public void aDeviceWithoutProfilesCannotPassAuthentication() {
        OnvifDeviceProbe probe=new OnvifDeviceProbe((url,body,bo,digest)->new OnvifPtzClient.Response(200,
                body.contains("GetDeviceInformation")?info():body.contains("GetCapabilities")?caps():profiles(OnvifDeviceProbe.MEDIA,true)));
        rejected(()->probe.read(account(),false),"未返回可用视频通道");
    }
    @Test public void soapAuthenticationFaultIsNotReturnedAsEmptySuccess() {
        OnvifDeviceProbe probe=new OnvifDeviceProbe((url,body,bo,digest)->new OnvifPtzClient.Response(200,
                soap("<s:Fault xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Reason><s:Text>NotAuthorized</s:Text></s:Reason></s:Fault>")));
        rejected(()->probe.read(account(),false),"NotAuthorized");
    }
    @Test public void advertisedExternalMediaEndpointIsRejected() {
        OnvifDeviceProbe probe=new OnvifDeviceProbe((url,body,bo,digest)->new OnvifPtzClient.Response(200,
                body.contains("GetDeviceInformation")?info():caps().replace("192.168.1.13","10.0.0.2")));
        rejected(()->probe.read(account(),false),"服务地址");
    }
    @Test public void media2ServicesAreUsedWhenMedia1IsNotAdvertised() {
        List<String> bodies=new ArrayList<>();
        OnvifDeviceProbe probe=new OnvifDeviceProbe((url,body,bo,digest)->{
            bodies.add(body);String reply;
            if(body.contains("GetDeviceInformation"))reply=info();
            else if(body.contains("GetCapabilities"))reply=soap("<GetCapabilitiesResponse xmlns='"+OnvifDeviceProbe.DEVICE+"'/>");
            else if(body.contains("GetServices"))reply=soap("<GetServicesResponse xmlns='"+OnvifDeviceProbe.DEVICE+"'><Service><Namespace>"+OnvifDeviceProbe.MEDIA2+"</Namespace><XAddr>http://192.168.1.13/onvif/Media20</XAddr></Service></GetServicesResponse>");
            else if(body.contains("GetProfiles"))reply=profiles(OnvifDeviceProbe.MEDIA2,false);
            else reply=uri(OnvifDeviceProbe.MEDIA2);
            return new OnvifPtzClient.Response(200,reply);
        });
        assertEquals(2,probe.read(account(),false).getStreamUris().size());
        assertTrue(bodies.stream().anyMatch(body->body.contains("<Protocol>RTSP</Protocol>")));
    }
    @Test public void selectedHttpDigestModeIsPreservedForEveryRequest() {
        List<Boolean> modes=new ArrayList<>();
        OnvifDeviceProbe probe=new OnvifDeviceProbe((url,body,bo,digest)->{
            modes.add(digest);return new OnvifPtzClient.Response(200,body.contains("GetDeviceInformation")?info():body.contains("GetCapabilities")?caps():body.contains("GetProfiles")?profiles(OnvifDeviceProbe.MEDIA,false):uri(OnvifDeviceProbe.MEDIA));
        });
        probe.read(account(),true);assertEquals(5,modes.size());assertTrue(modes.stream().allMatch(Boolean::booleanValue));
    }
    @Test public void specialCredentialCharactersAreEncodedWithoutChangingTheMediaPathOrQuery() throws Exception {
        FetchMainAndSubStreamUrisBo bo=account();bo.setPassword("p@ss?#/word");
        String result=OnvifDeviceProbe.authenticatedUri("rtsp://192.168.1.13/ch%20one?profile=a%26b",bo);
        assertTrue(result.contains("p%40ss%3F%23%2Fword"));assertTrue(result.endsWith("/ch%20one?profile=a%26b"));
    }
    @Test public void unsafeOrUnreportedStreamAddressFailsClearly() {
        for(String url:new String[]{null,"","http://192.168.1.13/stream","rtsp://10.0.0.2/stream"})
            rejected(()->OnvifDeviceProbe.authenticatedUri(url,account()),"播放地址");
    }
    @Test public void missingCredentialsAreRejectedBeforeNetworkAccess() {
        FetchMainAndSubStreamUrisBo bo=account();bo.setPassword("");rejected(()->OnvifDeviceProbe.validate(bo),"用户名和密码");
    }
}
