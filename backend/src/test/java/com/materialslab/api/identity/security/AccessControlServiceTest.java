package com.materialslab.api.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.domain.UserAccount;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** 验证角色权限码和超级管理员的统一授权规则。 */
class AccessControlServiceTest {
    private final AccessControlService accessControlService = new AccessControlService();

    /** 普通角色必须拥有精确的权限码。 */
    @Test
    void 应仅允许拥有对应权限码的普通用户执行操作() {
        UserPrincipal principal = principal(false, List.of(new SimpleGrantedAuthority("PERM_project.read")));

        assertThat(accessControlService.hasPermission(principal, "project.read")).isTrue();
        assertThat(accessControlService.hasPermission(principal, "project.archive")).isFalse();
        assertThatThrownBy(() -> accessControlService.requirePermission(principal, "project.archive"))
                .isInstanceOf(BusinessException.class);
    }

    /** 超级管理员拥有完整组织数据读取范围。 */
    @Test
    void 超级管理员应拥有所有权限和组织全量读取范围() {
        UserPrincipal principal = principal(true, List.of());

        assertThat(accessControlService.hasPermission(principal, "project.archive")).isTrue();
        assertThat(accessControlService.canReadAllOrganizationData(principal)).isTrue();
    }

    private UserPrincipal principal(boolean superAdmin, List<SimpleGrantedAuthority> authorities) {
        return new UserPrincipal(new UserAccount(UUID.randomUUID(), UUID.randomUUID(), "tester", "", "测试用户", "active", superAdmin), authorities);
    }
}
