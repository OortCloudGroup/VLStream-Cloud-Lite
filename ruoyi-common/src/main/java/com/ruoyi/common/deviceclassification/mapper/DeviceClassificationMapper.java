package com.ruoyi.common.deviceclassification.mapper;

import com.ruoyi.common.deviceclassification.domain.DeviceCategory;
import com.ruoyi.common.deviceclassification.domain.DeviceCategoryRelation;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DeviceClassificationMapper {
    DeviceCategory selectCategoryById(Long id);
    List<DeviceCategory> selectCategoriesByType(String categoryType);
    int countSiblingName(@Param("categoryType") String categoryType, @Param("parentId") Long parentId,
                         @Param("categoryName") String categoryName, @Param("excludeId") Long excludeId);
    int insertCategory(DeviceCategory category);
    int updateCategory(DeviceCategory category);
    int updateDescendantAncestors(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix,
                                  @Param("categoryId") Long categoryId);
    int countChildren(Long id);
    int countRelations(Long categoryId);
    int deleteCategory(Long id);
    List<DeviceCategoryRelation> selectRelations(@Param("protocolType") String protocolType,
        @Param("categoryType") String categoryType, @Param("tenantId") String tenantId,
        @Param("defaultTenantId") String defaultTenantId);
    List<DeviceCategoryRelation> selectLogicalRelations(@Param("categoryType") String categoryType,
        @Param("tenantId") String tenantId, @Param("defaultTenantId") String defaultTenantId);
    List<String> selectLogicalDeviceIds(@Param("categoryType") String categoryType,
        @Param("categoryId") Long categoryId, @Param("tenantId") String tenantId,
        @Param("defaultTenantId") String defaultTenantId);
    List<DeviceCategoryRelation> selectDeviceRelations(@Param("protocolType") String protocolType,
                                                        @Param("deviceKey") String deviceKey);
    int deleteDeviceTypeRelations(@Param("protocolType") String protocolType, @Param("deviceKey") String deviceKey,
                                  @Param("categoryType") String categoryType);
    int insertRelation(DeviceCategoryRelation relation);
    int deleteDeviceRelations(@Param("protocolType") String protocolType, @Param("deviceKeys") List<String> deviceKeys);
    int countProtocolDevices(@Param("protocolType") String protocolType, @Param("tenantId") String tenantId,
        @Param("defaultTenantId") String defaultTenantId);
    int countLogicalDevices(@Param("tenantId") String tenantId, @Param("defaultTenantId") String defaultTenantId);
    int countVisibleVlStreamDevice(@Param("deviceKey") String deviceKey, @Param("tenantId") String tenantId,
        @Param("defaultTenantId") String defaultTenantId);
}
