package com.ruoyi.common.deviceclassification.service;

import com.ruoyi.common.deviceclassification.domain.DeviceCategory;
import com.ruoyi.common.deviceclassification.domain.DeviceCategoryRelation;
import com.ruoyi.common.deviceclassification.domain.DeviceCategoryTreeNode;
import com.ruoyi.common.deviceclassification.domain.DeviceClassificationAssignment;
import com.ruoyi.common.deviceclassification.mapper.DeviceClassificationMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.VlStreamTenantContext;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceClassificationService {
    public static final String REGION = "REGION";
    public static final String GROUP = "GROUP";
    public static final String TAG = "TAG";
    public static final String ALL_PROTOCOLS = "ALL";
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(REGION, GROUP, TAG));
    private static final Set<String> PROTOCOLS = new HashSet<>(Arrays.asList("ISUP", "EHOME", "RTSP", "ONVIF", "GB28181", "DAHUA", "VLSTREAM", "CUSTOM"));

    private final DeviceClassificationMapper mapper;
    @Value("${vlstream.device.default-tenant-id:000000}")
    private String defaultTenantId = "000000";

    public DeviceClassificationService(DeviceClassificationMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> tree(String categoryType, String protocolType) {
        String type = normalizeType(categoryType);
        String protocol = normalizeTreeProtocol(protocolType);
        String tenantId = VlStreamTenantContext.currentTenant(defaultTenantId);
        List<DeviceCategory> categories = mapper.selectCategoriesByType(type);
        List<DeviceCategoryRelation> relations = ALL_PROTOCOLS.equals(protocol)
            ? mapper.selectLogicalRelations(type, tenantId, defaultTenantId)
            : mapper.selectRelations(protocol, type, tenantId, defaultTenantId);

        Map<Long, Set<String>> directDevices = new HashMap<>();
        Set<String> classifiedDevices = new HashSet<>();
        for (DeviceCategoryRelation relation : relations) {
            directDevices.computeIfAbsent(relation.getCategoryId(), key -> new HashSet<>()).add(relation.getDeviceKey());
            classifiedDevices.add(relation.getDeviceKey());
        }

        Map<Long, DeviceCategoryTreeNode> nodes = new LinkedHashMap<>();
        for (DeviceCategory category : categories) {
            DeviceCategoryTreeNode node = new DeviceCategoryTreeNode();
            node.setId(category.getId());
            node.setParentId(category.getParentId());
            node.setCategoryType(category.getCategoryType());
            node.setCategoryName(category.getCategoryName());
            node.setSortNum(category.getSortNum());
            nodes.put(category.getId(), node);
        }
        List<DeviceCategoryTreeNode> roots = new ArrayList<>();
        for (DeviceCategoryTreeNode node : nodes.values()) {
            DeviceCategoryTreeNode parent = nodes.get(node.getParentId());
            if (parent == null) roots.add(node); else parent.getChildren().add(node);
        }
        for (DeviceCategoryTreeNode root : roots) collectDevices(root, directDevices);

        int total = ALL_PROTOCOLS.equals(protocol)
            ? mapper.countLogicalDevices(tenantId, defaultTenantId)
            : mapper.countProtocolDevices(protocol, tenantId, defaultTenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tree", roots);
        result.put("totalCount", total);
        result.put("unclassifiedCount", Math.max(0, total - classifiedDevices.size()));
        return result;
    }

    public List<String> logicalDeviceIds(String categoryType, Long categoryId) {
        String type = normalizeType(categoryType);
        DeviceCategory category = requiredCategory(categoryId);
        if (!type.equals(category.getCategoryType())) throw new ServiceException("分类类型不匹配");
        return mapper.selectLogicalDeviceIds(type, categoryId,
            VlStreamTenantContext.currentTenant(defaultTenantId), defaultTenantId);
    }

    private Set<String> collectDevices(DeviceCategoryTreeNode node, Map<Long, Set<String>> directDevices) {
        Set<String> devices = new HashSet<>(directDevices.getOrDefault(node.getId(), Collections.emptySet()));
        for (DeviceCategoryTreeNode child : node.getChildren()) devices.addAll(collectDevices(child, directDevices));
        node.setDeviceCount(devices.size());
        return devices;
    }

    @Transactional
    public int addCategory(DeviceCategory category) {
        prepareCategory(category, null);
        category.setCreateBy(currentUsername());
        category.setUpdateBy(category.getCreateBy());
        return mapper.insertCategory(category);
    }

    @Transactional
    public int updateCategory(DeviceCategory category) {
        if (category.getId() == null) throw new ServiceException("分类ID不能为空");
        DeviceCategory old = requiredCategory(category.getId());
        String oldPrefix = old.getAncestors() + "," + old.getId();
        prepareCategory(category, old);
        String newPrefix = category.getAncestors() + "," + category.getId();
        category.setUpdateBy(currentUsername());
        int rows = mapper.updateCategory(category);
        if (!oldPrefix.equals(newPrefix)) mapper.updateDescendantAncestors(oldPrefix, newPrefix, category.getId());
        return rows;
    }

    private void prepareCategory(DeviceCategory category, DeviceCategory old) {
        category.setCategoryType(normalizeType(category.getCategoryType()));
        if (StringUtils.isBlank(category.getCategoryName())) throw new ServiceException("分类名称不能为空");
        category.setCategoryName(category.getCategoryName().trim());
        if (category.getParentId() == null) category.setParentId(0L);
        if (category.getSortNum() == null) category.setSortNum(0);
        if (old != null && !old.getCategoryType().equals(category.getCategoryType())) throw new ServiceException("分类类型不允许修改");
        if (category.getId() != null && category.getId().equals(category.getParentId())) throw new ServiceException("分类不能选择自身作为父节点");
        if (category.getParentId() == 0L) {
            category.setAncestors("0");
        } else {
            DeviceCategory parent = requiredCategory(category.getParentId());
            if (!parent.getCategoryType().equals(category.getCategoryType())) throw new ServiceException("父节点类型不一致");
            if (category.getId() != null && containsAncestor(parent.getAncestors(), category.getId())) throw new ServiceException("不能移动到自己的子节点下");
            category.setAncestors(parent.getAncestors() + "," + parent.getId());
        }
        if (mapper.countSiblingName(category.getCategoryType(), category.getParentId(), category.getCategoryName(), category.getId()) > 0) {
            throw new ServiceException("同级分类名称已存在");
        }
    }

    @Transactional
    public int deleteCategory(Long id) {
        requiredCategory(id);
        if (mapper.countChildren(id) > 0) throw new ServiceException("该分类存在子节点，请先处理子节点");
        if (mapper.countRelations(id) > 0) throw new ServiceException("该分类已关联设备，请先移动或解除关联");
        return mapper.deleteCategory(id);
    }

    public DeviceClassificationAssignment getAssignment(String protocolType, String deviceKey) {
        String protocol = normalizeProtocol(protocolType);
        if (StringUtils.isBlank(deviceKey)) throw new ServiceException("设备标识不能为空");
        requireVlStreamOwnership(protocol, deviceKey);
        DeviceClassificationAssignment result = new DeviceClassificationAssignment();
        result.setProtocolType(protocol);
        result.setDeviceKeys(Collections.singletonList(deviceKey));
        List<Long> tags = new ArrayList<>();
        for (DeviceCategoryRelation relation : mapper.selectDeviceRelations(protocol, deviceKey)) {
            if (REGION.equals(relation.getCategoryType())) result.setRegionId(relation.getCategoryId());
            else if (GROUP.equals(relation.getCategoryType())) result.setGroupId(relation.getCategoryId());
            else if (TAG.equals(relation.getCategoryType())) tags.add(relation.getCategoryId());
        }
        result.setTagIds(tags);
        return result;
    }

    @Transactional
    public void saveAssignment(DeviceClassificationAssignment assignment) {
        String protocol = normalizeProtocol(assignment.getProtocolType());
        if (assignment.getDeviceKeys() == null || assignment.getDeviceKeys().isEmpty()) throw new ServiceException("请至少选择一台设备");
        validateCategory(assignment.getRegionId(), REGION);
        validateCategory(assignment.getGroupId(), GROUP);
        List<Long> tagIds = assignment.getTagIds() == null ? Collections.emptyList() : new ArrayList<>(new HashSet<>(assignment.getTagIds()));
        for (Long tagId : tagIds) validateCategory(tagId, TAG);
        String username = currentUsername();
        for (String rawKey : assignment.getDeviceKeys()) {
            if (StringUtils.isBlank(rawKey)) continue;
            String deviceKey = rawKey.trim();
            requireVlStreamOwnership(protocol, deviceKey);
            replaceType(protocol, deviceKey, REGION, assignment.getRegionId() == null ? Collections.emptyList() : Collections.singletonList(assignment.getRegionId()), username);
            replaceType(protocol, deviceKey, GROUP, assignment.getGroupId() == null ? Collections.emptyList() : Collections.singletonList(assignment.getGroupId()), username);
            replaceType(protocol, deviceKey, TAG, tagIds, username);
        }
    }

    private void replaceType(String protocol, String deviceKey, String type, List<Long> categoryIds, String username) {
        mapper.deleteDeviceTypeRelations(protocol, deviceKey, type);
        for (Long categoryId : categoryIds) {
            DeviceCategoryRelation relation = new DeviceCategoryRelation();
            relation.setProtocolType(protocol);
            relation.setDeviceKey(deviceKey);
            relation.setCategoryType(type);
            relation.setCategoryId(categoryId);
            relation.setCreateBy(username);
            mapper.insertRelation(relation);
        }
    }

    private void requireVlStreamOwnership(String protocol, String deviceKey) {
        if ("VLSTREAM".equals(protocol) && mapper.countVisibleVlStreamDevice(deviceKey,
                VlStreamTenantContext.currentTenant(defaultTenantId), defaultTenantId) != 1) {
            throw new ServiceException("设备不存在或不属于当前租户", 403);
        }
    }

    @Transactional
    public void removeDeviceRelations(String protocolType, List<String> deviceKeys) {
        if (deviceKeys == null || deviceKeys.isEmpty()) return;
        mapper.deleteDeviceRelations(normalizeProtocol(protocolType), deviceKeys);
    }

    private void validateCategory(Long id, String expectedType) {
        if (id == null) return;
        DeviceCategory category = requiredCategory(id);
        if (!expectedType.equals(category.getCategoryType())) throw new ServiceException("分类类型不匹配");
    }

    private DeviceCategory requiredCategory(Long id) {
        DeviceCategory category = mapper.selectCategoryById(id);
        if (category == null) throw new ServiceException("分类节点不存在");
        return category;
    }

    private boolean containsAncestor(String ancestors, Long id) {
        if (StringUtils.isBlank(ancestors)) return false;
        return Arrays.asList(ancestors.split(",")).contains(String.valueOf(id));
    }

    private String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) throw new ServiceException("不支持的分类类型");
        return normalized;
    }

    private String normalizeProtocol(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!PROTOCOLS.contains(normalized)) throw new ServiceException("不支持的协议类型");
        return normalized;
    }

    private String normalizeTreeProtocol(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return ALL_PROTOCOLS.equals(normalized) ? normalized : normalizeProtocol(normalized);
    }

    private String currentUsername() {
        try { return SecurityUtils.getUsername(); } catch (Exception ignored) { return "system"; }
    }
}
