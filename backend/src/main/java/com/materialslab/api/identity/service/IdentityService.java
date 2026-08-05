package com.materialslab.api.identity.service;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.domain.ManagedRoleOption;
import com.materialslab.api.identity.domain.ManagedUser;
import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.mapper.IdentityMapper;
import com.materialslab.api.identity.security.DatabaseUserDetailsService;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 处理登录、会话及账户选项的应用服务。 */
@Service
public class IdentityService {
    private final DatabaseUserDetailsService userDetailsService;
    private final IdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;

    public IdentityService(DatabaseUserDetailsService userDetailsService, IdentityMapper identityMapper, PasswordEncoder passwordEncoder,
                           AccessControlService accessControlService) {
        this.userDetailsService = userDetailsService;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
        this.accessControlService = accessControlService;
    }

    /** 按用户名和密码建立认证主体。 */
    public Authentication authenticate(String username, String password) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        try { return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password)); }
        catch (Exception error) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "用户名或密码错误"); }
    }

    /** 序列化前端所需的当前会话。 */
    public Map<String, Object> session(UserPrincipal principal) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", principal.userId()); user.put("username", principal.getUsername());
        user.put("display_name", principal.account().displayName()); user.put("organization_id", principal.organizationId());
        user.put("is_super_admin", principal.isSuperAdmin());
        user.put("permissions", principal.isSuperAdmin() ? List.of("*") : identityMapper.listPermissionCodes(principal.userId()));
        user.put("role_codes", identityMapper.listRoleCodes(principal.userId()));
        user.put("role_names", identityMapper.listRoleNames(principal.userId()));
        return user;
    }

    /** 返回当前登录用户可选择的负责人和成员。 */
    public List<Map<String, Object>> visibleUserOptions(UserPrincipal principal) {
        accessControlService.requirePermission(principal, "project.create");
        return identityMapper.listVisibleActiveUsers(principal.organizationId()).stream().map(account -> Map.<String, Object>of(
                "id", account.id(), "username", account.username(), "display_name", account.displayName())).toList();
    }

    /** 分页查询当前组织用户，仅允许超级管理员调用。 */
    public List<ManagedUser> listManagedUsers(UserPrincipal principal, String status, String search, String roleCode, int page, int pageSize) {
        requireSuperAdmin(principal);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = Math.max(page - 1, 0) * normalizedPageSize;
        return identityMapper.listManagedUsers(principal.organizationId(), status, search, roleCode, normalizedPageSize, offset).stream()
                .map(row -> new ManagedUser(row.id(), row.username(), row.displayName(), row.email(), row.status(), row.active(),
                        row.superAdmin(), split(row.roleCodes()), split(row.roleNames()), row.lastLogin(), row.createdAt(), row.updatedAt()))
                .toList();
    }

    /** 统计当前组织用户，仅允许超级管理员调用。 */
    public long countManagedUsers(UserPrincipal principal, String status, String search, String roleCode) {
        requireSuperAdmin(principal);
        return identityMapper.countManagedUsers(principal.organizationId(), status, search, roleCode);
    }

    /** 查询当前组织可分配角色，仅允许超级管理员调用。 */
    public List<ManagedRoleOption> managedRoleOptions(UserPrincipal principal) {
        requireSuperAdmin(principal);
        return identityMapper.listManagedRoleOptions(principal.organizationId());
    }

    /** 创建组织用户并绑定角色。 */
    @Transactional
    public void createManagedUser(UserPrincipal principal, JsonNode payload) {
        requireSuperAdmin(principal);
        String password = required(payload, "password");
        if (password.length() < 8) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "初始密码至少 8 位");
        UUID userId = UUID.randomUUID();
        identityMapper.insertManagedUser(command(userId, principal.organizationId(), passwordEncoder.encode(password), payload));
        replaceRoles(principal, userId, payload);
    }

    /** 更新组织用户并重置角色关联。 */
    @Transactional
    public void updateManagedUser(UserPrincipal principal, UUID userId, JsonNode payload) {
        requireSuperAdmin(principal);
        UserAccount existing = identityMapper.findById(userId);
        if (existing == null || !principal.organizationId().equals(existing.organizationId())) throw new BusinessException(HttpStatus.NOT_FOUND, "user_not_found", "用户不存在或不属于当前组织");
        if (existing.superAdmin()) throw new BusinessException(HttpStatus.FORBIDDEN, "super_admin_protected", "超级管理员账号受保护");
        if (identityMapper.updateManagedUser(command(userId, principal.organizationId(), existing.password(), payload)) == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "user_not_found", "用户不存在或不属于当前组织");
        replaceRoles(principal, userId, payload);
    }

    /** 重置普通用户密码。 */
    @Transactional
    public void resetManagedUserPassword(UserPrincipal principal, UUID userId, String password) {
        requireSuperAdmin(principal);
        if (password == null || password.length() < 8) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "新密码至少 8 位");
        UserAccount existing = identityMapper.findById(userId);
        if (existing == null || !principal.organizationId().equals(existing.organizationId())) throw new BusinessException(HttpStatus.NOT_FOUND, "user_not_found", "用户不存在或不属于当前组织");
        if (existing.superAdmin()) throw new BusinessException(HttpStatus.FORBIDDEN, "super_admin_protected", "超级管理员账号受保护");
        identityMapper.resetPassword(principal.organizationId(), userId, passwordEncoder.encode(password));
    }

    /** 取得经过类型校验的当前主体。 */
    public static UserPrincipal currentPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) return userPrincipal;
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "authentication_required", "登录状态已失效");
    }

    private void requireSuperAdmin(UserPrincipal principal) {
        if (!principal.isSuperAdmin()) throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "仅超级管理员可管理系统用户");
    }

    private List<String> split(String values) {
        return values == null || values.isBlank() ? List.of() : Arrays.stream(values.split(",")).filter(value -> !value.isBlank()).toList();
    }

    private IdentityMapper.UserWriteCommand command(UUID userId, UUID organizationId, String password, JsonNode payload) {
        String status = required(payload, "status");
        if (!List.of("active", "locked", "disabled").contains(status)) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "用户状态不合法");
        return new IdentityMapper.UserWriteCommand(userId, organizationId, password, required(payload, "username"), required(payload, "display_name"),
                payload.path("email").asText(""), status, "active".equals(status));
    }

    private void replaceRoles(UserPrincipal principal, UUID userId, JsonNode payload) {
        JsonNode roleCodes = payload.path("role_codes");
        if (!roleCodes.isArray() || roleCodes.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "至少选择一个系统角色");
        identityMapper.deleteUserRoles(principal.organizationId(), userId);
        for (JsonNode roleCode : roleCodes) {
            UUID roleId = identityMapper.findRoleId(principal.organizationId(), roleCode.asText());
            if (roleId == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "角色不存在或不可用");
            identityMapper.insertUserRole(UUID.randomUUID(), principal.organizationId(), userId, roleId, principal.userId());
        }
    }

    private String required(JsonNode payload, String key) {
        String value = payload.path(key).asText();
        if (value.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key + " 不能为空");
        return value;
    }
}
