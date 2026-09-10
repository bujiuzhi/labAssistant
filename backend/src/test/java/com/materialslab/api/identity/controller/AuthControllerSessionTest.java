package com.materialslab.api.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.identity.service.IdentityService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 验证登录会话固定攻击防护及无会话注销。 */
class AuthControllerSessionTest {
    @AfterEach
    void 清理安全上下文() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 登录应轮换已有会话编号() {
        IdentityService identityService = mock(IdentityService.class);
        UserPrincipal principal = principal();
        when(identityService.authenticate("tester", "password123")).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of()));
        when(identityService.session(principal)).thenReturn(Map.of("id", principal.userId()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        String originalSessionId = request.getSession(true).getId();

        new AuthController(identityService).login(new AuthController.LoginRequest("tester", "password123"), request);

        assertThat(request.getSession(false).getId()).isNotEqualTo(originalSessionId);
    }

    @Test
    void 无会话注销也应成功() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(new AuthController(mock(IdentityService.class))
                .logout(request, new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void 改密成功后应使当前会话失效() {
        IdentityService identityService = mock(IdentityService.class);
        UserPrincipal principal = principal();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        var response = new AuthController(identityService).changePassword(
                new AuthController.ChangePasswordRequest("Current!Password123", "New!Password123456"), request);

        verify(identityService).changeOwnPassword(principal, "Current!Password123", "New!Password123456");
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private UserPrincipal principal() {
        UUID userId = UUID.randomUUID();
        return new UserPrincipal(new UserAccount(userId, UUID.randomUUID(), "tester", "", "测试用户", "active", false, false, 0), List.of());
    }
}
