package com.ruoyi.web.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.Test;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.Assert.*;

public class ProtocolTenantMigrationConfigurationTest {
    private String resolve(MockEnvironment env) {
        FluentConfiguration c=new FluentConfiguration();
        c.placeholders(Collections.singletonMap("existing","preserved"));
        new ProtocolTenantMigrationConfiguration(env).protocolLegacyTenantPlaceholder().customize(c);
        assertEquals("preserved",c.getPlaceholders().get("existing"));
        return c.getPlaceholders().get("protocol_legacy_tenant_id");
    }
    @Test public void singleCompatibilityDefaultsTo000000(){assertEquals("000000",resolve(new MockEnvironment()));}
    @Test public void explicitLegacyGlobalDefaultStillWorks(){assertEquals("single-custom",resolve(new MockEnvironment().withProperty("vlstream.device.default-tenant-id","single-custom")));}
    @Test public void multiBackfillIsNarrowAndDoesNotChangeGlobal(){MockEnvironment e=new MockEnvironment().withProperty("vlstream.device.default-tenant-id","000000").withProperty("protocol-device.legacy-tenant-id","tenant-multi-fixture");assertEquals("tenant-multi-fixture",resolve(e));assertEquals("000000",e.getProperty("vlstream.device.default-tenant-id"));}
    @Test public void deploymentEnvironmentVariableBinds(){Map<String,Object> p=new HashMap<>();p.put("PROTOCOL_DEVICE_LEGACY_TENANT_ID","default-multi");MockEnvironment e=new MockEnvironment();e.getPropertySources().addFirst(new SystemEnvironmentPropertySource("fixture-env",p));assertEquals("default-multi",resolve(e));}
    @Test public void sqlQuoteAndEmptyAreRejected(){for(String value:new String[]{"", " ","tenant' OR 1=1",String.join("",Collections.nCopies(65,"x"))}){try{resolve(new MockEnvironment().withProperty("protocol-device.legacy-tenant-id",value));fail("invalid configured tenant accepted");}catch(IllegalStateException expected){}}}
}
