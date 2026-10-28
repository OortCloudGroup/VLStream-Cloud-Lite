package com.ruoyi.wvp.conf;

import lombok.Data;

/**
 * 国标 SIP 服务身份（编号/域），持久化在 wvp_sip_config 表。
 * 表中有值时优先于 application.yml 中的 sip.id / sip.domain。
 */
@Data
public class SipIdentityConfig {

    private String sipId;

    private String sipDomain;
}
