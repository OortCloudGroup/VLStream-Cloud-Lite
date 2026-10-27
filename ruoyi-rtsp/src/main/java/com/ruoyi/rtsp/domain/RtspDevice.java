package com.ruoyi.rtsp.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * rtsp设备对象 rtsp_device
 *
 * @author 陈江灿
 * @date 2025-04-22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RtspDevice extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /** Assigned by the authenticated server context; never accepted from JSON. */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String tenantId;

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private com.ruoyi.common.deviceavailability.AvailabilitySnapshot availability;

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public Boolean getOnline() { return availability == null ? null : availability.getOnline(); }


    /**
     * 部门ID
     */
    private Long deptId;

    /** 全局设备分类筛选参数，不写入设备表。 */
    private String categoryType;
    private Long categoryId;
    private Boolean unclassified;

    /**
     * ip
     */
    @Excel(name = "ip")
    private String ip;

    /** Optional request-only port; persisted in the existing host/URL fields. */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = PortInput.class)
    private Integer port;

    @com.fasterxml.jackson.annotation.JsonSetter("ip")
    public void readIp(com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null || value.isNull()) { this.ip = null; return; }
        if (!value.isTextual()) throw new IllegalArgumentException("RTSP主机必须为字符串");
        this.ip = value.textValue();
    }

    public static class PortInput extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            com.fasterxml.jackson.core.JsonToken token = parser.currentToken();
            String text = parser.getText();
            if ((token != com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT
                    && token != com.fasterxml.jackson.core.JsonToken.VALUE_STRING)
                    || !text.matches("[0-9]{1,5}")) {
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "RTSP端口必须为1–65535的整数");
            }
            int number = Integer.parseInt(text);
            if (number < 1 || number > 65535) {
                throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "RTSP端口必须为1–65535的整数");
            }
            return number;
        }
    }

    /**
     * 摄像头名称
     */
    @Excel(name = "摄像头名称")
    private String name;

    /**
     * 用户名
     */
    @Excel(name = "用户名")
    private String userName;

    /**
     * 密码
     */
    @Excel(name = "密码")
    private String password;

    /**
     * 通道号
     */
    @Excel(name = "通道号")
    private String channel;

    /** 播放类型（1=本地，2=推流，3=EasyNTS） */
    @Excel(name = "播放类型")
    private String playType;

    /**
     * 直播流播放地址
     */
    @Excel(name = "直播流播放地址")
    private String url;

    /** 流id */
    @Excel(name = "流id")
    private String streamId;

    /** EasyNTS播放地址 */
    @Excel(name = "EasyNTS播放地址")
    private String easyNTSUrl;

    /**
     * 设备厂商
     */
    @Excel(name = "设备厂商")
    private String firm;

    /**
     * 部门名称
     */
    private String deptName;

    /** 纬度 */
    @Excel(name = "纬度")
    private String lat;

    /** 经度 */
    @Excel(name = "经度")
    private String lng;

    /** 地图定位地址 */
    @Excel(name = "地图定位地址")
    private String addressMap;
}
