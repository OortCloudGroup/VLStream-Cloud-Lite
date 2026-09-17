package com.ruoyi.isup.ehome;

import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.isup.mapper.EhomeDeviceMapper;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EhomeDevices {
    private final EhomeDeviceMapper mapper;
    public EhomeDevices(EhomeDeviceMapper mapper) { this.mapper = mapper; }

    @DataScope(deptAlias = "d")
    public List<EhomeDevice> list(EhomeDevice query) {
        // This installation has administrator accounts whose user ID is not 1.
        // Honor the explicitly granted administrator/all-data role as well as the legacy ID check.
        if (hasAllDataAdminRole(SecurityUtils.getLoginUser())) query.getParams().put("dataScope", "");
        return mapper.selectDevices(query);
    }

    static boolean hasAllDataAdminRole(LoginUser user) {
        return user != null && user.getPermissions() != null && user.getPermissions().contains("*:*:*")
                && user.getUser() != null && user.getUser().getRoles() != null
                && user.getUser().getRoles().stream().anyMatch(role -> "admin".equals(role.getRoleKey())
                    && "1".equals(role.getDataScope()) && "0".equals(role.getStatus()));
    }

    public void updateMetadata(EhomeDevice device) { mapper.updateMetadata(device); }
}
