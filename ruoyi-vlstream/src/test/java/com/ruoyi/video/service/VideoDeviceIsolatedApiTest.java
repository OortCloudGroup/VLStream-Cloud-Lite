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
        sql.execute("create table wvp_vlstream_device(id bigint,tenant_id varchar(64),device_id varchar(100),device_serial varchar(100),device_name varchar(100),ip_addr varchar(50),online int,latitude varchar(50),longitude varchar(50),visibility varchar(16))");
        sql.execute("insert into wvp_vlstream_device values(1,'default','vls-a',null,'VLS fixture','192.168.1.10',1,'22','114','PRIVATE'),(2,'foreign','foreign-vls','SERIAL-2','Foreign fixture','10.0.0.2',1,'31','121','PRIVATE'),(3,null,'legacy-vls',null,'Legacy fixture',null,0,null,null,'PRIVATE')");
        sql.execute("create table wvp_vlstream_device_access_ref(device_row_id bigint,access_ref varchar(32))");
        sql.execute("create table wvp_vlstream_access_request(request_id bigint,device_row_id bigint,requester_tenant_id varchar(64),requester_user_id bigint,status varchar(16),resolved_grant_id bigint)");
        sql.execute("create table wvp_vlstream_access_grant(grant_id bigint,device_row_id bigint,viewer_tenant_id varchar(64),expires_at timestamp,revoked_at timestamp)");
        sql.execute("create table isup_device(id bigint,dept_id bigint,device_id varchar(100),device_serial varchar(100),name varchar(100),ip_address varchar(50),status varchar(20),lat varchar(50),lng varchar(50),address_map varchar(100))");
        sql.execute("insert into isup_device values(1,1,'isup-a',null,'ISUP fixture',null,'ON',null,null,null)");
        sql.execute("create table ehome_device(id bigint,dept_id bigint,device_id varchar(100),device_serial varchar(100),name varchar(100),ip_address varchar(50),status varchar(20))");
        sql.execute("insert into ehome_device values(1,1,'ehome-a',null,'EHome fixture',null,'OFFLINE')");
        for (String table : Arrays.asList("rtsp_device","onvif_device")) {
            sql.execute("create table " + table + "(id bigint,dept_id bigint,name varchar(100),ip varchar(50),lat varchar(50),lng varchar(50),address_map varchar(100))");
            sql.execute("insert into " + table + " values(1,1,'" + table + " fixture','192.168.1.11','23','115',null)");
        }
        sql.execute("alter table rtsp_device add tenant_id varchar(64)");
        sql.execute("alter table onvif_device add tenant_id varchar(64)");
        sql.execute("alter table rtsp_device add play_type varchar(10)");
        sql.execute("alter table rtsp_device add channel varchar(20)");
        sql.execute("update rtsp_device set play_type='1', channel='101' where id=1");
        sql.execute("insert into onvif_device(id,dept_id,name,ip,lat,lng,address_map) values(2,2,'Restricted department','192.168.1.12',null,null,null)");
        sql.execute("alter table onvif_device add play_type varchar(10)");
        sql.execute("alter table onvif_device add channel varchar(20)");
        sql.execute("update onvif_device set play_type='1', channel='CH01' where id=1");
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
        VideoDeviceService service = new VideoDeviceService(reader, tenants, new VideoDeviceIdentityProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "protocolDefaultTenant", "default");
        MockMvc api = MockMvcBuilders.standaloneSetup(new VideoDeviceController(service)).build();
        api.perform(get("/video/device/list").param("pageNum","1").param("pageSize","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(10)).andExpect(jsonPath("$.data.rows.length()").value(2))
            .andExpect(jsonPath("$.data.stats.online").value(2))
            .andExpect(jsonPath("$.data.stats.offline").value(3))
            .andExpect(jsonPath("$.data.stats.unknown").value(5))
            .andExpect(jsonPath("$.data.rows[0].status").value("ONLINE"))
            .andExpect(jsonPath("$.data.rows[1].status").value("ONLINE"))
            .andExpect(jsonPath("$.data.protocols.length()").value(8));
    }

    @Test public void rtspSourceCarriesSavedPlaybackTypeAndChannelWithoutStreamUrl() {
        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setProtocol("RTSP"); query.setTenantId("default"); query.setDefaultTenantId("default");
        List<VideoDeviceSource> sources = reader.rtsp(query);
        assertEquals(1, sources.size());
        assertEquals("1", sources.get(0).getPlayType());
        assertEquals("101", sources.get(0).getChannel());
        assertNull(sources.get(0).getState());
    }

    @Test public void onvifSourceCarriesActualModeAndChannelWithoutStreamUrl() {
        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setProtocol("ONVIF"); query.setTenantId("default"); query.setDefaultTenantId("default");
        VideoDeviceSource source = reader.onvif(query).get(0);
        assertEquals("1", source.getPlayType()); assertEquals("CH01", source.getChannel());
    }

    @Test public void realSqlHidesForeignAndLegacyRowsByTenant() {
        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setProtocol("VLSTREAM"); query.setTenantId("foreign"); query.setDefaultTenantId("default");
        List<VideoDeviceSource> rows = reader.vlstream(query);
        assertEquals(1, rows.size()); assertEquals("foreign-vls", rows.get(0).getDeviceId());
        query.setProtocol("CUSTOM");
        assertTrue(reader.custom(query).isEmpty());
    }

    @Test public void sharedCatalogUsesOpaqueRefsAndRedactsPrivateDeviceDetails() throws Exception {
        try (Statement sql = connection.createStatement()) {
            sql.execute("insert into wvp_vlstream_device values(4,'foreign-public','public-vls','SERIAL-4','Public fixture','172.16.0.4',1,'30','120','PUBLIC')");
            sql.execute("insert into wvp_vlstream_device values(5,'foreign-granted','granted-vls','SERIAL-5','Granted fixture','192.168.0.5',1,'29','119','PRIVATE')");
            sql.execute("insert into wvp_vlstream_device values(6,'foreign-expired','expired-vls','SERIAL-6','Expired fixture','192.168.0.6',1,'28','118','PRIVATE')");
            sql.execute("insert into wvp_vlstream_device_access_ref values(2,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),(4,'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'),(5,'cccccccccccccccccccccccccccccccc'),(6,'dddddddddddddddddddddddddddddddd')");
            sql.execute("insert into wvp_vlstream_access_request values(21,2,'default',100,'PENDING',null),(22,5,'default',100,'APPROVED',501),(23,6,'default',100,'APPROVED',601)");
            sql.execute("insert into wvp_vlstream_access_grant values(501,5,'default',timestamp '2099-01-01 00:00:00',null),(601,6,'default',timestamp '2000-01-01 00:00:00',null)");
        }

        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setTenantId("default"); query.setDefaultTenantId("default");
        List<VideoDeviceSource> rows = reader.shareableVlstream(query);
        assertEquals(4, rows.size());

        VideoDeviceSource pending = byName(rows, "Foreign fixture");
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", pending.getRowId());
        assertEquals("PENDING", pending.getViewerAccess());
        assertEquals("PENDING", pending.getMyAccessRequestStatus());
        assertNull(pending.getDeviceId()); assertNull(pending.getSerial()); assertNull(pending.getIpAddress());
        assertNull(pending.getLatitude()); assertNull(pending.getLongitude()); assertNull(pending.getState());

        VideoDeviceSource publicDevice = byName(rows, "Public fixture");
        assertEquals("PUBLIC", publicDevice.getViewerAccess());
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", publicDevice.getRowId());
        assertEquals("ONLINE", publicDevice.getState()); assertEquals("30", publicDevice.getLatitude());
        assertNull(publicDevice.getDeviceId()); assertNull(publicDevice.getSerial()); assertNull(publicDevice.getIpAddress());

        VideoDeviceSource granted = byName(rows, "Granted fixture");
        assertEquals("GRANTED", granted.getViewerAccess()); assertEquals("APPROVED", granted.getMyAccessRequestStatus());
        assertNull(granted.getDeviceId()); assertNull(granted.getSerial()); assertNull(granted.getIpAddress());

        VideoDeviceSource expired = byName(rows, "Expired fixture");
        assertEquals("NONE", expired.getViewerAccess()); assertEquals("EXPIRED", expired.getMyAccessRequestStatus());
        assertNull(expired.getLatitude()); assertNull(expired.getLongitude()); assertNull(expired.getState());
        assertNull(expired.getDeviceId()); assertNull(expired.getSerial()); assertNull(expired.getIpAddress());
    }

    @Test public void sharedLegacyDefaultTenantRowsRemainDiscoverableWithoutPrivateDetailLeaks() throws Exception {
        seedLegacySharingRows();
        for (String legacyTenant : new String[]{null, "", "default", "   "}) {
            setLegacySharingTenant(legacyTenant);
            VideoDeviceQuery external = vlstreamQuery("external");
            List<VideoDeviceSource> rows = reader.shareableVlstream(external);
            assertEquals("Foreign reader must discover normalized legacy owner " + String.valueOf(legacyTenant), 3, rows.size());

            VideoDeviceSource publicDevice = byName(rows, "Legacy public fixture");
            assertEquals("PUBLIC", publicDevice.getViewerAccess());
            assertEquals("11111111111111111111111111111111", publicDevice.getRowId());
            assertEquals("ONLINE", publicDevice.getState());
            assertEquals("22", publicDevice.getLatitude());
            assertNull(publicDevice.getDeviceId()); assertNull(publicDevice.getSerial()); assertNull(publicDevice.getIpAddress());

            VideoDeviceSource granted = byName(rows, "Legacy granted fixture");
            assertEquals("GRANTED", granted.getViewerAccess());
            assertEquals("APPROVED", granted.getMyAccessRequestStatus());
            assertEquals("23", granted.getLatitude());
            assertNull(granted.getDeviceId()); assertNull(granted.getSerial()); assertNull(granted.getIpAddress());

            VideoDeviceSource privateDevice = byName(rows, "Legacy private fixture");
            assertEquals("NONE", privateDevice.getViewerAccess());
            assertEquals("33333333333333333333333333333333", privateDevice.getRowId());
            assertNull(privateDevice.getMyAccessRequestStatus());
            assertNull(privateDevice.getDeviceId()); assertNull(privateDevice.getSerial()); assertNull(privateDevice.getIpAddress());
            assertNull(privateDevice.getLatitude()); assertNull(privateDevice.getLongitude()); assertNull(privateDevice.getState());
            assertTrue("Owned rows must not be repeated as shared rows", reader.shareableVlstream(vlstreamQuery("default")).isEmpty());
        }
    }

    @Test public void ownedLegacyDefaultTenantRowsUseTheSameNormalizationAsSharedExclusion() throws Exception {
        seedLegacySharingRows();
        for (String legacyTenant : new String[]{null, "", "default", "   "}) {
            setLegacySharingTenant(legacyTenant);
            List<VideoDeviceSource> owned = reader.vlstream(vlstreamQuery("default"));
            assertEquals("Default owner must retain every legacy row " + String.valueOf(legacyTenant), 5, owned.size());
            assertEquals("legacy-public", byName(owned, "Legacy public fixture").getDeviceId());
            assertTrue(reader.shareableVlstream(vlstreamQuery("default")).isEmpty());
            List<VideoDeviceSource> externalOwned = reader.vlstream(vlstreamQuery("foreign"));
            assertEquals(1, externalOwned.size());
            assertEquals("foreign-vls", externalOwned.get(0).getDeviceId());
        }
    }

    private void seedLegacySharingRows() throws Exception {
        try (Statement sql = connection.createStatement()) {
            sql.execute("insert into wvp_vlstream_device values(40,'default','legacy-public','SERIAL-40','Legacy public fixture','10.0.0.40',1,'22','114','PUBLIC')," +
                "(41,'default','legacy-granted','SERIAL-41','Legacy granted fixture','10.0.0.41',1,'23','115','PRIVATE')," +
                "(42,'default','legacy-private','SERIAL-42','Legacy private fixture','10.0.0.42',1,'24','116','PRIVATE')");
            sql.execute("insert into wvp_vlstream_device_access_ref values(40,'11111111111111111111111111111111')," +
                "(41,'22222222222222222222222222222222'),(42,'33333333333333333333333333333333')");
            sql.execute("insert into wvp_vlstream_access_grant values(4041,41,'external',timestamp '2099-01-01 00:00:00',null)");
        }
    }

    private void setLegacySharingTenant(String tenant) throws Exception {
        try (PreparedStatement sql = connection.prepareStatement("update wvp_vlstream_device set tenant_id=? where id in (40,41,42)")) {
            sql.setString(1, tenant);
            sql.executeUpdate();
        }
        session.clearCache();
    }

    private static VideoDeviceQuery vlstreamQuery(String tenant) {
        VideoDeviceQuery query = new VideoDeviceQuery();
        query.setProtocol("VLSTREAM"); query.setTenantId(tenant); query.setDefaultTenantId("default");
        return query;
    }

    private static VideoDeviceSource byName(List<VideoDeviceSource> rows, String name) {
        return rows.stream().filter(row -> name.equals(row.getDeviceName())).findFirst().orElseThrow(AssertionError::new);
    }

    @Test public void realSqlScopesOwnedProtocolsByTenantAndDepartment() throws Exception {
        Statement sql = connection.createStatement();
        for (String table : Arrays.asList("rtsp_device", "onvif_device")) {
            sql.execute("insert into " + table + "(id,dept_id,name,tenant_id) values(11,1,'Owned fixture','foreign'),(12,2,'Other department','foreign'),(13,1,'Other tenant','third')");
            VideoDeviceQuery query = new VideoDeviceQuery();
            query.setProtocol(table.startsWith("rtsp") ? "RTSP" : "ONVIF");
            query.setTenantId("foreign"); query.setDefaultTenantId("default");
            query.getParams().put("dataScope", " AND d.dept_id=1");
            List<VideoDeviceSource> rows = table.startsWith("rtsp") ? reader.rtsp(query) : reader.onvif(query);
            assertEquals(1, rows.size()); assertEquals("Owned fixture", rows.get(0).getDeviceName());
            query.setTenantId("default");
            rows = table.startsWith("rtsp") ? reader.rtsp(query) : reader.onvif(query);
            assertEquals(1, rows.size()); assertEquals("1", rows.get(0).getRowId());
        }
        sql.close();
    }

    @Test public void otherTenantCannotReadExplicitTopTenantProtocolRowsInAggregateSql() throws Exception {
        String top = "0e391fd7-1033-4f09-88c0-187582fee462";
        try (Statement sql = connection.createStatement()) {
            for (String table : Arrays.asList("rtsp_device", "onvif_device")) {
                sql.execute("insert into " + table + "(id,dept_id,name,tenant_id) values(21,1,'Top tenant fixture','" + top + "'),(22,1,'Own tenant fixture','other-tenant')");
                VideoDeviceQuery query = new VideoDeviceQuery();
                query.setProtocol(table.startsWith("rtsp") ? "RTSP" : "ONVIF");
                query.setTenantId("other-tenant"); query.setDefaultTenantId("000000");
                query.getParams().put("dataScope", " AND d.dept_id=1");
                List<VideoDeviceSource> rows = table.startsWith("rtsp") ? reader.rtsp(query) : reader.onvif(query);
                assertEquals(1, rows.size()); assertEquals("Own tenant fixture", rows.get(0).getDeviceName());
                query.setTenantId(top);
                rows = table.startsWith("rtsp") ? reader.rtsp(query) : reader.onvif(query);
                assertEquals(1, rows.size()); assertEquals("Top tenant fixture", rows.get(0).getDeviceName());
                query.getParams().put("dataScope", " AND d.dept_id=2");
                rows = table.startsWith("rtsp") ? reader.rtsp(query) : reader.onvif(query);
                assertTrue(rows.isEmpty());
            }
        }
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
