package com.materialslab.api.identity.service;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.domain.PlatformOrganization;
import com.materialslab.api.identity.mapper.IdentityMapper;
import com.materialslab.api.identity.mapper.PlatformOrganizationMapper;
import com.materialslab.api.identity.security.PasswordPolicy;
import com.materialslab.api.identity.security.UserPrincipal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台控制面服务：仅负责开通租户，不读取或代理其他组织的业务数据。 */
@Service
public class PlatformOrganizationService {
    private final PlatformOrganizationMapper organizationMapper;
    private final IdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;

    public PlatformOrganizationService(PlatformOrganizationMapper organizationMapper, IdentityMapper identityMapper,
                                       PasswordEncoder passwordEncoder) {
        this.organizationMapper = organizationMapper;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 返回平台组织目录。 */
    public List<PlatformOrganization> listOrganizations(UserPrincipal principal) {
        requirePlatformAdmin(principal);
        return organizationMapper.listOrganizations();
    }

    /**
     * 原子开通组织、内置角色和首个组织管理员。
     *
     * @param principal 已认证的平台管理员
     * @param request 组织及首个管理员信息
     * @return 新组织的基础信息，不返回管理员密码
     */
    @Transactional
    public PlatformOrganization createOrganization(UserPrincipal principal, CreateOrganizationRequest request) {
        requirePlatformAdmin(principal);
        if (request == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "组织开通参数不能为空");
        String organizationCode = text(request.organizationCode()).toUpperCase(java.util.Locale.ROOT);
        String organizationName = text(request.organizationName());
        String adminUsername = text(request.adminUsername());
        String adminDisplayName = text(request.adminDisplayName());
        String adminEmail = request.adminEmail() == null ? "" : request.adminEmail().trim();
        validate(organizationCode, organizationName, adminUsername, adminDisplayName, adminEmail, request.adminPassword());
        if (identityMapper.existsUsernameExcluding(adminUsername, UUID.randomUUID())) {
            throw new BusinessException(HttpStatus.CONFLICT, "username_conflict", "管理员用户名已存在");
        }

        UUID organizationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        try {
            organizationMapper.insertOrganization(organizationId, organizationCode, organizationName);
            organizationMapper.insertOrganizationAdmin(adminId, organizationId, passwordEncoder.encode(request.adminPassword()),
                    adminUsername, adminDisplayName, adminEmail);
            Map<String, UUID> permissionIds = permissionIds();
            UUID superAdminRoleId = createSystemRoles(organizationId, permissionIds);
            organizationMapper.insertUserRole(UUID.randomUUID(), organizationId, adminId, superAdminRoleId, adminId);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(HttpStatus.CONFLICT, "organization_conflict", "组织编码或管理员用户名已存在");
        }
        return new PlatformOrganization(organizationId, organizationCode, organizationName, "active", null);
    }

    private UUID createSystemRoles(UUID organizationId, Map<String, UUID> permissionIds) {
        UUID superAdminRoleId = null;
        for (SystemIdentityCatalog.RoleDefinition role : SystemIdentityCatalog.ROLES) {
            UUID roleId = UUID.randomUUID();
            organizationMapper.insertSystemRole(roleId, organizationId, role.code(), role.name(), "系统角色");
            for (String permissionCode : role.permissions()) {
                UUID permissionId = permissionIds.get(permissionCode);
                if (permissionId == null) {
                    throw new IllegalStateException("系统权限字典不完整，无法开通组织：" + permissionCode);
                }
                organizationMapper.insertRolePermission(UUID.randomUUID(), roleId, permissionId);
            }
            if ("super_admin".equals(role.code())) superAdminRoleId = roleId;
        }
        if (superAdminRoleId == null) throw new IllegalStateException("系统角色定义缺少超级管理员");
        return superAdminRoleId;
    }

    private Map<String, UUID> permissionIds() {
        Map<String, UUID> result = new HashMap<>();
        organizationMapper.listPermissions().forEach(permission -> result.put(permission.permissionCode(), permission.id()));
        for (SystemIdentityCatalog.PermissionDefinition permission : SystemIdentityCatalog.PERMISSIONS) {
            if (!result.containsKey(permission.code())) {
                throw new IllegalStateException("系统权限字典不完整，无法开通组织：" + permission.code());
            }
        }
        return result;
    }

    private void requirePlatformAdmin(UserPrincipal principal) {
        if (!principal.isPlatformAdmin() || principal.isSuperAdmin() || !organizationMapper.isPlatformOrganization(principal.organizationId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "仅平台管理员可管理组织");
        }
    }

    private void validate(String organizationCode, String organizationName, String adminUsername, String adminDisplayName,
                          String adminEmail, String adminPassword) {
        if (!organizationCode.matches("[A-Z0-9][A-Z0-9_-]{1,31}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "组织编码须为 2 至 32 位大写字母、数字、下划线或短横线");
        }
        if (organizationName.isBlank() || organizationName.length() > 200 || adminDisplayName.isBlank() || adminDisplayName.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "组织名称或管理员显示名称不合法");
        }
        if (!adminUsername.matches("[A-Za-z0-9._-]{3,64}") || adminEmail.length() > 254) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "管理员用户名或邮箱不合法");
        }
        PasswordPolicy.validateManagedPassword(adminPassword, adminUsername);
    }

    private String text(String value) { return value == null ? "" : value.trim(); }

    /** 创建组织的受控入参；新管理员默认仅拥有该组织的超级管理员权限。 */
    public record CreateOrganizationRequest(String organizationCode, String organizationName, String adminUsername,
                                            String adminDisplayName, String adminEmail, String adminPassword) { }
}
