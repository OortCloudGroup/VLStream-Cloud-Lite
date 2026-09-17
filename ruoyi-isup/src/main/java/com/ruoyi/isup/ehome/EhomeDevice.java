package com.ruoyi.isup.ehome;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

/** Public device view. Native session keys and device passwords never leave the SDK layer. */
@Data
public class EhomeDevice extends BaseEntity {
    private Long id;
    private Long deptId;
    private String deviceId;
    private String name;
    private String deviceSerial;
    private String ipAddress;
    private String firmwareVersion;
    private String devProtocolVersion;
    private String status;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer luserId;
    private String keyword;
    private String categoryType;
    private Long categoryId;
    private Boolean unclassified;
}
