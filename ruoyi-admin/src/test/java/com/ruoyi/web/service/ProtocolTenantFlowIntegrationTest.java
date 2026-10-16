package com.ruoyi.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.deviceavailability.ProtocolAvailabilityService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.aspectj.DataScopeAspect;
import com.ruoyi.framework.security.context.PermissionContextHolder;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.mapper.OnvifDeviceMapper;
import com.ruoyi.onvif.service.OnvifDeviceScopedReader;
import com.ruoyi.onvif.service.impl.OnvifDeviceServiceImpl;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.mapper.RtspDeviceMapper;
import com.ruoyi.rtsp.service.RtspDeviceScopedReader;
import com.ruoyi.rtsp.service.impl.RtspDeviceServiceImpl;
import com.ruoyi.web.controller.onvif.OnvifDeviceController;
import com.ruoyi.web.controller.rtsp.RtspDeviceController;
import com.ruoyi.wvp.media.service.RtspPreviewSessionService;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real MyBatis SQL + service/AOP + preview controllers; only media I/O is mocked. */
public class ProtocolTenantFlowIntegrationTest {
    private SqlSession sql;
    private Connection db;
    private RtspDeviceMapper rtspMapper;
    private OnvifDeviceMapper onvifMapper;
    private RtspDeviceServiceImpl rtsp;
    private OnvifDeviceServiceImpl onvif;
    private RtspDeviceController rtspController;
    private OnvifDeviceController onvifController;
    private RtspPreviewSessionService previews;

    @Before public void setup() throws Exception {
        UnpooledDataSource ds = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = ds.getConnection();
        try (Statement s=db.createStatement()) {
            s.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(50),ancestors varchar(100))");
            s.execute("insert into sys_dept values(20,'fixture','0'),(30,'other','0')");
            String columns="id bigint auto_increment primary key, tenant_id varchar(64),dept_id bigint,ip varchar(50),"
                    + "name varchar(30),user_name varchar(20),password varchar(50),channel varchar(20),url varchar(255),"
                    + "firm varchar(30),lat varchar(30),lng varchar(30),address_map varchar(255),create_by varchar(64),"
                    + "create_time timestamp,update_by varchar(64),update_time timestamp,remark varchar(500),"
                    + "play_type varchar(2),stream_id varchar(100),easy_NTS_url varchar(255)";
            s.execute("create table rtsp_device("+columns+")");
            s.execute("create table onvif_device("+columns+",model varchar(20),firmware_version varchar(100),stream_uris varchar(4000))");
        }
        Configuration config=new Configuration(new Environment("fixture", new JdbcTransactionFactory(), ds));
        config.getTypeAliasRegistry().registerAlias("RtspDevice",RtspDevice.class);
        config.getTypeAliasRegistry().registerAlias("OnvifDevice",OnvifDevice.class);
        parse(config,"mapper/rtsp/RtspDeviceMapper.xml");
        parse(config,"mapper/onvif/OnvifDeviceMapper.xml");
        sql=new SqlSessionFactoryBuilder().build(config).openSession(true);
        rtspMapper=sql.getMapper(RtspDeviceMapper.class); onvifMapper=sql.getMapper(OnvifDeviceMapper.class);
        rtsp=new RtspDeviceServiceImpl(); onvif=new OnvifDeviceServiceImpl();
        AspectJProxyFactory rf=new AspectJProxyFactory(new RtspDeviceScopedReader(rtspMapper,"legacy"));
        rf.addAspect(new DataScopeAspect());
        AspectJProxyFactory of=new AspectJProxyFactory(new OnvifDeviceScopedReader(onvifMapper,"legacy"));
        of.addAspect(new DataScopeAspect());
        ReflectionTestUtils.setField(rtsp,"rtspDeviceMapper",rtspMapper);
        ReflectionTestUtils.setField(rtsp,"scopedReader",rf.getProxy());
        ReflectionTestUtils.setField(rtsp,"availability",mock(ProtocolAvailabilityService.class));
        ReflectionTestUtils.setField(onvif,"onvifDeviceMapper",onvifMapper);
        ReflectionTestUtils.setField(onvif,"scopedReader",of.getProxy());
        ReflectionTestUtils.setField(onvif,"availability",mock(ProtocolAvailabilityService.class));
        ProtocolDeviceAccess access=new ProtocolDeviceAccess(rtsp,onvif,"legacy");
        previews=mock(RtspPreviewSessionService.class);
        when(previews.start(anyLong(),anyLong(),anyString())).thenReturn(Collections.singletonMap("sessionId","fixture"));
        when(previews.start(anyString(),anyLong(),anyLong(),anyString())).thenReturn(Collections.singletonMap("sessionId","fixture"));
        rtspController=new RtspDeviceController(); onvifController=new OnvifDeviceController();
        for(Object controller:Arrays.asList(rtspController,onvifController)) {
            ReflectionTestUtils.setField(controller,"previewAccess",access);
            ReflectionTestUtils.setField(controller,"previewSessions",previews);
        }
        login("tenant-a",true,"1");
    }
    private void parse(Configuration config,String resource) throws Exception {
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
        }
    }
    private void login(String tenant,boolean federated,String scope) {
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.addHeader("tenantid","forged-header");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SysRole role=new SysRole();role.setStatus("0");role.setDataScope(scope);
        role.setPermissions(new HashSet<>(Arrays.asList("rtsp:RtspDevice:list","rtsp:RtspDevice:view",
                "rtsp:RtspDevice:query","rtsp:RtspDevice:edit","rtsp:RtspDevice:remove",
                "onvif:device:list","onvif:device:play","onvif:device:query","onvif:device:edit","onvif:device:remove")));
        SysUser user=new SysUser();user.setUserId(10L);user.setDeptId(20L);user.setRoles(Collections.singletonList(role));
        LoginUser principal=new LoginUser();principal.setUser(user);principal.setUserId(10L);
        principal.setDeptId(20L);principal.setTenantId(tenant);principal.setFederated(federated);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,Collections.emptyList()));
        PermissionContextHolder.setContext("rtsp:RtspDevice:list,onvif:device:list");
    }
    private RtspDevice addRtsp() {
        RtspDevice row=new RtspDevice();row.setTenantId("forged-body");row.setDeptId(20L);
        row.setIp("203.0.113.7");row.setName("fixture");row.setFirm("1");row.setChannel("101");
        row.setUserName("fixture");row.setPassword("fixture");row.setPlayType("1");
        assertEquals(1,rtsp.insertRtspDevice(row));return row;
    }
    private OnvifDevice addOnvif() {
        OnvifDevice row=new OnvifDevice();row.setTenantId("forged-body");row.setDeptId(20L);
        row.setIp("203.0.113.8");row.setName("fixture");row.setPlayType("1");
        row.setUrl("rtsp://203.0.113.8/fixture");assertEquals(1,onvif.insertOnvifDevice(row));return row;
    }
    @After public void cleanup() throws Exception {
        SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes();
        if(sql!=null)sql.close(); if(db!=null){try(Statement s=db.createStatement()){s.execute("drop all objects");}db.close();}
    }

    @Test public void federatedTenantCanCreateReadEditPreviewRenewStopAndDeleteBothProtocols() {
        RtspDevice r=addRtsp();OnvifDevice o=addOnvif();
        assertEquals("tenant-a",rtsp.selectRtspDeviceById(r.getId()).getTenantId());
        assertEquals("tenant-a",onvif.selectOnvifDeviceById(o.getId()).getTenantId());
        r.setTenantId("tenant-b");r.setName("edited");o.setTenantId("tenant-b");o.setName("edited");
        assertEquals(1,rtsp.updateRtspDevice(r));assertEquals(1,onvif.updateOnvifDevice(o));
        assertEquals("tenant-a",rtsp.selectRtspDeviceById(r.getId()).getTenantId());
        assertEquals("tenant-a",onvif.selectOnvifDeviceById(o.getId()).getTenantId());
        assertEquals(200,rtspController.preview(r.getId()).get("code"));
        assertEquals(200,onvifController.preview(o.getId()).get("code"));
        rtspController.renewPreview(r.getId(),"fixture");rtspController.stopPreview(r.getId(),"fixture");
        onvifController.renewPreview(o.getId(),"fixture");onvifController.stopPreview(o.getId(),"fixture");
        verify(previews).start(eq(r.getId()),eq(10L),anyString());
        verify(previews).start(eq("ONVIF"),eq(o.getId()),eq(10L),anyString());
        assertEquals(1,rtsp.deleteRtspDeviceByIds(new Long[]{r.getId()}));
        assertEquals(1,onvif.deleteOnvifDeviceByIds(new Long[]{o.getId()}));
    }
    @Test public void tenantSwitchCannotEnumerateReadMutateOrUseAnotherTenantsPreviewLease() {
        RtspDevice r=addRtsp();OnvifDevice o=addOnvif();login("tenant-b",true,"1");
        RtspDevice query=new RtspDevice();query.setTenantId("tenant-a");query.getParams().put("tenantId","tenant-a");
        assertTrue(rtsp.selectRtspDeviceList(query).isEmpty());assertEquals("tenant-b",query.getParams().get("tenantId"));
        assertTrue(onvif.selectOnvifDeviceList(new OnvifDevice()).isEmpty());
        assertThrows(ServiceException.class,()->rtsp.selectRtspDeviceById(r.getId()));
        assertThrows(ServiceException.class,()->onvif.selectOnvifDeviceById(o.getId()));
        assertThrows(ServiceException.class,()->rtsp.updateRtspDevice(r));
        assertThrows(ServiceException.class,()->onvif.updateOnvifDevice(o));
        assertThrows(ServiceException.class,()->rtsp.deleteRtspDeviceByIds(new Long[]{r.getId()}));
        assertThrows(ServiceException.class,()->onvif.deleteOnvifDeviceByIds(new Long[]{o.getId()}));
        assertThrows(ServiceException.class,()->rtspController.preview(r.getId()));
        assertThrows(ServiceException.class,()->rtspController.renewPreview(r.getId(),"fixture"));
        assertThrows(ServiceException.class,()->rtspController.stopPreview(r.getId(),"fixture"));
        assertThrows(ServiceException.class,()->onvifController.preview(o.getId()));
        assertThrows(ServiceException.class,()->onvifController.renewPreview(o.getId(),"fixture"));
        assertThrows(ServiceException.class,()->onvifController.stopPreview(o.getId(),"fixture"));
        verifyNoInteractions(previews);
        addOnvif(); // Same network IP in another tenant must neither disclose nor block on tenant A's row.
    }
    @Test public void localAndNativeSsoSessionsRetainTheirOwnTenantSemantics() throws Exception {
        try(Statement s=db.createStatement()) {
            s.execute("insert into rtsp_device(name,play_type,url) values('legacy','1','rtsp://203.0.113.1/fixture')");
            s.execute("insert into onvif_device(name,play_type,url) values('legacy','1','rtsp://203.0.113.2/fixture')");
        }
        login(null,false,"1");assertEquals(1,rtsp.selectRtspDeviceList(new RtspDevice()).size());
        assertEquals(1,onvif.selectOnvifDeviceList(new OnvifDevice()).size());
        assertEquals("legacy",addRtsp().getTenantId());
        login("sso-tenant",false,"1");assertTrue(rtsp.selectRtspDeviceList(new RtspDevice()).isEmpty());
        assertEquals("sso-tenant",addRtsp().getTenantId());
        assertEquals("sso-tenant",addOnvif().getTenantId());
        login(null,true,"1");assertThrows(ServiceException.class,this::addRtsp);
        assertThrows(ServiceException.class,this::addOnvif);
    }
    @Test public void updateAndDeleteSqlAlsoEnforceTenantEvenIfCalledWithoutServiceValidation() {
        RtspDevice r=addRtsp();OnvifDevice o=addOnvif();
        r.getParams().put("tenantId","tenant-b");r.getParams().put("defaultTenantId","legacy");
        o.getParams().put("tenantId","tenant-b");o.getParams().put("defaultTenantId","legacy");
        assertEquals(0,rtspMapper.updateRtspDevice(r));assertEquals(0,onvifMapper.updateOnvifDevice(o));
        assertEquals(0,rtspMapper.deleteRtspDeviceByIds(new Long[]{r.getId()},"tenant-b","legacy"));
        assertEquals(0,onvifMapper.deleteOnvifDeviceByIds(new Long[]{o.getId()},"tenant-b","legacy"));
    }
    @Test public void batchDeleteValidatesEveryRowBeforeDeletingAnyAndDepartmentScopeStillApplies() {
        RtspDevice r=addRtsp();OnvifDevice o=addOnvif();login("tenant-b",true,"1");
        RtspDevice other=addRtsp();OnvifDevice otherOnvif=addOnvif();login("tenant-a",true,"1");
        assertThrows(ServiceException.class,()->rtsp.deleteRtspDeviceByIds(new Long[]{r.getId(),other.getId()}));
        assertThrows(ServiceException.class,()->onvif.deleteOnvifDeviceByIds(new Long[]{o.getId(),otherOnvif.getId()}));
        assertNotNull(rtsp.selectRtspDeviceById(r.getId()));assertNotNull(onvif.selectOnvifDeviceById(o.getId()));
        login("tenant-a",false,"3");
        r.setDeptId(30L);o.setDeptId(30L);rtsp.updateRtspDevice(r);onvif.updateOnvifDevice(o);
        assertThrows(ServiceException.class,()->rtspController.preview(r.getId()));
        assertThrows(ServiceException.class,()->onvifController.preview(o.getId()));
    }
    @Test public void clientJsonCannotChooseOrReassignOwnership() throws Exception {
        ObjectMapper json=new ObjectMapper();
        assertNull(json.readValue("{\"tenantId\":\"tenant-b\"}",RtspDevice.class).getTenantId());
        assertNull(json.readValue("{\"tenantId\":\"tenant-b\"}",OnvifDevice.class).getTenantId());
    }
    @Test public void topTenantOrAdministratorDoesNotBypassDeviceOwnership() {
        RtspDevice r=addRtsp();OnvifDevice o=addOnvif();
        login("configured-top-tenant",true,"1");
        LoginUser principal=(LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        principal.getUser().setUserId(1L); // Existing department-admin bypass must not bypass the tenant predicate.
        assertTrue(rtsp.selectRtspDeviceList(new RtspDevice()).isEmpty());
        assertTrue(onvif.selectOnvifDeviceList(new OnvifDevice()).isEmpty());
        assertThrows(ServiceException.class,()->rtspController.preview(r.getId()));
        assertThrows(ServiceException.class,()->onvifController.preview(o.getId()));
        verifyNoInteractions(previews);
    }
    @Test public void legacyFallbackIsDecidedByExactTrustedTenantEqualityBeforeSqlCollation() {
        login("LEGACY",true,"1");
        RtspDevice rq=new RtspDevice();rtsp.selectRtspDeviceList(rq);
        OnvifDevice oq=new OnvifDevice();onvif.selectOnvifDeviceList(oq);
        String rsql=sql.getConfiguration().getMappedStatement(RtspDeviceMapper.class.getName()+".selectRtspDeviceList").getBoundSql(rq).getSql();
        String osql=sql.getConfiguration().getMappedStatement(OnvifDeviceMapper.class.getName()+".selectOnvifDeviceList").getBoundSql(oq).getSql();
        assertFalse(rsql.contains("tenant_id is null"));assertFalse(osql.contains("tenant_id is null"));
    }
}
