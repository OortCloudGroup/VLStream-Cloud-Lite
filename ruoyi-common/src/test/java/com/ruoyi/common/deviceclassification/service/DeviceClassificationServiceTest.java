package com.ruoyi.common.deviceclassification.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.common.deviceclassification.domain.DeviceCategory;
import com.ruoyi.common.deviceclassification.domain.DeviceCategoryRelation;
import com.ruoyi.common.deviceclassification.domain.DeviceCategoryTreeNode;
import com.ruoyi.common.deviceclassification.mapper.DeviceClassificationMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;

public class DeviceClassificationServiceTest {
    private DeviceClassificationMapper mapper;
    private DeviceClassificationService service;

    @Before
    public void setUp() {
        com.ruoyi.common.core.domain.model.LoginUser user = new com.ruoyi.common.core.domain.model.LoginUser();
        user.setTenantId("tenant-a");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null));
        mapper = mock(DeviceClassificationMapper.class);
        service = new DeviceClassificationService(mapper);
    }

    @After
    public void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void allProtocolTreeUsesLogicalDeviceIdsAndDeduplicatesCounts() {
        DeviceCategory root = category(1L, 0L, "区域一");
        DeviceCategory child = category(2L, 1L, "子区域");
        when(mapper.selectCategoriesByType(DeviceClassificationService.REGION))
            .thenReturn(Arrays.asList(root, child));
        when(mapper.selectLogicalRelations(DeviceClassificationService.REGION, "tenant-a", "000000"))
            .thenReturn(Arrays.asList(
                relation(1L, "ISUP", "device-1"),
                relation(2L, "VLSTREAM", "device-1"),
                relation(2L, "VLSTREAM", "device-2")
            ));
        when(mapper.countLogicalDevices("tenant-a", "000000")).thenReturn(3);

        Map<String, Object> result = service.tree("region", "all");

        List<DeviceCategoryTreeNode> tree = (List<DeviceCategoryTreeNode>) result.get("tree");
        assertEquals(1, tree.size());
        assertEquals(2, tree.get(0).getDeviceCount());
        assertEquals(2, tree.get(0).getChildren().get(0).getDeviceCount());
        assertEquals(3, result.get("totalCount"));
        assertEquals(1, result.get("unclassifiedCount"));
        verify(mapper).selectLogicalRelations(DeviceClassificationService.REGION, "tenant-a", "000000");
        verify(mapper).countLogicalDevices("tenant-a", "000000");
    }

    @Test
    public void mapperXmlRegistersLogicalClassificationQueries() throws Exception {
        String resource = "mapper/common/DeviceClassificationMapper.xml";
        Configuration configuration = new Configuration();
        XMLMapperBuilder builder = new XMLMapperBuilder(
            getClass().getClassLoader().getResourceAsStream(resource),
            configuration,
            resource,
            configuration.getSqlFragments()
        );

        builder.parse();

        String namespace = "com.ruoyi.common.deviceclassification.mapper.DeviceClassificationMapper.";
        assertTrue(configuration.hasStatement(namespace + "selectLogicalRelations"));
        assertTrue(configuration.hasStatement(namespace + "selectLogicalDeviceIds"));
        assertTrue(configuration.hasStatement(namespace + "countLogicalDevices"));
    }

    @Test
    public void logicalDeviceIdsIncludeSelectedCategoryAndItsDescendants() {
        DeviceCategory category = category(2L, 1L, "子区域");
        when(mapper.selectCategoryById(2L)).thenReturn(category);
        when(mapper.selectLogicalDeviceIds(DeviceClassificationService.REGION, 2L, "tenant-a", "000000"))
            .thenReturn(Arrays.asList("device-1", "device-2"));

        List<String> result = service.logicalDeviceIds("REGION", 2L);

        assertEquals(Arrays.asList("device-1", "device-2"), result);
    }

    @Test
    public void protocolSpecificTreeKeepsExistingBehavior() {
        when(mapper.selectCategoriesByType(DeviceClassificationService.TAG)).thenReturn(Collections.emptyList());
        when(mapper.selectRelations("VLSTREAM", DeviceClassificationService.TAG, "tenant-a", "000000")).thenReturn(Collections.emptyList());
        when(mapper.countProtocolDevices("VLSTREAM", "tenant-a", "000000")).thenReturn(4);

        Map<String, Object> result = service.tree("TAG", "VLSTREAM");

        assertNotNull(result.get("tree"));
        assertEquals(4, result.get("totalCount"));
        assertEquals(4, result.get("unclassifiedCount"));
    }

    private DeviceCategory category(Long id, Long parentId, String name) {
        DeviceCategory category = new DeviceCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setCategoryType(DeviceClassificationService.REGION);
        category.setCategoryName(name);
        return category;
    }

    private DeviceCategoryRelation relation(Long categoryId, String protocol, String logicalDeviceId) {
        DeviceCategoryRelation relation = new DeviceCategoryRelation();
        relation.setCategoryId(categoryId);
        relation.setCategoryType(DeviceClassificationService.REGION);
        relation.setProtocolType(protocol);
        relation.setDeviceKey(logicalDeviceId);
        return relation;
    }
}
