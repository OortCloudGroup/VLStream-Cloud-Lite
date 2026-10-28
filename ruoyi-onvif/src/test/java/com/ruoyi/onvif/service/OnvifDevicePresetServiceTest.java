package com.ruoyi.onvif.service;

import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.AbsoluteMoveBo;
import com.ruoyi.onvif.domain.bo.PresetsBo;
import com.ruoyi.common.exception.ServiceException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnvifDevicePresetServiceTest {
    static class Reader extends OnvifDeviceScopedReader {
        List<OnvifDevice> rows = new ArrayList<>();
        Long selected;
        Reader() { super(null, "test"); }
        @Override public List<OnvifDevice> forOperation(OnvifDevice query, boolean map) {
            selected = query.getId(); assertFalse(map); return rows;
        }
    }
    static class Fixture {
        Reader reader = new Reader();
        List<String> calls = new ArrayList<>();
        Object body;
        Map<String, Object> capability = new HashMap<>();
        IOnvifService api;
        OnvifDevicePresetService service;
        Fixture() {
            OnvifDevice device = new OnvifDevice();
            device.setId(11L); device.setIp("192.0.2.1"); device.setUserName("fake&user");
            device.setPassword("fake-secret"); device.setChannel("stale-profile");
            reader.rows.add(device);
            capability.put("status", "supported"); capability.put("presets", true);
            capability.put("profileToken", "actual-profile");
            api = (IOnvifService) Proxy.newProxyInstance(IOnvifService.class.getClassLoader(), new Class[]{IOnvifService.class},
                (proxy, method, args) -> {
                    calls.add(method.getName()); body = args[0];
                    if (method.getName().equals("getPresetList")) {
                        Map<String,String> row = new LinkedHashMap<>();
                        row.put("name","Door"); row.put("token","a&b"); row.put("password","must-not-return");
                        return Collections.singletonList(row);
                    }
                    return null;
                });
            service = new OnvifDevicePresetService(reader, api, deviceRow -> capability);
        }
    }
    @Test public void listUsesScopedIdAndActualProfileAndNeverReturnsCredentials() {
        Fixture f = new Fixture();
        List<Map<String,String>> rows = f.service.list(11L);
        assertEquals(Long.valueOf(11), f.reader.selected);
        AbsoluteMoveBo request = (AbsoluteMoveBo) f.body;
        assertEquals("fake&amp;user",request.getUsername());
        assertEquals("fake-secret",request.getPassword());
        assertEquals("actual-profile",request.getProfileToken());
        assertEquals(new HashSet<>(Arrays.asList("name","token")),rows.get(0).keySet());
        assertEquals("a&b",rows.get(0).get("token"));
    }
    @Test public void everyMutationRechecksAccessAndEscapesSoapText() {
        Fixture f = new Fixture();
        f.service.create(11L," A&B ");
        assertEquals("A&amp;B",((PresetsBo)f.body).getPresetToken());
        f.service.goTo(11L,"<one>");
        assertEquals("&lt;one&gt;",((PresetsBo)f.body).getPresetToken());
        f.service.remove(11L,"two");
        assertEquals(Arrays.asList("addPreset","gotoPreset","removePreset"),f.calls);
        f.reader.rows.clear();
        try { f.service.goTo(11L,"two"); fail(); } catch (ServiceException expected) {}
        assertEquals(3,f.calls.size());
    }
    @Test public void missingCrossIdOrWrongScopeIsDeniedBeforeCapabilityAndNetwork() {
        Fixture f = new Fixture();
        for (Long id : Arrays.asList(0L,12L)) {
            try { f.service.list(id); fail(); } catch (ServiceException expected) {}
        }
        f.reader.rows.clear();
        try { f.service.list(11L); fail(); } catch (ServiceException expected) {}
        assertTrue(f.calls.isEmpty());
    }
    @Test public void unsupportedUnknownOrMissingProfileNeverCallsDevice() {
        for (String status : Arrays.asList("unknown","unsupported","supported")) {
            Fixture f = new Fixture();
            f.capability.put("status",status); f.capability.put("presets",false);
            try { f.service.list(11L); fail(); } catch (ServiceException expected) {}
            assertTrue(f.calls.isEmpty());
        }
        Fixture f = new Fixture(); f.capability.remove("profileToken");
        try { f.service.list(11L); fail(); } catch (ServiceException expected) {}
        assertTrue(f.calls.isEmpty());
    }
    @Test public void invalidInputDoesNotSendMutationOrRetry() {
        Fixture f = new Fixture();
        for (String token : Arrays.asList("", "a\n",String.join("",Collections.nCopies(257,"x")))) {
            try { f.service.goTo(11L,token); fail(); } catch (ServiceException expected) {}
        }
        assertTrue(f.calls.isEmpty());
    }
}
