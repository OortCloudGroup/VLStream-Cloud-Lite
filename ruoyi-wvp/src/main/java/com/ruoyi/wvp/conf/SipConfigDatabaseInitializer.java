package com.ruoyi.wvp.conf;

import com.ruoyi.wvp.mapper.SipConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * 启动时用数据库中的国标 SIP 身份覆盖 application.yml 中的 sip.id / sip.domain。
 * 必须排在 SipLayer(@Order(10)) 之前执行，保证 SIP 服务启动时使用最终生效的身份。
 * 表内无数据或数据非法时保持 application.yml（含环境变量）的配置不变。
 */
@Slf4j
@Component
@Order(value = 1)
public class SipConfigDatabaseInitializer implements CommandLineRunner {

    private static final String SIP_ID_PATTERN = "\\d{20}";

    private static final String SIP_DOMAIN_PATTERN = "\\d{10}";

    @Autowired
    private SipConfigMapper sipConfigMapper;

    @Autowired
    private SipConfig sipConfig;

    @Override
    public void run(String... args) {
        try {
            SipIdentityConfig stored = sipConfigMapper.selectDefault();
            if (stored == null) {
                return;
            }
            if (ObjectUtils.isEmpty(stored.getSipId()) || ObjectUtils.isEmpty(stored.getSipDomain())) {
                return;
            }
            if (!stored.getSipId().matches(SIP_ID_PATTERN) || !stored.getSipDomain().matches(SIP_DOMAIN_PATTERN)) {
                log.warn("[SIP服务] 数据库中的国标身份格式非法，继续使用 application.yml：编号={}, 域={}",
                        stored.getSipId(), stored.getSipDomain());
                return;
            }
            sipConfig.setId(stored.getSipId());
            sipConfig.setDomain(stored.getSipDomain());
            log.info("[SIP服务] 已应用数据库中的国标身份：编号={}, 域={}", stored.getSipId(), stored.getSipDomain());
        } catch (Exception e) {
            log.warn("[SIP服务] 读取数据库国标身份配置失败，继续使用 application.yml", e);
        }
    }
}
