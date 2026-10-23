package com.ruoyi.framework.security.filter;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.TokenService;

/**
 * token过滤器 验证token有效性
 * 
 * @author ruoyi
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter
{
    private static final String ZLM_HOOK_PATH_PREFIX = "/index/hook/";

    @Autowired
    private TokenService tokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        return request.getServletPath().startsWith(ZLM_HOOK_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        boolean sharedHandshake = SharedCameraRtcAuthenticationRequest.isSharedHandshake(request);
        if (sharedHandshake)
        {
            // A shared RTC ticket must bind to credentials verified for this handshake, never
            // an authentication left in the context by another filter or an earlier request.
            SecurityContextHolder.clearContext();
        }
        SharedCameraRtcAuthenticationRequest.Views views;
        try
        {
            views = SharedCameraRtcAuthenticationRequest.prepare(request);
            LoginUser loginUser = tokenService.getLoginUser(views.getAuthenticationRequest());
            if (sharedHandshake && loginUser == null)
            {
                rejectSharedHandshake(response);
                return;
            }
            if (StringUtils.isNotNull(loginUser)
                    && (sharedHandshake || StringUtils.isNull(SecurityUtils.getAuthentication())))
            {
                tokenService.verifyToken(loginUser);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(views.getChainRequest()));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }
        catch (ServiceException | IllegalArgumentException e)
        {
            if (!sharedHandshake) throw e;
            rejectSharedHandshake(response);
            return;
        }
        chain.doFilter(views.getChainRequest(), response);
    }

    private void rejectSharedHandshake(HttpServletResponse response) throws IOException
    {
        SecurityContextHolder.clearContext();
        // Never include protocol contents, credential values or verifier details in the response.
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Shared RTC authentication failed");
    }
}
