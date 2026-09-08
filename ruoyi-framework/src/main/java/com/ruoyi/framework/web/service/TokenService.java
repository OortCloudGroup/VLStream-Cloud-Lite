package com.ruoyi.framework.web.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.AddressUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.framework.dto.PlatformLoginUser;
import com.ruoyi.framework.config.properties.AuthModeProperties;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.ISysUserService;
import eu.bitwalker.useragentutils.UserAgent;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.ruoyi.framework.constant.PlatformConstants.*;

/**
 * token验证处理
 *
 * @author qdata
 */
@Component
public class TokenService
{
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private static final String HEADER_AUTH_SOURCE = "X-WVP-Auth-Source";

    private static final String HEADER_VLSTREAM_TOKEN = "X-VLStream-Token";

    private static final String AUTH_SOURCE_VLSTREAM = "vlstream";

    private static final Set<String> VLSTREAM_PROTOCOL_PERMISSIONS = new HashSet<>(Arrays.asList(
            "isup:*", "rtsp:*", "onvif:*", "dahua:*", "wvp:*", "gb:*", "custom:*"
    ));

    // 令牌自定义标识
    @Value("${token.header}")
    private String header;

    // 令牌秘钥
    @Value("${token.secret}")
    private String secret;

    @Value("${token.verifyTokenAddress}")
    private String verifyTokenAddress;

    @Value("${token.vlstreamVerifyTokenAddress:}")
    private String vlstreamVerifyTokenAddress;

    /**
     * 可选的协议设备默认部门。为空时新增记录不写入 dept_id。
     */
    @Value("${token.vlstreamDefaultDeptId:${VLSTREAM_DEFAULT_DEPT_ID:}}")
    private String vlstreamDefaultDeptId;

    // 令牌有效期（分钟），负数表示永不过期
    @Value("${token.expireTime}")
    private int expireTime;

    protected static final long MILLIS_SECOND = 1000;

    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;

    private static final Long MILLIS_MINUTE_TEN = 20 * 60 * 1000L;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private AuthModeProperties authModeProperties;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    /**
     * 获取用户身份信息
     *
     * @return 用户信息
     */
    public LoginUser getLoginUser(HttpServletRequest request)
    {
        // 获取请求携带的令牌
        String accessToken = resolveAccessToken(request);
        if (StringUtils.isBlank(accessToken)) {
            return null;
        }

        String userKey = getTokenKey(accessToken);

        // VLStream 联邦鉴权独立于 WVP 的 SSO/local 模式。
        if (isVlstreamRequest(request)) {
            return createVlstreamLoginUser(getVlstreamLoginUser(request, accessToken), accessToken, userKey);
        }

        if (authModeProperties.isLocal()) {
            return getLocalLoginUser(accessToken);
        }

        PlatformLoginUser platformLoginUser = getPlatformLoginUser(request, accessToken);

        SysUser sysUser = sysUserMapper.selectUserByPlatformUserId(platformLoginUser.getUserId());
        if (sysUser == null) {
            sysUser = new SysUser();
            sysUser.setDeptId(100L);
            sysUser.setNickName(platformLoginUser.getUserName());
            sysUser.setUserName(platformLoginUser.getUserName());
            sysUser.setPassword("");
            sysUser.setPassword(SecurityUtils.encryptPassword("123456"));
            sysUser.setCreateTime(new Date());
            sysUser.setPlatformUserId(platformLoginUser.getUserId());
            sysUserService.insertUser(sysUser);

            List<SysUserRole> userRoleList = new ArrayList<>();
            SysUserRole sysUserRole = new SysUserRole();
            sysUserRole.setRoleId(1L);
            sysUserRole.setUserId(sysUser.getUserId());
            userRoleList.add(sysUserRole);
            userRoleMapper.batchUserRole(userRoleList);
        }
        SysRole sysRole = new SysRole();
        sysRole.setRoleIds(Collections.singletonList(1L));
        sysUser.setRoles(sysRoleMapper.selectRoleList(sysRole));

        LoginUser loginUser = new LoginUser(sysUser.getUserId(), sysUser.getDeptId(), sysUser, permissionService.getMenuPermission(sysUser));
        loginUser.setToken(accessToken);
        cacheLoginUser(userKey, loginUser);
        return loginUser;
    }

    /**
     * 解析系统自身登录签发的 JWT，并从 Redis 恢复登录用户。
     */
    private LoginUser getLocalLoginUser(String token)
    {
        try {
            Claims claims = parseToken(normalizeLocalToken(token));
            String uuid = (String) claims.get(Constants.LOGIN_USER_KEY);
            if (StringUtils.isBlank(uuid)) {
                return null;
            }
            return redisCache.getCacheObject(getTokenKey(uuid));
        } catch (Exception e) {
            log.debug("本地登录令牌解析失败: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 创建不落WVP用户、角色表的VLStream联邦身份。
     */
    private LoginUser createVlstreamLoginUser(PlatformLoginUser platformUser, String accessToken, String userKey) {
        SysUser sysUser = new SysUser();
        Long userId = toSyntheticUserId(platformUser.getUserId());
        Long deptId = toLong(vlstreamDefaultDeptId);
        sysUser.setUserId(userId);
        sysUser.setDeptId(deptId);
        sysUser.setUserName(platformUser.getUserName());
        sysUser.setNickName(platformUser.getUserName());
        sysUser.setPassword("");
        sysUser.setStatus("0");
        sysUser.setDelFlag("0");
        sysUser.setPlatformUserId(platformUser.getUserId());

        SysRole federatedRole = new SysRole();
        federatedRole.setRoleId(-1L);
        federatedRole.setRoleName("VLStream 联邦用户");
        federatedRole.setRoleKey("vlstream_federated");
        federatedRole.setDataScope("1");
        federatedRole.setStatus("0");
        federatedRole.setPermissions(new HashSet<>(VLSTREAM_PROTOCOL_PERMISSIONS));
        sysUser.setRoles(Collections.singletonList(federatedRole));

        LoginUser loginUser = new LoginUser(userId, deptId, sysUser, new HashSet<>(VLSTREAM_PROTOCOL_PERMISSIONS));
        loginUser.setFederated(true);
        loginUser.setToken(accessToken);
        cacheLoginUser(userKey, loginUser);
        return loginUser;
    }

    /**
     * 为审计字段生成稳定的负数ID，避免与WVP数据库中的正数用户ID冲突。
     */
    private Long toSyntheticUserId(String externalUserId) {
        long hash = 1125899906842597L;
        for (int i = 0; i < externalUserId.length(); i++) {
            hash = 31 * hash + externalUserId.charAt(i);
        }
        if (hash == Long.MIN_VALUE) {
            return -2L;
        }
        long absolute = Math.abs(hash);
        return absolute == 0L ? -2L : -absolute;
    }

    /**
     * 校验平台token有效性获取平台用户信息
     *
     * @param request
     * @param accessToken
     * @return
     */
    private PlatformLoginUser getPlatformLoginUser(HttpServletRequest request, String accessToken) {
        // body是具体进入应用时的验证
        String requestBody = "{\"accesstoken\": \"" + accessToken + "\"}";

        HttpResponse response = HttpRequest.post(verifyTokenAddress)
                .header(HEADER_REQUEST_TYPE, request.getHeader(HEADER_REQUEST_TYPE))
                .header(HEADER_APP_ID, request.getHeader(HEADER_APP_ID))
                .header(HEADER_SERVER_KEY, request.getHeader(HEADER_SERVER_KEY))
                .header(HEADER_ACCESS_TOKEN, accessToken).body(requestBody).timeout(1500).execute();

        String respBody = response.body();
        log.info("verifyToken respBody ================== " + respBody);

        JSONObject bodyJson = JSONUtil.parseObj(respBody);
        PlatformLoginUser platformLoginUser;
        if (200 == bodyJson.getInt("code")) {
            JSONConfig jsonConfig = JSONConfig.create().setIgnoreError(true);
            //联通环境
            String data = bodyJson.getStr("data");
            platformLoginUser = JSONUtil.toBean(data, jsonConfig, PlatformLoginUser.class);
        } else {
            log.error("网关verifyToken失败：{}", respBody);
            throw new ServiceException("网关verifyToken失败！");
        }
        return platformLoginUser;
    }

    /**
     * 校验 VLStream/SpringBlade token 并转换为 WVP 使用的平台用户模型。
     */
    private PlatformLoginUser getVlstreamLoginUser(HttpServletRequest request, String accessToken) {
        if (StringUtils.isBlank(vlstreamVerifyTokenAddress)) {
            throw new ServiceException("WVP未配置VLStream令牌校验地址");
        }

        String token = normalizeFederatedToken(accessToken);
        String bearerToken = "Bearer " + token;
        HttpResponse response;
        try {
            HttpRequest verifyRequest = HttpRequest.get(vlstreamVerifyTokenAddress)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .header("blade-auth", bearerToken);
            // VLS 本地会话 token 负责业务鉴权；平台 token 和应用身份继续供平台网关校验。
            forwardHeader(verifyRequest, request, HEADER_ACCESS_TOKEN);
            forwardHeader(verifyRequest, request, HEADER_REQUEST_TYPE);
            forwardHeader(verifyRequest, request, HEADER_APP_ID);
            forwardHeader(verifyRequest, request, HEADER_SERVER_KEY);
            forwardHeader(verifyRequest, request, HEADER_TENANT_ID);
            response = verifyRequest.timeout(1500).execute();
        } catch (Exception e) {
            log.warn("VLStream令牌校验服务调用失败: {}", e.getClass().getSimpleName());
            throw new ServiceException("VLStream令牌校验服务不可用");
        }

        if (response == null || response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new ServiceException("VLStream令牌校验服务返回异常");
        }

        String responseBody = response.body();
        if (StringUtils.isBlank(responseBody)) {
            throw new ServiceException("VLStream令牌校验未返回结果");
        }

        JSONObject bodyJson;
        try {
            bodyJson = JSONUtil.parseObj(responseBody);
        } catch (Exception e) {
            log.warn("VLStream令牌校验返回格式错误: {}", e.getClass().getSimpleName());
            throw new ServiceException("VLStream令牌校验返回格式错误");
        }

        Integer code = bodyJson.getInt("code");
        Boolean success = bodyJson.getBool("success");
        JSONObject data = bodyJson.getJSONObject("data");
        if (!Integer.valueOf(200).equals(code) || Boolean.FALSE.equals(success) || data == null) {
            log.warn("VLStream令牌校验失败，code={}，msg={}", code, bodyJson.getStr("msg"));
            throw new ServiceException("VLStream令牌校验失败");
        }

        JSONObject user = data.getJSONObject("user");
        if (user == null) {
            user = data;
        }

        String userId = firstNotBlank(user.getStr("userId"), user.getStr("id"),
                data.getStr("userId"), data.getStr("account"), user.getStr("userName"));
        String userName = firstNotBlank(data.getStr("userName"), data.getStr("realName"),
                data.getStr("account"), user.getStr("userName"), user.getStr("nickName"),
                user.getStr("loginId"), userId);
        if (StringUtils.isBlank(userId)) {
            throw new ServiceException("VLStream用户信息缺少用户标识");
        }

        PlatformLoginUser platformLoginUser = new PlatformLoginUser();
        platformLoginUser.setUserId(userId);
        platformLoginUser.setUserName(userName);
        platformLoginUser.setTenantId(firstNotBlank(data.getStr("tenantId"), user.getStr("tenantId")));
        platformLoginUser.setAccessToken(accessToken);
        return platformLoginUser;
    }

    private void forwardHeader(HttpRequest target, HttpServletRequest source, String headerName)
    {
        String value = source.getHeader(headerName);
        if (StringUtils.isNotBlank(value)) {
            target.header(headerName, value.trim());
        }
    }

    private boolean isVlstreamRequest(HttpServletRequest request) {
        return AUTH_SOURCE_VLSTREAM.equalsIgnoreCase(request.getHeader(HEADER_AUTH_SOURCE));
    }

    private String normalizeFederatedToken(String accessToken) {
        String token = accessToken == null ? "" : accessToken.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return token;
    }

    private String normalizeLocalToken(String token)
    {
        String normalized = token == null ? "" : token.trim();
        if (normalized.regionMatches(true, 0, Constants.TOKEN_PREFIX, 0, Constants.TOKEN_PREFIX.length())) {
            normalized = normalized.substring(Constants.TOKEN_PREFIX.length()).trim();
        }
        return normalized;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Long toLong(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 设置用户身份信息
     */
    public void setLoginUser(LoginUser loginUser)
    {
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNotEmpty(loginUser.getToken()))
        {
            refreshToken(loginUser);
        }
    }

    /**
     * 删除用户身份信息
     */
    public void delLoginUser(String token)
    {
        if (StringUtils.isNotEmpty(token))
        {
            String userKey = getTokenKey(token);
            redisCache.deleteObject(userKey);
        }
    }

    /**
     * 创建令牌
     *
     * @param loginUser 用户信息
     * @return 令牌
     */
    public String createToken(LoginUser loginUser)
    {
        String token = IdUtils.fastUUID();
        loginUser.setToken(token);
        setUserAgent(loginUser);
        refreshToken(loginUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, token);
        return createToken(claims);
    }

    /**
     * 验证令牌有效期，相差不足20分钟，自动刷新缓存
     *
     * @param loginUser
     * @return 令牌
     */
    public void verifyToken(LoginUser loginUser)
    {
        if (isNeverExpire())
        {
            return;
        }
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        if (expireTime - currentTime <= MILLIS_MINUTE_TEN)
        {
            refreshToken(loginUser);
        }
    }

    /**
     * 刷新令牌有效期
     *
     * @param loginUser 登录信息
     */
    public void refreshToken(LoginUser loginUser)
    {
        String userKey = getTokenKey(loginUser.getToken());
        cacheLoginUser(userKey, loginUser);
    }

    private void cacheLoginUser(String userKey, LoginUser loginUser)
    {
        long loginTime = System.currentTimeMillis();
        loginUser.setLoginTime(loginTime);
        if (isNeverExpire())
        {
            loginUser.setExpireTime(Long.MAX_VALUE);
            redisCache.setCacheObject(userKey, loginUser);
            return;
        }
        loginUser.setExpireTime(loginTime + expireTime * MILLIS_MINUTE);
        redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
    }

    private boolean isNeverExpire()
    {
        return expireTime < 0;
    }

    /**
     * 设置用户代理信息
     *
     * @param loginUser 登录信息
     */
    public void setUserAgent(LoginUser loginUser)
    {
        UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"));
        String ip = IpUtils.getIpAddr();
        loginUser.setIpaddr(ip);
        loginUser.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        loginUser.setBrowser(userAgent.getBrowser().getName());
        loginUser.setOs(userAgent.getOperatingSystem().getName());
    }

    /**
     * 从数据声明生成令牌
     *
     * @param claims 数据声明
     * @return 令牌
     */
    private String createToken(Map<String, Object> claims)
    {
        String token = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, secret).compact();
        return token;
    }

    /**
     * 从令牌中获取数据声明
     *
     * @param token 令牌
     * @return 数据声明
     */
    private Claims parseToken(String token)
    {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从令牌中获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token)
    {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 获取请求token
     *
     * @param request
     * @return token
     */
    private String getToken(HttpServletRequest request)
    {
        return request.getHeader(header);
    }

    /**
     * VLStream 联邦请求使用独立的本地会话令牌，避免与平台网关 AccessToken 混用。
     */
    String resolveAccessToken(HttpServletRequest request)
    {
        if (isVlstreamRequest(request)) {
            String federatedToken = normalizeFederatedToken(request.getHeader(HEADER_VLSTREAM_TOKEN));
            if (StringUtils.isNotBlank(federatedToken)) {
                return federatedToken;
            }
        }
        return getToken(request);
    }

    private String getTokenKey(String uuid)
    {
        return CacheConstants.LOGIN_TOKEN_KEY + uuid;
    }
}
