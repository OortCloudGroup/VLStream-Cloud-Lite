package com.ruoyi.web.service;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.deviceavailability.ProtocolAvailabilityService;
import com.ruoyi.common.deviceclassification.domain.DeviceCategory;
import com.ruoyi.common.deviceclassification.domain.DeviceCategoryRelation;
import com.ruoyi.common.deviceclassification.domain.DeviceCategoryTreeNode;
import com.ruoyi.common.deviceclassification.mapper.DeviceClassificationMapper;
import com.ruoyi.common.deviceclassification.service.DeviceClassificationService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.aspectj.DataScopeAspect;
import com.ruoyi.framework.security.context.PermissionContextHolder;
import com.ruoyi.rtsp.domain.RtspDevice;
import com.ruoyi.rtsp.mapper.RtspDeviceMapper;
import com.ruoyi.rtsp.service.RtspClassificationScope;
import com.ruoyi.rtsp.service.RtspDeviceScopedReader;
import com.ruoyi.rtsp.service.impl.RtspDeviceServiceImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RtspDeviceTenantIsolationTest {
    @Before public void request() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
    }
    @After public void clear() {
        SecurityContextHolder.clearContext();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    private void login(String tenant) {
        SysRole role = new SysRole(); role.setStatus("0"); role.setDataScope("3");
        role.setPermissions(new java.util.HashSet<>(Arrays.asList("rtsp:RtspDevice:list",
                "rtsp:RtspDevice:query", "rtsp:RtspDevice:view", "rtsp:RtspDevice:edit",
                "rtsp:RtspDevice:remove", "rtsp:RtspDevice:export")));
        PermissionContextHolder.setContext("rtsp:RtspDevice:query");
        SysUser user = new SysUser(); user.setUserId(10L); user.setDeptId(20L);
        user.setRoles(Collections.singletonList(role));
        LoginUser principal = new LoginUser(); principal.setUser(user); principal.setTenantId(tenant);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }

    private RtspDeviceScopedReader reader(RtspDeviceMapper mapper) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new RtspDeviceScopedReader(mapper, "default"));
        factory.addAspect(new DataScopeAspect());
        return factory.getProxy();
    }

    @Test public void listAndMapOverwriteForgedTenantFlagAndRecomputeScopeAfterSwitch() {
        login("foreign");
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDeviceScopedReader reader = reader(mapper);
        RtspDevice query = new RtspDevice(); query.getParams().put("legacyTenantVisible", true);
        query.getParams().put("dataScope", " OR 1=1");
        reader.list(query, false);
        assertEquals("foreign", query.getParams().get("tenantId"));
        assertFalse(query.getParams().get("dataScope").toString().contains("1=1"));
        assertTrue(query.getParams().get("dataScope").toString().contains("d.dept_id = 20"));
        login("default"); reader.list(query, true);
        assertEquals("default", query.getParams().get("tenantId"));
        verify(mapper).selectRtspDeviceList(query); verify(mapper).selectRtspDeviceListMap(query);
    }

    @Test public void missingFederatedTenantAndUnauthenticatedRequestsFailClosed() {
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDeviceScopedReader reader = reader(mapper);
        assertThrows(ServiceException.class, () -> reader.list(new RtspDevice(), false));
        login(null);
        LoginUser principal = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        principal.setFederated(true);
        assertThrows(ServiceException.class, () -> reader.list(new RtspDevice(), false));
        verifyNoInteractions(mapper);
    }

    @Test public void legacyLocalSessionStillUsesConfiguredDefaultTenant() {
        login(null);
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDevice query = new RtspDevice(); reader(mapper).list(query, false);
        assertEquals("default", query.getParams().get("tenantId"));
    }

    @Test public void restrictedAndDisabledRolesRemainDeniedInBothDirectoryViews() {
        login("default");
        SysRole role = ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser().getRoles().get(0);
        RtspDeviceScopedReader reader = reader(mock(RtspDeviceMapper.class));
        role.setDataScope("5"); RtspDevice query = new RtspDevice(); reader.list(query, false);
        assertTrue(query.getParams().get("dataScope").toString().contains("d.dept_id = 0"));
        role.setDataScope("1"); role.setStatus("1"); reader.list(query, true);
        assertTrue(query.getParams().get("dataScope").toString().contains("d.dept_id = 0"));
    }

    @Test public void combinedClassificationSqlUsesAuthorizedRtspKeysOrAnEmptyPredicate() throws Exception {
        Configuration config = new Configuration(); String resource = "mapper/common/DeviceClassificationMapper.xml";
        new XMLMapperBuilder(getClass().getClassLoader().getResourceAsStream(resource), config,
                resource, config.getSqlFragments()).parse();
        Map<String,Object> query = new java.util.HashMap<>();
        query.put("tenantId", "default"); query.put("defaultTenantId", "default");
        query.put("categoryType", "REGION"); query.put("categoryId", 1L);
        for (String name : Arrays.asList("selectLogicalRelations", "selectLogicalDeviceIds", "countLogicalDevices")) {
            query.put("rtspDeviceKeys", Collections.emptyList());
            String denied = config.getMappedStatement(DeviceClassificationMapper.class.getName() + "." + name)
                .getBoundSql(query).getSql().replaceAll("\\s+", " ");
            assertTrue(denied.contains("tenant_id = ?")); assertTrue(denied.contains("and 1=0"));
            query.put("rtspDeviceKeys", Collections.singletonList("7"));
            BoundSql allowed = config.getMappedStatement(DeviceClassificationMapper.class.getName() + "." + name).getBoundSql(query);
            assertTrue(allowed.getSql().replaceAll("\\s+", " ").contains("and id in"));
            assertTrue(allowed.getParameterMappings().stream().anyMatch(p -> p.getProperty().startsWith("__frch_rtspKey")));
        }
    }

    @Test public void actualListAndPaginationCountSqlShareTenantAndDepartmentPredicates() throws Exception {
        login("foreign");
        RtspDevice query = new RtspDevice();
        reader(mock(RtspDeviceMapper.class)).list(query, false);
        Configuration config = new Configuration();
        config.getTypeAliasRegistry().registerAlias("RtspDevice", RtspDevice.class);
        String resource = "mapper/rtsp/RtspDeviceMapper.xml";
        new XMLMapperBuilder(getClass().getClassLoader().getResourceAsStream(resource), config,
                resource, config.getSqlFragments()).parse();
        for (String name : Arrays.asList("selectRtspDeviceList", "selectRtspDeviceListMap")) {
            BoundSql bound = config.getMappedStatement(RtspDeviceMapper.class.getName() + "." + name).getBoundSql(query);
            String sql = bound.getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("r.tenant_id = ?")); assertTrue(sql.contains("d.dept_id = 20"));
            assertTrue(bound.getParameterMappings().stream().anyMatch(p -> "params.tenantId".equals(p.getProperty())));
            String count = new com.github.pagehelper.parser.CountSqlParser().getSmartCountSql(sql);
            assertTrue(count.contains("r.tenant_id = ?")); assertTrue(count.contains("d.dept_id = 20"));
        }
    }

    @Test public void queryOnlyRoleKeepsItsDepartmentScopeWithoutListPermission() {
        login("default");
        LoginUser principal = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        principal.getUser().getRoles().get(0).setPermissions(Collections.singleton("rtsp:RtspDevice:query"));
        PermissionContextHolder.setContext("rtsp:RtspDevice:query");
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDevice row = new RtspDevice(); row.setId(7L);
        when(mapper.selectRtspDeviceList(any())).thenReturn(Collections.singletonList(row));
        assertSame(row, service(mapper, reader(mapper)).selectRtspDeviceById(7L));
        org.mockito.ArgumentCaptor<RtspDevice> captured = org.mockito.ArgumentCaptor.forClass(RtspDevice.class);
        verify(mapper).selectRtspDeviceList(captured.capture());
        assertTrue(captured.getValue().getParams().get("dataScope").toString().contains("d.dept_id = 20"));
        RtspDevice listQuery = new RtspDevice(); reader(mapper).list(listQuery, false);
        assertTrue(listQuery.getParams().get("dataScope").toString().contains("d.dept_id = 0"));
    }

    @Test public void broadListRoleDoesNotOverrideRestrictedEditOrRemoveScope() {
        login("default");
        LoginUser principal = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysRole operationRole = principal.getUser().getRoles().get(0);
        operationRole.setPermissions(new java.util.HashSet<>(Arrays.asList("rtsp:RtspDevice:edit", "rtsp:RtspDevice:remove")));
        SysRole listRole = new SysRole(); listRole.setStatus("0"); listRole.setDataScope("1");
        listRole.setPermissions(Collections.singleton("rtsp:RtspDevice:list"));
        principal.getUser().setRoles(Arrays.asList(listRole, operationRole));
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDevice outside = new RtspDevice(); outside.setId(7L); outside.setDeptId(30L);
        when(mapper.selectRtspDeviceList(any())).thenAnswer(call -> {
            RtspDevice query = call.getArgument(0);
            String scope = String.valueOf(query.getParams().get("dataScope"));
            assertTrue(scope.contains("d.dept_id = 20"));
            return Collections.emptyList();
        });
        RtspDeviceServiceImpl service = service(mapper, reader(mapper));
        PermissionContextHolder.setContext("rtsp:RtspDevice:edit");
        assertThrows(ServiceException.class, () -> service.updateRtspDevice(outside));
        PermissionContextHolder.setContext("rtsp:RtspDevice:remove");
        assertThrows(ServiceException.class, () -> service.deleteRtspDeviceByIds(new Long[]{7L}));
        verify(mapper, never()).updateRtspDevice(any());
        verify(mapper, never()).deleteRtspDeviceByIds(any(), any(), any());
    }

    @Test public void viewAndExportReadsUseTheirCheckedPermission() {
        login("default");
        LoginUser principal = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDeviceServiceImpl service = service(mapper, reader(mapper));
        for (String permission : Arrays.asList("rtsp:RtspDevice:view", "rtsp:RtspDevice:export")) {
            principal.getUser().getRoles().get(0).setPermissions(Collections.singleton(permission));
            PermissionContextHolder.setContext(permission);
            RtspDevice query = new RtspDevice(); service.selectRtspDeviceList(query);
            assertTrue(query.getParams().get("dataScope").toString().contains("d.dept_id = 20"));
        }
    }

    @Test public void operationScopeCannotBypassForeignOrMissingFederatedTenant() {
        RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        login("foreign"); PermissionContextHolder.setContext("rtsp:RtspDevice:edit");
        RtspDevice query = new RtspDevice(); query.getParams().put("legacyTenantVisible", true);
        reader(mapper).forOperation(query, false);
        assertEquals("foreign", query.getParams().get("tenantId"));
        login(null);
        ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).setFederated(true);
        assertThrows(ServiceException.class, () -> reader(mapper).forOperation(new RtspDevice(), false));
    }
    private RtspDeviceServiceImpl service(RtspDeviceMapper mapper, RtspDeviceScopedReader reader) {
        RtspDeviceServiceImpl service = new RtspDeviceServiceImpl();
        ReflectionTestUtils.setField(service, "rtspDeviceMapper", mapper);
        ReflectionTestUtils.setField(service, "scopedReader", reader);
        ReflectionTestUtils.setField(service, "availability", mock(ProtocolAvailabilityService.class));
        return service;
    }

    @Test public void foreignTenantCannotReadUpdateDeleteOrCreateLegacyRows() {
        login("foreign"); RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDeviceServiceImpl service = service(mapper, reader(mapper));
        RtspDevice row = new RtspDevice(); row.setId(7L);
        assertThrows(ServiceException.class, () -> service.selectRtspDeviceById(7L));
        assertThrows(ServiceException.class, () -> service.updateRtspDevice(row));
        assertThrows(ServiceException.class, () -> service.deleteRtspDeviceByIds(new Long[]{7L}));
        verify(mapper, never()).insertRtspDevice(any());
    }

    @Test public void detailAndBatchMutationRequireAllRowsVisibleInScopedDirectory() {
        login("default"); RtspDeviceMapper mapper = mock(RtspDeviceMapper.class);
        RtspDevice row = new RtspDevice(); row.setId(7L);
        when(mapper.selectRtspDeviceList(any())).thenAnswer(call ->
                Long.valueOf(7L).equals(((RtspDevice) call.getArgument(0)).getId())
                    ? Collections.singletonList(row) : Collections.emptyList());
        RtspDeviceServiceImpl service = service(mapper, reader(mapper));
        assertSame(row, service.selectRtspDeviceById(7L));
        assertThrows(ServiceException.class, () -> service.selectRtspDeviceById(8L));
        assertThrows(ServiceException.class, () -> service.deleteRtspDeviceByIds(new Long[]{7L,8L}));
        verify(mapper, never()).selectRtspDeviceById(any());
        verify(mapper, never()).deleteRtspDeviceByIds(any(), any(), any());
        verify(mapper, never()).updateRtspDevice(any());
    }

    @Test @SuppressWarnings("unchecked") public void rtspClassificationCountsAndAssignmentsUseSameVisibleDirectory() {
        login("default"); RtspDeviceMapper devices = mock(RtspDeviceMapper.class);
        RtspDevice row = new RtspDevice(); row.setId(7L);
        when(devices.selectRtspDeviceList(any())).thenReturn(Collections.singletonList(row));
        DeviceClassificationMapper mapper = mock(DeviceClassificationMapper.class);
        DeviceClassificationService service = new DeviceClassificationService(mapper);
        ReflectionTestUtils.setField(service, "defaultTenantId", "default");
        ReflectionTestUtils.setField(service, "protocolScopes", Collections.singletonList(new RtspClassificationScope(reader(devices))));
        DeviceCategory category = new DeviceCategory(); category.setId(1L); category.setParentId(0L);
        when(mapper.selectCategoriesByType("REGION")).thenReturn(Collections.singletonList(category));
        DeviceCategoryRelation visible = new DeviceCategoryRelation(); visible.setCategoryId(1L); visible.setDeviceKey("7");
        DeviceCategoryRelation hidden = new DeviceCategoryRelation(); hidden.setCategoryId(1L); hidden.setDeviceKey("8");
        when(mapper.selectRelations("RTSP", "REGION", "default", "default")).thenReturn(Arrays.asList(visible,hidden));
        Map<String,Object> result = service.tree("REGION", "RTSP");
        assertEquals(1, result.get("totalCount")); assertEquals(0, result.get("unclassifiedCount"));
        assertEquals(1, ((List<DeviceCategoryTreeNode>) result.get("tree")).get(0).getDeviceCount());
        assertThrows(ServiceException.class, () -> service.getAssignment("RTSP", "8"));
        verify(mapper, never()).selectDeviceRelations("RTSP", "8");
    }
}
