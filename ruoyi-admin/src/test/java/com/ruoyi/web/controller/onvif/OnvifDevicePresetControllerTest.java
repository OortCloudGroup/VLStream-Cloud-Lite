package com.ruoyi.web.controller.onvif;

import com.ruoyi.onvif.domain.bo.DevicePresetRequest;
import com.ruoyi.onvif.service.OnvifDevicePresetService;
import java.util.*;
import org.junit.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class OnvifDevicePresetControllerTest {
    static class Fake extends OnvifDevicePresetService {
        String value; Long id;
        Fake() { super(null,null); }
        @Override public List<Map<String,String>> list(Long id) { this.id=id; return Collections.emptyList(); }
        @Override public void create(Long id,String name) { this.id=id; value=name; }
        @Override public void goTo(Long id,String token) { this.id=id; value=token; }
        @Override public void remove(Long id,String token) { this.id=id; value=token; }
    }
    @Test public void idRoutesBindOnlyPublicNameOrTokenFields() throws Exception {
        Fake fake = new Fake();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OnvifDevicePresetController(fake)).build();
        mvc.perform(get("/onvif/service/device/11/presets")).andExpect(status().isOk());
        mvc.perform(post("/onvif/service/device/11/presets").contentType("application/json")
            .content("{\"name\":\"Door\"}")).andExpect(status().isOk());
        assertEquals("Door",fake.value);
        mvc.perform(post("/onvif/service/device/11/presets/goto").contentType("application/json")
            .content("{\"token\":\"opaque\"}")).andExpect(status().isOk());
        mvc.perform(delete("/onvif/service/device/11/presets").contentType("application/json")
            .content("{\"token\":\"opaque\"}")).andExpect(status().isOk());
        assertEquals(Long.valueOf(11),fake.id); assertEquals("opaque",fake.value);
        assertEquals(2, DevicePresetRequest.class.getDeclaredFields().length);
    }
    @Test public void everyActionKeepsItsExistingDistinctPermission() throws Exception {
        String[] methods={"list","create","goTo","remove"};
        String[] permissions={"getPresets","addPreset","gotoPreset","removePreset"};
        for(int i=0;i<methods.length;i++) {
            Class<?>[] args=i==0?new Class[]{Long.class}:new Class[]{Long.class,DevicePresetRequest.class};
            String rule=OnvifDevicePresetController.class.getMethod(methods[i],args).getAnnotation(PreAuthorize.class).value();
            assertEquals("@ss.hasPermi('onvif:service:"+permissions[i]+"')",rule);
        }
    }
}
