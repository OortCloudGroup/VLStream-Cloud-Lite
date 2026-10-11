package com.ruoyi.video.service;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.video.controller.VideoDeviceController;
import com.ruoyi.video.config.VideoDeviceIdentityProperties;
import com.ruoyi.video.domain.VideoDeviceQuery;
import com.ruoyi.video.domain.VideoDeviceSource;
import com.ruoyi.video.mapper.VideoDeviceMapper;
import com.ruoyi.vlstream.service.VlStreamDeviceTenantService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.File;
import java.net.*;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Executes the real MyBatis query and HTTP controller against an isolated in-memory fixture.
 * No running WVP database, protocol service or camera is contacted.
 * Supply -Dvideo.square.h2Jar=<cached Java-8-compatible H2 jar> to run without changing project dependencies.
 */
public class VideoDeviceIsolatedApiTest {
    private Connection connection;
    private SqlSession session;
    private VideoDeviceScopedReader reader;
    private URLClassLoader loader;

    @Before public void setup() throws Exception {
        String jar = System.getProperty("video.square.h2Jar");
        Assume.assumeTrue("Optional cached H2 jar was not supplied", jar != null && new File(jar).isFile());
        loader = new URLClassLoader(new URL[]{new File(jar).toURI().toURL()}, getClass().getClassLoader());
        Driver driver = (Driver) loader.loadClass("org.h2.Driver").newInstance();
        connection = driver.connect("jdbc:h2:mem:video_square_" + UUID.randomUUID() + ";MODE=MySQL", new Properties());
        Statement sql = connection.createStatement();
        sql.execute("create table sys_dept(dept_id bigint)");
        sql.execute("insert into sys_dept values(1),(2)");
        sql.execute("create table wvp_vlstream_device(id bigint,tenant_id varchar(64),device_id varchar(100),device_serial varchar(100),device_name varchar(100),ip_addr varchar(50),online int,latitude varchar(50),longitude varchar(50))");
        sql.execute("insert into wvp_vlstream_device values(1,'default','vls-a',null,'VLS fixture','192.168.1.10',1,'22','114'),(2,'foreign','foreign-vls',null,'Foreign fixture',null,1,null,null),(3,null,'legacy-vls',null,'Legacy fixture',null,0,null,null)");
        sql.execute("create table isup_device(id bigint,dept_id bigint,device_id varchar(100),device_serial varchar(100),name varchar(100),ip_address varchar(50),status varchar(20),lat varchar(50),lng varchar(50),address_map varchar(100))");
        sql.execute("insert into isup_device values(1,1,'isup-a',null,'ISUP fixture',null,'ON',null,null,null)");
        sql.execute("create table ehome_device(id bigint,dept_id bigint,device_id varchar(100),device_serial varchar(100),name varchar(100),ip_address varchar(50),status varchar(20))");
        sql.execute("insert into ehome_device values(1,1,'ehome-a',null,'EHome fixture',null,'OFFLINE')");
        for (String table : Arrays.asList("rtsp_device","onvif_device")) {
            sql.execute("create table " + table + "(id bigint,dept_id bigint,name varchar(100),ip varchar(50),lat varchar(50),lng varchar(50),address_map varchar(100))");
            sql.execute("insert into " + table + " values(1,1,'" + table + " fixture','192.168.1.11','23','115',null)");
        }
        sql.execute("insert into onvif_device values(2,2,'Restricted department','192.168.1.12',null,null,null)");
        sql.execute("create table wvp_device(id bigint,dept_id bigint,device_id varchar(100),name varchar(100),ip varchar(50),on_line int,lat varchar(50),lng varchar(50))");
        sql.execute("insert into wvp_device values(1,1,'gb-a','GB fixture',null,0,null,null)");
        sql.execute("create table dahua_device(id bigint,dept_id bigint,device_id varchar(100),name varchar(100),ip varchar(50),lat varchar(50),lng varchar(50),address_map varchar(100))");
        sql.execute("insert into dahua_device values(1,1,'dahua-a','Dahua fixture',null,null,null,null)");
        sql.execute("create table wvp_custom_device(id bigint,device_code varchar(100),device_name varchar(100),status varchar(20),latitude varchar(50),longitude varchar(50),device_type varchar(20),address varchar(100))");
        sql.execute("insert into wvp_custom_device values(1,'custom-a','Custom fixture','UNKNOWN',null,null,null,null)");
        Configuration config = new Configuration(new Environment("isolated", new JdbcTransactionFactory(),
            new SingleConnectionDataSource(connection, true)));
        String resource = "mapper/video/VideoDeviceMapper.xml";
        new XMLMapperBuilder(getClass().getClassLoader().getResourceAsStream(resource), config, resource, config.getSqlFragments()).parse();
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        reader = new VideoDeviceScopedReader(session.getMapper(VideoDeviceMapper.class));
    }

    @After public void cleanup() throws Exception {
        SecurityContextHolder.clearContext();
        if (session != null) session.close();
        if (connection != null) connection.close();
        if (loader != null) loader.close();
    }

    @Test public void realHttpControllerReturnsAllAuthorizedLogicalDevicesAndCounts() throws Exception {
        LoginUser user = new LoginUser();
        user.setUser(new SysUser()); user.setTenantId("default");
        user.setPermissions(new HashSet<>(Arrays.asList("vlstream:device:list", "isup:lsupDevice:list", "ehome:device:list",
            "rtsp:RtspDevice:list", "onvif:device:list", "wvp:device:list", "dahua:device:list", "custom:device:list")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
        VlStreamDeviceTenantService tenants = mock(VlStreamDeviceTenantService.class);
        when(tenants.defaultTenant()).thenReturn("default");
        MockMvc api = MockMvcBuilders.standaloneSetup(new VideoDeviceController(
            new VideoDeviceService(reader, tenants, new VideoDeviceIdentityProperties()))).build();
        api.perform(get("/video/device/list").param("pageNum","2").param("pageSize","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(10)).andExpect(jsonPath("$.data.rows.length()").value(2))
            .andExpect(jsonPath("$.data.stats.online").value(2))
            .andExpect(jsonPath("$.data.stats.offline").value(3))
            .andExpect(jsonPath("$.data.stats.unknown").value(5))
            .andExpect(jsonPath("$.data.protocols.length()").value(8));
    }

    @Test public void realSqlHidesForeignAndLegacyRowsByTenant() {
        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setProtocol("VLSTREAM"); query.setTenantId("foreign"); query.setDefaultTenantId("default");
        List<VideoDeviceSource> rows = reader.vlstream(query);
        assertEquals(1, rows.size()); assertEquals("foreign-vls", rows.get(0).getDeviceId());
        query.setProtocol("CUSTOM");
        assertTrue(reader.custom(query).isEmpty());
    }

    @Test public void realSqlAppliesDepartmentPredicateWithoutRemovingUnlocatedDevices() {
        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setProtocol("ONVIF"); query.setTenantId("default"); query.setDefaultTenantId("default");
        query.getParams().put("dataScope", " AND d.dept_id=2");
        List<VideoDeviceSource> rows = reader.onvif(query);
        assertEquals(1, rows.size()); assertEquals("Restricted department", rows.get(0).getDeviceName());
        assertNull(rows.get(0).getLatitude());
    }
}
