package com.ruoyi.common.utils;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;

/** Derives scope from authenticated server-side identity, never from request parameters. */
public final class VlStreamTenantContext {
    private VlStreamTenantContext() { }

    public static String currentTenant(String defaultTenant) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (user == null) throw new ServiceException("未登录", 401);
        if (StringUtils.isNotBlank(user.getTenantId())) return user.getTenantId();
        if (user.isFederated()) throw new ServiceException("平台登录会话缺少租户", 403);
        return defaultTenant;
    }
}
