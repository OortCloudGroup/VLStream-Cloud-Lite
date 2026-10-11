package com.ruoyi.onvif.utils;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class OnvifPtzCapabilitiesTest {
    static final String DEVICE = "http://www.onvif.org/ver10/device/wsdl";
    static final String MEDIA = "http://www.onvif.org/ver10/media/wsdl";
    static final String PTZ = "http://www.onvif.org/ver20/ptz/wsdl";
    static final String SCHEMA = "http://www.onvif.org/ver10/schema";

    static OnvifDevice device() {
        OnvifDevice device = new OnvifDevice();
        device.setIp("192.168.1.13");
        device.setUserName("admin");
        device.setPassword("test-only");
        device.setChannel("Profile_1");
        return device;
    }

    static String soap(String body) {
        return "<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Body>"
                + body + "</s:Body></s:Envelope>";
    }

    static String fault(String reason) {
        return soap("<s:Fault xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                + "<s:Reason><s:Text>" + reason + "</s:Text></s:Reason></s:Fault>");
    }

    /** No network is used. Unexpected or mutating operations fail immediately. */
    static class Fixture implements OnvifPtzClient.Transport {
        final List<String> operations = new ArrayList<>();
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        boolean advertisedPtz = true;
        boolean selectedProfileHasPtz = true;
        boolean selectedProfileExists = true;
        boolean optionsContinuous = true;
        boolean nodeContinuous = true;
        String returnedNodeToken = "Node_1";
        int maximumPresets = 16;
        String failedOperation;
        String failureBody;
        int failureStatus = 200;
        boolean timeout;

        @Override
        public OnvifPtzClient.Response post(String url, String body) {
            String operation = operation(body);
            operations.add(operation);
            urls.add(url);
            bodies.add(body);
            if (operation.equals(failedOperation)) {
                if (timeout) throw new ServiceException("ONVIF PTZ 服务连接失败或超时");
                return new OnvifPtzClient.Response(failureStatus, failureBody);
            }
            String base = url.substring(0, url.indexOf("/onvif/"));
            String response;
            if ("GetCapabilities".equals(operation)) {
                response = "<GetCapabilitiesResponse xmlns='" + DEVICE + "'><Capabilities xmlns:t='" + SCHEMA + "'>"
                        + (advertisedPtz ? "<t:PTZ><t:XAddr>" + base + "/onvif/PTZ</t:XAddr></t:PTZ>" : "")
                        + "<t:Media><t:XAddr>" + base + "/onvif/Media</t:XAddr></t:Media>"
                        + "</Capabilities></GetCapabilitiesResponse>";
            } else if ("GetProfiles".equals(operation)) {
                response = "<GetProfilesResponse xmlns='" + MEDIA + "' xmlns:t='" + SCHEMA + "'>"
                        + profile("Unselected", true)
                        + (selectedProfileExists ? profile("Profile_1", selectedProfileHasPtz) : "")
                        + profile("Profile_2", true) + "</GetProfilesResponse>";
            } else if ("GetConfigurationOptions".equals(operation)) {
                response = "<GetConfigurationOptionsResponse xmlns='" + PTZ + "' xmlns:t='" + SCHEMA + "'>"
                        + "<PTZConfigurationOptions><t:Spaces>" + continuousSpace(optionsContinuous)
                        + "</t:Spaces></PTZConfigurationOptions></GetConfigurationOptionsResponse>";
            } else if ("GetStatus".equals(operation)) {
                response = "<GetStatusResponse xmlns='" + PTZ + "'/>";
            } else if ("GetNodes".equals(operation)) {
                response = "<GetNodesResponse xmlns='" + PTZ + "' xmlns:t='" + SCHEMA + "'>"
                        + "<PTZNode token='UnselectedNode'><t:SupportedPTZSpaces>" + continuousSpace(true)
                        + "</t:SupportedPTZSpaces><t:MaximumNumberOfPresets>255</t:MaximumNumberOfPresets></PTZNode>"
                        + "<PTZNode token='" + returnedNodeToken + "'><t:SupportedPTZSpaces>" + continuousSpace(nodeContinuous)
                        + "</t:SupportedPTZSpaces><t:MaximumNumberOfPresets>" + maximumPresets
                        + "</t:MaximumNumberOfPresets></PTZNode></GetNodesResponse>";
            } else {
                throw new AssertionError("Capability discovery must remain read-only: " + operation);
            }
            return new OnvifPtzClient.Response(200, soap(response));
        }

        private String profile(String token, boolean hasPtz) {
            return "<Profiles token='" + token + "'>" + (hasPtz
                    ? "<t:PTZConfiguration token='Configuration_1'><t:NodeToken>Node_1</t:NodeToken></t:PTZConfiguration>"
                    : "") + "</Profiles>";
        }

        private String continuousSpace(boolean present) {
            return present ? "<t:ContinuousPanTiltVelocitySpace>"
                    + "<t:URI>http://www.onvif.org/ver10/tptz/PanTiltSpaces/VelocityGenericSpace</t:URI>"
                    + "<t:XRange><t:Min>-1</t:Min><t:Max>1</t:Max></t:XRange>"
                    + "<t:YRange><t:Min>-1</t:Min><t:Max>1</t:Max></t:YRange>"
                    + "</t:ContinuousPanTiltVelocitySpace>" : "";
        }

        static String operation(String body) {
            for (String operation : new String[]{"GetCapabilities", "GetProfiles", "GetConfigurationOptions", "GetStatus", "GetNodes", "ContinuousMove", "Stop"}) {
                if (body.contains("<" + operation + " ") || body.contains("<p:" + operation + " ")) return operation;
            }
            throw new AssertionError("Unexpected SOAP operation");
        }
    }

    private void assertUnavailable(Map<String, Object> result, String status) {
        assertEquals(status, result.get("status"));
        assertEquals(Boolean.FALSE, result.get("continuousPanTilt"));
        assertEquals(Boolean.FALSE, result.get("presets"));
        assertEquals("Profile_1", result.get("profileToken"));
        assertFalse(String.valueOf(result.get("reason")).isEmpty());
    }

    @Test public void selectedProfileAndMatchedNodeYieldReadOnlySupportedCapabilities() {
        Fixture fixture = new Fixture();
        Map<String, Object> result = new OnvifPtzCapabilities(fixture).inspect(device());
        assertEquals("supported", result.get("status"));
        assertEquals(Boolean.TRUE, result.get("continuousPanTilt"));
        assertEquals(Boolean.TRUE, result.get("presets"));
        assertEquals("Profile_1", result.get("profileToken"));
        assertEquals("", result.get("reason"));
        assertEquals(5, fixture.operations.size());
        assertEquals("GetCapabilities", fixture.operations.get(0));
        assertEquals("GetStatus", fixture.operations.get(3));
        assertEquals("http://192.168.1.13/onvif/Media", fixture.urls.get(1));
        assertEquals("http://192.168.1.13/onvif/PTZ", fixture.urls.get(2));
        assertTrue(fixture.bodies.get(2).contains("<ConfigurationToken>Configuration_1</ConfigurationToken>"));
        assertTrue(fixture.bodies.get(3).contains("<ProfileToken>Profile_1</ProfileToken>"));
        assertFalse(fixture.operations.contains("ContinuousMove"));
        assertFalse(fixture.operations.contains("Stop"));
    }

    @Test public void noAdvertisedPtzIsUnsupportedWithoutCallingMediaOrMotion() {
        Fixture fixture = new Fixture(); fixture.advertisedPtz = false;
        assertUnavailable(new OnvifPtzCapabilities(fixture).inspect(device()), "unsupported");
        assertEquals(1, fixture.operations.size());
    }

    @Test public void selectedProfileWithoutPtzCannotBorrowAnotherProfilesConfiguration() {
        Fixture fixture = new Fixture(); fixture.selectedProfileHasPtz = false;
        assertUnavailable(new OnvifPtzCapabilities(fixture).inspect(device()), "unsupported");
        assertEquals(2, fixture.operations.size());
    }

    @Test public void missingSelectedProfileIsUnknownRatherThanClaimingNoPtzSupport() {
        Fixture fixture = new Fixture(); fixture.selectedProfileExists = false;
        Map<String, Object> result = new OnvifPtzCapabilities(fixture).inspect(device());
        assertUnavailable(result, "unknown");
        assertTrue(String.valueOf(result.get("reason")).contains("通道"));
    }

    @Test public void httpAuthenticationFailureIsUnknownAndExplainsAccountCheck() {
        Fixture fixture = new Fixture(); fixture.failedOperation = "GetCapabilities";
        fixture.failureStatus = 401; fixture.failureBody = "Unauthorized";
        Map<String, Object> result = new OnvifPtzCapabilities(fixture).inspect(device());
        assertUnavailable(result, "unknown");
        assertTrue(String.valueOf(result.get("reason")).contains("认证"));
    }

    @Test public void soapAuthenticationFaultAtStatusCannotPassAsUsablePtz() {
        Fixture fixture = new Fixture(); fixture.failedOperation = "GetStatus";
        fixture.failureBody = fault("NotAuthorized");
        Map<String, Object> result = new OnvifPtzCapabilities(fixture).inspect(device());
        assertUnavailable(result, "unknown");
        assertTrue(String.valueOf(result.get("reason")).contains("认证"));
        assertFalse(fixture.operations.contains("GetNodes"));
    }

    @Test public void timeoutRemainsUnknownAndNeverBecomesUnsupported() {
        Fixture fixture = new Fixture(); fixture.failedOperation = "GetConfigurationOptions"; fixture.timeout = true;
        Map<String, Object> result = new OnvifPtzCapabilities(fixture).inspect(device());
        assertUnavailable(result, "unknown");
        assertTrue(String.valueOf(result.get("reason")).contains("超时"));
    }

    @Test public void profileNodeTokenMustMatchInsteadOfUsingAnotherAdvertisedNode() {
        Fixture fixture = new Fixture(); fixture.returnedNodeToken = "WrongNode";
        assertUnavailable(new OnvifPtzCapabilities(fixture).inspect(device()), "unknown");
    }

    @Test public void continuousDirectionNeedsBothConfigurationAndMatchingNodeSpaces() {
        for (boolean missingOptions : new boolean[]{true, false}) {
            Fixture fixture = new Fixture(); fixture.maximumPresets = 0;
            fixture.optionsContinuous = !missingOptions; fixture.nodeContinuous = missingOptions;
            assertUnavailable(new OnvifPtzCapabilities(fixture).inspect(device()), "unsupported");
        }
    }

    @Test public void presetOnlyCapabilityDoesNotEnableContinuousDirectionCommands() {
        Fixture fixture = new Fixture(); fixture.optionsContinuous = false;
        OnvifPtzCapabilities client = new OnvifPtzCapabilities(fixture);
        Map<String, Object> result = client.inspect(device());
        assertEquals("supported", result.get("status"));
        assertEquals(Boolean.FALSE, result.get("continuousPanTilt"));
        assertEquals(Boolean.TRUE, result.get("presets"));
        try { client.requireContinuous(device()); fail("Direction controls must be unavailable"); }
        catch (ServiceException error) { assertTrue(error.getMessage().contains("连续方向控制")); }
        assertEquals(5, fixture.operations.size());
    }

    @Test public void cacheIdentityIncludesHostAccountPasswordAndSelectedChannel() {
        Fixture fixture = new Fixture(); OnvifPtzCapabilities client = new OnvifPtzCapabilities(fixture);
        OnvifDevice original = device(); client.inspect(original);
        client.inspect(device()); assertEquals(5, fixture.operations.size());
        original.setId(999L); original.setName("renamed"); client.inspect(original);
        assertEquals("Unrelated mutable row fields must not invalidate the cache", 5, fixture.operations.size());
        original.setPassword("different-test-password"); client.inspect(original);
        assertEquals(10, fixture.operations.size());
        client.inspect(device()); assertEquals("Mutating the source row must not mutate its old key", 10, fixture.operations.size());
        OnvifDevice otherUser = device(); otherUser.setUserName("operator"); client.inspect(otherUser);
        assertEquals(15, fixture.operations.size());
        OnvifDevice otherHost = device(); otherHost.setIp("192.168.1.14"); client.inspect(otherHost);
        assertEquals(20, fixture.operations.size());
        OnvifDevice otherChannel = device(); otherChannel.setChannel("Profile_2");
        assertEquals("Profile_2", client.inspect(otherChannel).get("profileToken"));
        assertEquals(25, fixture.operations.size());
    }

    @Test public void returnedMapsCannotPoisonTheCacheAndRefreshRereadsTheCamera() {
        Fixture fixture = new Fixture(); OnvifPtzCapabilities client = new OnvifPtzCapabilities(fixture);
        Map<String, Object> first = client.inspect(device());
        first.put("status", "unsupported"); first.put("continuousPanTilt", Boolean.FALSE);
        first.put("reason", "changed by caller");
        Map<String, Object> cached = client.inspect(device());
        assertEquals("supported", cached.get("status"));
        assertEquals(Boolean.TRUE, cached.get("continuousPanTilt"));
        assertEquals("", cached.get("reason"));
        assertEquals(5, fixture.operations.size());
        cached.clear(); fixture.advertisedPtz = false;
        assertUnavailable(client.inspect(device(), true), "unsupported");
        assertEquals(6, fixture.operations.size());
        assertUnavailable(client.inspect(device()), "unsupported");
        assertEquals(6, fixture.operations.size());
    }
}
