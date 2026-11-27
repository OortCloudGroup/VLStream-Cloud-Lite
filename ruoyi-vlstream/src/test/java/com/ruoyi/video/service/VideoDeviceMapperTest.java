package com.ruoyi.video.service;

import com.ruoyi.video.domain.VideoDeviceQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import java.io.InputStream;
import static org.junit.Assert.*;

public class VideoDeviceMapperTest {
    @Test public void allQueriesParseAndEnforceTenantWithoutSelectingCredentials() {
        Configuration configuration = new Configuration();
        String resource = "mapper/video/VideoDeviceMapper.xml";
        InputStream input = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(input);
        new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        for (String protocol : VideoDeviceService.PERMISSIONS.keySet()) {
            VideoDeviceQuery query = new VideoDeviceQuery();
            query.setProtocol(protocol); query.setTenantId("tenant"); query.setDefaultTenantId("default");
            query.getParams().put("dataScope", " AND d.dept_id = 123");
            String sql = configuration.getMappedStatement("com.ruoyi.video.mapper.VideoDeviceMapper.selectSources")
                .getBoundSql(query).getSql().toLowerCase();
            String compactSql = sql.replaceAll("\\s+", "");
            assertFalse(sql.contains("password")); assertFalse(sql.contains("stream_url"));
            assertFalse(sql.contains("select *")); assertFalse(sql.contains("limit"));
            if (java.util.Arrays.asList("VLSTREAM", "RTSP", "ONVIF").contains(protocol)) {
                assertTrue(sql.contains("tenant_id"));
                if (!"VLSTREAM".equals(protocol)) {
                    assertFalse(sql.contains("tenant_id is null"));
                    query.setTenantId("default");
                    String defaultSql = configuration.getMappedStatement("com.ruoyi.video.mapper.VideoDeviceMapper.selectSources")
                        .getBoundSql(query).getSql().toLowerCase();
                    assertTrue(defaultSql.contains("tenant_id is null"));
                }
            }
            else assertTrue("Missing tenant equality for " + protocol, compactSql.contains("?=?"));
            if (!"VLSTREAM".equals(protocol) && !"CUSTOM".equals(protocol)) assertTrue(sql.contains("d.dept_id = 123"));
            assertFalse(sql.contains("lat is not null"));
        }
    }
}

