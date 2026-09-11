package com.materialslab.api.identity.service;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.domain.ManagedRoleOption;
import com.materialslab.api.identity.domain.ManagedUser;
import com.materialslab.api.identity.domain.RegistrationInvitation;
import com.materialslab.api.identity.domain.RegistrationInvitationIssue;
import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.mapper.IdentityMapper;
import com.materialslab.api.identity.security.DatabaseUserDetailsService;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.LoginAttemptGuard;
import com.materialslab.api.identity.security.PasswordPolicy;
import com.materialslab.api.identity.security.UserPrincipal;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.AuthenticationException;
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
    private static final SecureRandom INVITATION_RANDOM = new SecureRandom();
    private static final int DEFAULT_INVITATION_VALID_HOURS = 168;
    private static final int MAX_INVITATION_VALID_HOURS = 720;
    private final DatabaseUserDetailsService userDetailsService;
    private final IdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;
    private final LoginAttemptGuard loginAttemptGuard;

    public IdentityService(DatabaseUserDetailsService userDetailsService, IdentityMapper identityMapper, PasswordEncoder passwordEncoder,
                           AccessControlService accessControlService, LoginAttemptGuard loginAttemptGuard) {
        this.userDetailsService = userDetailsService;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
        this.accessControlService = accessControlService;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    /** 按用户名和密码建立认证主体。 */
    public Authentication authenticate(String username, String password) {
        loginAttemptGuard.requireAllowed(username);
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        try {
            Authentication authentication = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            loginAttemptGuard.recordSuccess(username);
            return authentication;
        } catch (AuthenticationException error) {
            loginAttemptGuard.recordFailure(username);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "用户名或密码错误");
        }
    }

    /** 序列化前端所需的当前会话。 */
    public Map<String, Object> session(UserPrincipal principal) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", principal.userId()); user.put("username", principal.getUsername());
        user.put("display_name", principal.account().displayName()); user.put("organization_id", principal.organizationId());
        user.put("is_super_admin", principal.isSuperAdmin());
        user.put("is_platform_admin", principal.isPlatformAdmin());
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
        List<String> roleCodes = requiredAssignableRoleCodes(payload);
        String password = required(payload, "password");
        String username = required(payload, "username");
        PasswordPolicy.validateManagedPassword(password, username);
        UUID userId = UUID.randomUUID();
        IdentityMapper.UserWriteCommand command = command(userId, principal.organizationId(), passwordEncoder.encode(password), payload);
        requireAvailableUsername(command.username(), userId);
        try {
            identityMapper.insertManagedUser(command);
        } catch (DuplicateKeyException error) {
            throw usernameConflict();
        }
        replaceRoles(principal, userId, roleCodes);
    }

    /** 更新组织用户并重置角色关联。 */
    @Transactional
    public void updateManagedUser(UserPrincipal principal, UUID userId, JsonNode payload) {
        requireSuperAdmin(principal);
        UserAccount existing = identityMapper.findById(userId);
        if (existing == null || !principal.organizationId().equals(existing.organizationId())) throw new BusinessException(HttpStatus.NOT_FOUND, "user_not_found", "用户不存在或不属于当前组织");
        if (existing.superAdmin()) throw new BusinessException(HttpStatus.FORBIDDEN, "super_admin_protected", "超级管理员账号受保护");
        List<String> roleCodes = requiredAssignableRoleCodes(payload);
        IdentityMapper.UserWriteCommand command = command(userId, principal.organizationId(), existing.password(), payload);
        requireAvailableUsername(command.username(), userId);
        try {
            if (identityMapper.updateManagedUser(command) == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "user_not_found", "用户不存在或不属于当前组织");
        } catch (DuplicateKeyException error) {
            throw usernameConflict();
        }
        replaceRoles(principal, userId, roleCodes);
    }

    /** 重置普通用户密码。 */
    @Transactional
    public void resetManagedUserPassword(UserPrincipal principal, UUID userId, String password) {
        requireSuperAdmin(principal);
        UserAccount existing = identityMapper.findById(userId);
        if (existing == null || !principal.organizationId().equals(existing.organizationId())) throw new BusinessException(HttpStatus.NOT_FOUND, "user_not_found", "用户不存在或不属于当前组织");
        if (existing.superAdmin()) throw new BusinessException(HttpStatus.FORBIDDEN, "super_admin_protected", "超级管理员账号受保护");
        PasswordPolicy.validateManagedPassword(password, existing.username());
        identityMapper.resetPassword(principal.organizationId(), userId, passwordEncoder.encode(password));
    }

    /** 签发一次性邀请码；只有哈希写入数据库，明文仅随本次响应返回。 */
    @Transactional
    public RegistrationInvitationIssue createRegistrationInvitation(UserPrincipal principal, String roleCode, Integer validForHours) {
        requireSuperAdmin(principal);
        if ("super_admin".equals(roleCode)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "邀请码不能分配超级管理员角色");
        }
        UUID roleId = identityMapper.findRoleId(principal.organizationId(), roleCode);
        if (roleId == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "角色不存在或不可用");
        int hours = validForHours == null ? DEFAULT_INVITATION_VALID_HOURS : validForHours;
        if (hours < 1 || hours > MAX_INVITATION_VALID_HOURS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "邀请码有效期须为 1 至 720 小时");
        }
        String invitationCode = generateInvitationCode();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(hours);
        identityMapper.insertRegistrationInvitation(new IdentityMapper.RegistrationInvitationCommand(
                UUID.randomUUID(), principal.organizationId(), roleId, principal.userId(), hashInvitationCode(invitationCode), expiresAt));
        return new RegistrationInvitationIssue(invitationCode, roleCode, expiresAt);
    }

    /** 查询当前组织邀请码元数据，邀请码明文不可再次读取。 */
    public List<RegistrationInvitation> listRegistrationInvitations(UserPrincipal principal) {
        requireSuperAdmin(principal);
        return identityMapper.listRegistrationInvitations(principal.organizationId());
    }

    /** 撤销尚未使用的邀请码；已使用或不存在的邀请不暴露额外状态。 */
    @Transactional
    public void revokeRegistrationInvitation(UserPrincipal principal, UUID invitationId) {
        requireSuperAdmin(principal);
        if (identityMapper.revokeRegistrationInvitation(principal.organizationId(), invitationId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "invitation_not_found", "邀请码不存在、已使用或已撤销");
        }
    }

    /** 使用邀请码注册普通用户；邀请码在同一事务内原子消费，不能注册超级管理员。 */
    @Transactional
    public void registerByInvitation(JsonNode payload) {
        String invitationCode = required(payload, "invitation_code").trim();
        if (invitationCode.length() > 128) throw invalidInvitation();
        IdentityMapper.ActiveRegistrationInvitation invitation = identityMapper.findActiveRegistrationInvitation(hashInvitationCode(invitationCode));
        if (invitation == null) throw invalidInvitation();
        String password = required(payload, "password");
        String username = required(payload, "username");
        String displayName = required(payload, "display_name");
        String email = payload.path("email").asText("");
        validateRegistrationProfile(username, displayName, email);
        PasswordPolicy.validateManagedPassword(password, username);
        UUID userId = UUID.randomUUID();
        IdentityMapper.UserWriteCommand command = new IdentityMapper.UserWriteCommand(userId, invitation.organizationId(),
                passwordEncoder.encode(password), username, displayName, email, "active", true);
        requireAvailableUsername(command.username(), userId);
        try {
            identityMapper.insertManagedUser(command);
        } catch (DuplicateKeyException error) {
            throw usernameConflict();
        }
        if (identityMapper.consumeRegistrationInvitation(invitation.id(), userId) == 0) throw invalidInvitation();
        identityMapper.insertUserRole(UUID.randomUUID(), invitation.organizationId(), userId, invitation.roleId(), invitation.createdById());
    }

    /** 校验当前密码后更新当前用户；控制器负责使该会话失效并要求重新登录。 */
    @Transactional
    public void changeOwnPassword(UserPrincipal principal, String currentPassword, String newPassword) {
        UserAccount current = identityMapper.findById(principal.userId());
        if (current == null || !principal.organizationId().equals(current.organizationId()) || !"active".equals(current.status())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "authentication_required", "登录状态已失效");
        }
        if (!passwordEncoder.matches(currentPassword, current.password())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_current_password", "当前密码不正确");
        }
        PasswordPolicy.validateManagedPassword(newPassword, current.username());
        if (passwordEncoder.matches(newPassword, current.password())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "新密码不能与当前密码相同");
        }
        identityMapper.resetPassword(current.organizationId(), current.id(), passwordEncoder.encode(newPassword));
    }

    /** 取得经过类型校验的当前主体。 */
    public static UserPrincipal currentPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) return userPrincipal;
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "authentication_required", "登录状态已失效");
    }

    private void requireSuperAdmin(UserPrincipal principal) {
        if (!principal.isSuperAdmin() || principal.isPlatformAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "仅当前组织的超级管理员可管理系统用户");
        }
    }

    private void requireAvailableUsername(String username, UUID excludedUserId) {
        if (identityMapper.existsUsernameExcluding(username, excludedUserId)) {
            throw usernameConflict();
        }
    }

    private BusinessException usernameConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "username_conflict", "用户名已存在");
    }

    private BusinessException invalidInvitation() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_invitation", "邀请码无效、已过期、已使用或已撤销");
    }

    private String generateInvitationCode() {
        byte[] bytes = new byte[24];
        INVITATION_RANDOM.nextBytes(bytes);
        return "MLI-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateRegistrationProfile(String username, String displayName, String email) {
        if (!username.matches("[A-Za-z0-9._-]{3,64}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "用户名须为 3 至 64 位字母、数字、点、下划线或短横线");
        }
        if (displayName.length() > 100 || email.length() > 254) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "显示名称或邮箱长度超出限制");
        }
    }

    private String hashInvitationCode(String invitationCode) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(invitationCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("运行环境不支持 SHA-256", error);
        }
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

    private List<String> requiredAssignableRoleCodes(JsonNode payload) {
        JsonNode roleCodes = payload.path("role_codes");
        if (!roleCodes.isArray() || roleCodes.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "至少选择一个普通系统角色");
        }
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        for (JsonNode roleCode : roleCodes) {
            String normalizedCode = roleCode.asText("").trim();
            if (normalizedCode.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "角色编码不能为空");
            }
            if ("super_admin".equals(normalizedCode)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "普通用户不可分配超级管理员角色");
            }
            if (!normalizedCodes.add(normalizedCode)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "角色不可重复分配");
            }
        }
        return List.copyOf(normalizedCodes);
    }

    private void replaceRoles(UserPrincipal principal, UUID userId, List<String> roleCodes) {
        identityMapper.deleteUserRoles(principal.organizationId(), userId);
        for (String roleCode : roleCodes) {
            UUID roleId = identityMapper.findRoleId(principal.organizationId(), roleCode);
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
