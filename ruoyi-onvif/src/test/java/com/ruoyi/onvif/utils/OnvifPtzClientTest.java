package com.ruoyi.onvif.utils;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.OnvifPZT;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OnvifPtzClientTest {
    private static final String PTZ = "http://www.onvif.org/ver20/ptz/wsdl";
    private static final String DEVICE = "http://www.onvif.org/ver10/device/wsdl";
    static String soap(String content) {
        return "<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Body>" + content + "</s:Body></s:Envelope>";
    }
    static String success(String name, String namespace) { return soap("<"+name+"Response xmlns='"+namespace+"'/>"); }
    private OnvifDevice device() {
        OnvifDevice d = new OnvifDevice(); d.setIp("192.168.1.11");d.setUserName("admin");d.setPassword("test-only");d.setChannel("Profile_1");return d;
    }
    private OnvifPZT command(String direction, Double speed) {
        OnvifPZT c = new OnvifPZT();c.setDirection(direction);c.setControSpeed(speed);return c;
    }
    private void rejected(Runnable action, String text) {
        try { action.run();fail("Expected a clear error"); } catch (ServiceException e) { assertTrue(e.getMessage(),e.getMessage().contains(text)); }
    }
    @Test public void successWithoutFaultIsAccepted() {
        assertNotNull(OnvifPtzClient.checked(new OnvifPtzClient.Response(200,success("ContinuousMove",PTZ)),"ContinuousMove",PTZ));
        assertNotNull(OnvifPtzClient.checked(new OnvifPtzClient.Response(200,success("Stop",PTZ)),"Stop",PTZ));
    }
    @Test public void soapFaultReasonIsPreservedEvenWithHttp200() {
        String fault=soap("<s:Fault xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Reason><s:Text>NotAuthorized</s:Text></s:Reason></s:Fault>");
        rejected(()->OnvifPtzClient.checked(new OnvifPtzClient.Response(200,fault),"Stop",PTZ),"NotAuthorized");
    }
    @Test public void faultWithoutReasonDoesNotCauseNullPointer() {
        rejected(()->OnvifPtzClient.checked(new OnvifPtzClient.Response(500,soap("<s:Fault xmlns:s='http://www.w3.org/2003/05/soap-envelope'/>")),"Stop",PTZ),"SOAP Fault");
    }
    @Test public void httpAndMalformedResponsesRemainExplicit() {
        rejected(()->OnvifPtzClient.checked(new OnvifPtzClient.Response(404,"not XML"),"Stop",PTZ),"HTTP 404");
        rejected(()->OnvifPtzClient.checked(new OnvifPtzClient.Response(503,success("Stop",PTZ)),"Stop",PTZ),"HTTP 503");
    }
    @Test public void unrelatedSuccessCannotPassAsStop() {
        rejected(()->OnvifPtzClient.checked(new OnvifPtzClient.Response(200,success("ContinuousMove",PTZ)),"Stop",PTZ),"未收到操作成功应答");
    }
    @Test public void fourDirectionsFollowNormalizedPtzCoordinates() {
        assertArrayEquals(new double[]{0.1,0},OnvifPtzClient.velocity("right",0.1),0);
        assertArrayEquals(new double[]{-0.1,0},OnvifPtzClient.velocity("left",0.1),0);
        assertArrayEquals(new double[]{0,0.1},OnvifPtzClient.velocity("upper",0.1),0);
        assertArrayEquals(new double[]{0,-0.1},OnvifPtzClient.velocity("below",0.1),0);
    }
    @Test public void invalidSpeedAndDirectionAreRejectedBeforeSending() {
        for(Double speed:new Double[]{null,Double.NaN,Double.POSITIVE_INFINITY,0.0,0.09,1.01})
            rejected(()->OnvifPtzClient.velocity("right",speed),"速度");
        rejected(()->OnvifPtzClient.velocity("unknown",0.1),"方向");
    }
    @Test public void missingDeviceAndProfileNeverFallBackSilently() {
        rejected(()->OnvifPtzClient.validate(null),"不存在");
        OnvifDevice d=device();d.setChannel(null);rejected(()->OnvifPtzClient.validate(d),"ProfileToken");
    }
    @Test public void advertisedEndpointMustBelongToTheCamera() {
        assertEquals("http://192.168.1.11/onvif/PTZ",OnvifPtzClient.validateEndpoint("http://192.168.1.11","http://192.168.1.11/onvif/PTZ"));
        for(String url:new String[]{"http://10.0.0.1/ptz","http://u:p@192.168.1.11/ptz","file:///etc/passwd","/onvif/PTZ"})
            rejected(()->OnvifPtzClient.validateEndpoint("http://192.168.1.11",url),"服务地址");
    }
    @Test public void discoveredEndpointAndSuccessfulStartStopDoNotReprobeForStop() {
        List<String> urls=new ArrayList<>(),bodies=new ArrayList<>();
        OnvifPtzCapabilitiesTest.Fixture discovery = new OnvifPtzCapabilitiesTest.Fixture();
        OnvifPtzClient client=new OnvifPtzClient((url,body)->{
            urls.add(url);bodies.add(body);
            if (!body.contains("<p:ContinuousMove ") && !body.contains("<p:Stop ")) return discovery.post(url,body);
            return new OnvifPtzClient.Response(200,success(body.contains("ContinuousMove")?"ContinuousMove":"Stop",PTZ));
        });
        client.start(device(),command("right",0.1));
        assertEquals(7,urls.size());
        int readonlyCalls=discovery.operations.size();
        client.stop(device());
        assertEquals(8,urls.size());assertEquals(readonlyCalls,discovery.operations.size());
        assertEquals("http://192.168.1.11/onvif/PTZ",urls.get(6));assertEquals(urls.get(6),urls.get(7));
        assertTrue(bodies.get(6).contains("x=\"0.1\""));assertTrue(bodies.get(6).contains("PT2S"));
        assertTrue(bodies.get(7).contains("<p:PanTilt>true</p:PanTilt>"));assertFalse(bodies.get(6).contains("test-only"));
    }
    @Test public void unsupportedCapabilitiesPreventMovementBeforeEndpointOrMotionRequests() {
        OnvifPtzCapabilitiesTest.Fixture discovery = new OnvifPtzCapabilitiesTest.Fixture();
        discovery.advertisedPtz=false;
        OnvifPtzClient client=new OnvifPtzClient(discovery);
        rejected(()->client.start(device(),command("right",0.1)),"不支持");
        assertEquals(1,discovery.operations.size());
        assertFalse(discovery.operations.contains("ContinuousMove"));
        assertFalse(discovery.operations.contains("Stop"));
    }
    @Test public void failedStartStillStopsAtCapturedEndpointEvenWhenReadOnlyProbesAreNowUnavailable() {
        for (int failureMode=0;failureMode<3;failureMode++) {
            final int mode=failureMode;
            List<String> operations=new ArrayList<>(),urls=new ArrayList<>();
            boolean[] readonlyUnavailable={false};
            OnvifPtzCapabilitiesTest.Fixture discovery = new OnvifPtzCapabilitiesTest.Fixture();
            OnvifPtzClient client=new OnvifPtzClient((url,body)->{
                String operation=OnvifPtzCapabilitiesTest.Fixture.operation(body);
                operations.add(operation);urls.add(url);
                if ("ContinuousMove".equals(operation)) {
                    if (mode==0) return new OnvifPtzClient.Response(500,success("ContinuousMove",PTZ));
                    if (mode==1) return new OnvifPtzClient.Response(200,"malformed SOAP response");
                    throw new ServiceException("ONVIF PTZ 服务连接失败或超时");
                }
                if ("Stop".equals(operation)) return new OnvifPtzClient.Response(200,success("Stop",PTZ));
                if (readonlyUnavailable[0]) throw new ServiceException("Read-only probes unavailable after attempted movement");
                return discovery.post(url,body);
            });
            rejected(()->client.start(device(),command("right",0.1)),mode==2?"超时":"ContinuousMove");
            assertEquals(7,operations.size());
            String capturedEndpoint=urls.get(6);
            readonlyUnavailable[0]=true;
            client.stop(device());
            assertEquals("Only Stop may be sent after the attempted movement",8,operations.size());
            assertEquals("Stop",operations.get(7));
            assertEquals(capturedEndpoint,urls.get(7));
            // A successful Stop releases that pending endpoint. A later Stop must discover it again.
            rejected(()->client.stop(device()),"Read-only probes unavailable");
            assertEquals("GetCapabilities",operations.get(8));
        }
    }
    @Test public void xmlEntitiesCannotReadLocalFiles() {
        rejected(()->OnvifPtzClient.checked(new OnvifPtzClient.Response(200,"<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+success("Stop",PTZ)),"Stop",PTZ),"无有效 SOAP");
    }
}
