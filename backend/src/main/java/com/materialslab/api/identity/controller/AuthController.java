package com.materialslab.api.identity.controller;

import com.materialslab.api.common.model.ApiResponse;
import com.materialslab.api.common.model.PageResponse;
import com.materialslab.api.identity.domain.ManagedRoleOption;
import com.materialslab.api.identity.domain.ManagedUser;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.identity.service.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 会话接口，URL 为 /api/v1/auth。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identityService;
    public AuthController(IdentityService identityService) { this.identityService = identityService; }

    /** 获取 CSRF 令牌并写入 Cookie。 */
    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken csrfToken) { return ApiResponse.of(Map.of("csrf_token", csrfToken.getToken())); }

    /** 登录并持久化同源 Session。 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = identityService.authenticate(request.username(), request.password());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return ResponseEntity.ok(ApiResponse.of(identityService.session((UserPrincipal) authentication.getPrincipal())));
    }

    /** 返回当前会话用户。 */
    @GetMapping("/session")
    public ApiResponse<Map<String, Object>> session() { return ApiResponse.of(identityService.session(IdentityService.currentPrincipal())); }

    /** 注销并失效服务器会话。 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** 查询当前组织的有效用户选项。 */
    @GetMapping("/users/options")
    public ApiResponse<?> userOptions() { return ApiResponse.of(identityService.visibleUserOptions(IdentityService.currentPrincipal())); }

    /** 分页查询当前组织用户，仅超级管理员可访问。 */
    @GetMapping("/users")
    public PageResponse<ManagedUser> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(name = "role_code", required = false) String roleCode) {
        UserPrincipal principal = IdentityService.currentPrincipal();
        List<ManagedUser> users = identityService.listManagedUsers(principal, status, search, roleCode, page, pageSize);
        return PageResponse.of(users, page, pageSize, identityService.countManagedUsers(principal, status, search, roleCode));
    }

    /** 查询当前组织可分配角色，仅超级管理员可访问。 */
    @GetMapping("/roles/options")
    public ApiResponse<List<ManagedRoleOption>> roleOptions() {
        return ApiResponse.of(identityService.managedRoleOptions(IdentityService.currentPrincipal()));
    }

    /** 创建组织用户，仅超级管理员可访问。 */
    @PostMapping("/users")
    public ApiResponse<Void> createUser(@RequestBody tools.jackson.databind.JsonNode payload) {
        identityService.createManagedUser(IdentityService.currentPrincipal(), payload);
        return ApiResponse.of(null);
    }

    /** 更新组织用户，仅超级管理员可访问。 */
    @PatchMapping("/users/{userId}")
    public ApiResponse<Void> updateUser(@PathVariable java.util.UUID userId, @RequestBody tools.jackson.databind.JsonNode payload) {
        identityService.updateManagedUser(IdentityService.currentPrincipal(), userId, payload);
        return ApiResponse.of(null);
    }

    /** 重置普通用户密码，仅超级管理员可访问。 */
    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable java.util.UUID userId, @RequestBody ResetPasswordRequest request) {
        identityService.resetManagedUserPassword(IdentityService.currentPrincipal(), userId, request.password());
        return ResponseEntity.noContent().build();
    }

    /** 登录请求。 */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    /** 密码重置请求。 */
    public record ResetPasswordRequest(@NotBlank String password) { }
}
