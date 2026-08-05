package com.materialslab.api.identity.security;

import com.materialslab.api.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 统一执行组织内 RBAC 权限校验，避免控制器遗漏业务授权。 */
@Service
public class AccessControlService {
    /** 判断用户是否具备指定业务权限，超级管理员拥有组织内全部权限。 */
    public boolean hasPermission(UserPrincipal principal, String permissionCode) {
        return principal.isSuperAdmin() || principal.getAuthorities().stream()
                .anyMatch(authority -> ("PERM_" + permissionCode).equals(authority.getAuthority()));
    }

    /** 要求当前用户具备指定业务权限。 */
    public void requirePermission(UserPrincipal principal, String permissionCode) {
        if (!hasPermission(principal, permissionCode)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "当前用户无权执行该操作");
        }
    }

    /** 超级管理员可读取组织全部数据，其他用户必须经过项目成员范围过滤。 */
    public boolean canReadAllOrganizationData(UserPrincipal principal) {
        return principal.isSuperAdmin();
    }
}
