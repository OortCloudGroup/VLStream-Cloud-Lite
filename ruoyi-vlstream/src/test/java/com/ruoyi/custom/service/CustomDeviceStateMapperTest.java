package com.ruoyi.custom.service;

import com.ruoyi.custom.mapper.CustomDeviceStateMapper;
import com.ruoyi.custom.domain.CustomTargetHeartbeat;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.junit.*;
import java.io.File;
import java.net.*;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real mapper, isolated fixture only; never connects to the application's DB. */
public class CustomDeviceStateMapperTest {
    @Test public void exactTargetProjectionEnforcesTenantAndLegacyDefaultWithoutSecrets() throws Exception {
        String jar = System.getProperty("video.square.h2Jar"); Assume.assumeTrue(jar != null && new File(jar).isFile());
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(jar).toURI().toURL()}, getClass().getClassLoader())) {
            Driver driver = (Driver) loader.loadClass("org.h2.Driver").newInstance();
            try (Connection connection = driver.connect("jdbc:h2:mem:custom_state_" + UUID.randomUUID() + ";MODE=MySQL", new Properties())) {
                connection.createStatement().execute("create table wvp_vlstream_device(device_id varchar(100),tenant_id varchar(64),online boolean,online_reason varchar(64),last_heartbeat_time timestamp,update_time timestamp)");
                connection.createStatement().execute("insert into wvp_vlstream_device values('legacy',null,false,'heartbeat_timeout',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),('own','default',true,'heartbeat',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),('foreign','foreign',true,'heartbeat',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                Configuration config = new Configuration(new Environment("fixture", new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
                String resource = "mapper/custom/CustomDeviceStateMapper.xml";
                new XMLMapperBuilder(getClass().getClassLoader().getResourceAsStream(resource), config, resource, config.getSqlFragments()).parse();
                try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                    CustomDeviceStateMapper mapper = session.getMapper(CustomDeviceStateMapper.class);
                    assertFalse(mapper.selectCameraTarget("legacy", "default", "default").getOnline());
                    assertTrue(mapper.selectCameraTarget("own", "default", "default").getOnline());
                    assertNull(mapper.selectCameraTarget("foreign", "default", "default"));
                    assertNull(mapper.selectCameraTarget("legacy", "foreign", "default"));
                    assertNull(mapper.selectCameraTarget("same-name", "default", "default"));
                    assertTrue(mapper.selectCameraTarget("foreign", "foreign", "default").getOnline());
                    Map<String,Object> query = new HashMap<>(); query.put("deviceId", "own"); query.put("tenantId", "default"); query.put("defaultTenantId", "default");
                    String sql = config.getMappedStatement("com.ruoyi.custom.mapper.CustomDeviceStateMapper.selectCameraTarget").getBoundSql(query).getSql();
                    assertFalse(sql.contains("select *")); assertFalse(sql.contains("password")); assertFalse(sql.contains("stream_url"));
                    Set<String> fields = new HashSet<>(); for (java.lang.reflect.Field field : CustomTargetHeartbeat.class.getDeclaredFields()) fields.add(field.getName());
                    assertEquals(new HashSet<>(Arrays.asList("online","onlineReason","lastHeartbeatTime","updateTime")), fields);
                }
            }
        }
    }
}
