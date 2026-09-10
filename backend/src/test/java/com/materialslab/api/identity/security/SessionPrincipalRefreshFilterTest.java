package com.materialslab.api.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.materialslab.api.identity.domain.UserAccount;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

/** 验证管理操作会立即撤销旧会话，而未撤销会话在每次请求使用最新授权。 */
class SessionPrincipalRefreshFilterTest {
    @AfterEach
    void 清理安全上下文() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 会话版本变化应失效会话并拒绝继续访问() throws Exception {
        DatabaseUserDetailsService users = mock(DatabaseUserDetailsService.class);
        UserPrincipal cached = principal(0, "PERM_document.view");
        when(users.loadActiveById(cached.userId())).thenReturn(principal(cached.userId(), cached.organizationId(), 1, "PERM_document.view"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(cached, "", cached.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new SessionPrincipalRefreshFilter(users, new ObjectMapper()).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void 未撤销会话应使用数据库中的最新权限继续请求() throws Exception {
        DatabaseUserDetailsService users = mock(DatabaseUserDetailsService.class);
        UserPrincipal cached = principal(0, "PERM_document.view");
        UserPrincipal current = principal(cached.userId(), cached.organizationId(), 0, "PERM_project.create");
        when(users.loadActiveById(cached.userId())).thenReturn(current);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(cached, "", cached.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> assertThat(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().map(item -> item.getAuthority()).toList()).containsExactly("PERM_project.create");

        new SessionPrincipalRefreshFilter(users, new ObjectMapper()).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private UserPrincipal principal(long sessionVersion, String authority) {
        return principal(UUID.randomUUID(), UUID.randomUUID(), sessionVersion, authority);
    }

    private UserPrincipal principal(UUID userId, UUID organizationId, long sessionVersion, String authority) {
        return new UserPrincipal(new UserAccount(userId, organizationId, "tester", "", "测试用户", "active", false, false, sessionVersion),
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
