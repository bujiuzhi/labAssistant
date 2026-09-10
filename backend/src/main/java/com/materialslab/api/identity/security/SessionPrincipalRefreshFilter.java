package com.materialslab.api.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 每次已认证请求均重新读取账户与权限；状态失效或会话版本变化时立即终止会话。
 * 这样管理员禁用用户、调整角色或重置密码后，旧 JSESSIONID 不能继续沿用旧授权。
 */
public class SessionPrincipalRefreshFilter extends OncePerRequestFilter {
    private final DatabaseUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public SessionPrincipalRefreshFilter(DatabaseUserDetailsService userDetailsService, ObjectMapper objectMapper) {
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserPrincipal cached)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            UserPrincipal current = userDetailsService.loadActiveById(cached.userId());
            if (current.sessionVersion() != cached.sessionVersion()) {
                rejectExpiredSession(request, response);
                return;
            }
            UsernamePasswordAuthenticationToken refreshed = UsernamePasswordAuthenticationToken.authenticated(
                    current, authentication.getCredentials(), current.getAuthorities());
            refreshed.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(refreshed);
            filterChain.doFilter(request, response);
        } catch (UsernameNotFoundException error) {
            rejectExpiredSession(request, response);
        }
    }

    private void rejectExpiredSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        SecurityConfig.writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "authentication_required");
    }
}
