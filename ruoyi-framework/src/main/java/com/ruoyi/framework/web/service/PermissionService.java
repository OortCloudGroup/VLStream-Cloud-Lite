package com.ruoyi.framework.web.service;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.CollectionUtils;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.security.context.FederatedMenuPermissions;
import com.ruoyi.framework.security.context.PermissionContextHolder;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.beans.factory.annotation.Value;

/**
 * RuoYi首创 自定义权限实现，ss取自SpringSecurity首字母
 * 
 * @author ruoyi
 */
@Service("ss")
public class PermissionService
{
    @Value("${token.tenant-type:${token.tenantType:${SSO_TENANT_TYPE:single}}}")
    private String tenantType = "single";
    @Value("${vlstream.device.default-tenant-id:000000}")
    private String defaultTenant = "000000";
    @Value("${vlstream.device.multi-tenant-default-tenant-id:0e391fd7-1033-4f09-88c0-187582fee462}")
    private String multiTenantDefault = "0e391fd7-1033-4f09-88c0-187582fee462";
    /**
     * 验证用户是否具备某权限
     * 
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    public boolean hasPermi(String permission)
    {
        if (StringUtils.isEmpty(permission))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNotNull(loginUser) && loginUser.isFederated())
        {
            PermissionContextHolder.setContext(permission);
            return federatedAllowed(loginUser, permission.trim());
        }
        if (StringUtils.isNull(loginUser) || CollectionUtils.isEmpty(loginUser.getPermissions()))
        {
            return false;
        }
        PermissionContextHolder.setContext(permission);
        return hasPermissions(loginUser.getPermissions(), permission);
    }

    /**
     * 验证用户是否不具备某权限，与 hasPermi逻辑相反
     *
     * @param permission 权限字符串
     * @return 用户是否不具备某权限
     */
    public boolean lacksPermi(String permission)
    {
        return hasPermi(permission) != true;
    }

    /**
     * 验证用户是否具有以下任意一个权限
     *
     * @param permissions 以 PERMISSION_DELIMETER 为分隔符的权限列表
     * @return 用户是否具有以下任意一个权限
     */
    public boolean hasAnyPermi(String permissions)
    {
        if (StringUtils.isEmpty(permissions))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNotNull(loginUser) && loginUser.isFederated())
        {
            for (String permission : permissions.split(Constants.PERMISSION_DELIMETER))
            {
                if (permission != null && federatedAllowed(loginUser, permission.trim()))
                {
                    PermissionContextHolder.setContext(permissions);
                    return true;
                }
            }
            return false;
        }
        if (StringUtils.isNull(loginUser) || CollectionUtils.isEmpty(loginUser.getPermissions()))
        {
            return false;
        }
        PermissionContextHolder.setContext(permissions);
        Set<String> authorities = loginUser.getPermissions();
        for (String permission : permissions.split(Constants.PERMISSION_DELIMETER))
        {
            if (permission != null && hasPermissions(authorities, permission))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断用户是否拥有某个角色
     * 
     * @param role 角色字符串
     * @return 用户是否具备某角色
     */
    public boolean hasRole(String role)
    {
        if (StringUtils.isEmpty(role))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNull(loginUser) || CollectionUtils.isEmpty(loginUser.getUser().getRoles()))
        {
            return false;
        }
        for (SysRole sysRole : loginUser.getUser().getRoles())
        {
            String roleKey = sysRole.getRoleKey();
            if (Constants.SUPER_ADMIN.equals(roleKey) || roleKey.equals(StringUtils.trim(role)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证用户是否不具备某角色，与 isRole逻辑相反。
     *
     * @param role 角色名称
     * @return 用户是否不具备某角色
     */
    public boolean lacksRole(String role)
    {
        return hasRole(role) != true;
    }

    /**
     * 验证用户是否具有以下任意一个角色
     *
     * @param roles 以 ROLE_NAMES_DELIMETER 为分隔符的角色列表
     * @return 用户是否具有以下任意一个角色
     */
    public boolean hasAnyRoles(String roles)
    {
        if (StringUtils.isEmpty(roles))
        {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (StringUtils.isNull(loginUser) || CollectionUtils.isEmpty(loginUser.getUser().getRoles()))
        {
            return false;
        }
        for (String role : roles.split(Constants.ROLE_DELIMETER))
        {
            if (hasRole(role))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否包含权限
     * 
     * @param permissions 权限列表
     * @param permission 权限字符串
     * @return 用户是否具备某权限
     */
    private boolean hasPermissions(Set<String> permissions, String permission)
    {
        String normalizedPermission = StringUtils.trim(permission);
        return permissions.stream().anyMatch(grantedPermission ->
                Constants.ALL_PERMISSION.equals(grantedPermission)
                        || PatternMatchUtils.simpleMatch(grantedPermission, normalizedPermission));
    }

    private boolean federatedAllowed(LoginUser user, String permission) {
        if (requiresLegacyDefaultTenant(permission)) {
            String mode = tenantType == null ? "" : tenantType.trim();
            if (!"single".equalsIgnoreCase(mode) && !"multi".equalsIgnoreCase(mode)) return false;
            String tenant = "multi".equalsIgnoreCase(mode) ? multiTenantDefault : defaultTenant;
            if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,64}")
                || ("multi".equalsIgnoreCase(mode) && "000000".equals(tenant))
                || !tenant.equals(user.getTenantId())) return false;
        }
        if (FederatedMenuPermissions.allows(user.getPermissions(), permission)) return true;
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) return false;
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return FederatedMenuPermissions.allowsSquareRequest(user.getPermissions(), permission,
            request.getMethod(), request.getServletPath(), request.getParameter("lease"))
            || FederatedMenuPermissions.allowsEventLocation(user.getPermissions(), permission,
                request.getMethod(), request.getServletPath());
    }

    /** Shared video endpoints keep their native WVP behavior and require the VLS square page for federation. */
    public boolean canUseVideoSquare() {
        LoginUser user = SecurityUtils.getLoginUser();
        if (user == null) return false;
        if (!user.isFederated()) return true;
        Set<String> granted = user.getPermissions();
        return granted != null && (granted.contains(FederatedMenuPermissions.SQUARE) || granted.contains("*:*:*"));
    }

    public boolean canUseFederatedVideoSquare() {
        LoginUser user = SecurityUtils.getLoginUser();
        return user != null && user.isFederated() && canUseVideoSquare();
    }

    private boolean requiresLegacyDefaultTenant(String permission) {
        return permission.startsWith("wvp:") || permission.startsWith("gb:")
            || permission.startsWith("isup:") || permission.startsWith("ehome:")
            || permission.startsWith("dahua:") || permission.startsWith("custom:");
    }
}
