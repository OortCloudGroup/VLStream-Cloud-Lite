package com.ruoyi.wvp.mapper;

import com.ruoyi.wvp.conf.SipIdentityConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

/**
 * 国标 SIP 服务身份配置访问。
 */
@Mapper
@Repository
public interface SipConfigMapper {

    @Select("SELECT sip_id AS sipId, sip_domain AS sipDomain FROM wvp_sip_config WHERE config_key = 'default'")
    SipIdentityConfig selectDefault();

    @Insert("INSERT INTO wvp_sip_config (config_key, sip_id, sip_domain) VALUES ('default', #{sipId}, #{sipDomain}) "
            + "ON DUPLICATE KEY UPDATE sip_id = #{sipId}, sip_domain = #{sipDomain}")
    int upsertDefault(@Param("sipId") String sipId, @Param("sipDomain") String sipDomain);
}
