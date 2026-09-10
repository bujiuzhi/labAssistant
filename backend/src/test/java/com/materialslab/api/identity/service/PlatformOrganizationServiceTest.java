package com.materialslab.api.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.mapper.IdentityMapper;
import com.materialslab.api.identity.mapper.PlatformOrganizationMapper;
import com.materialslab.api.identity.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 验证平台组织开通边界：仅平台管理员可创建，且新管理员不会继承平台权限。 */
class PlatformOrganizationServiceTest {
    @Test
    void 创建组织应初始化内置角色和首个组织管理员() {
        PlatformOrganizationMapper organizationMapper = mock(PlatformOrganizationMapper.class);
        IdentityMapper identityMapper = mock(IdentityMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("StrongPassword123")).thenReturn("encoded-password");
        when(organizationMapper.listPermissions()).thenReturn(SystemIdentityCatalog.PERMISSIONS.stream()
                .map(permission -> new PlatformOrganizationMapper.PermissionRow(UUID.randomUUID(), permission.code())).toList());
        PlatformOrganizationService service = new PlatformOrganizationService(organizationMapper, identityMapper, encoder);

        var created = service.createOrganization(platformPrincipal(), new PlatformOrganizationService.CreateOrganizationRequest(
                "lab_b", "乙实验室", "lab-b-admin", "乙管理员", "admin@example.invalid", "StrongPassword123"));

        assertThat(created.organizationCode()).isEqualTo("LAB_B");
        assertThat(created.name()).isEqualTo("乙实验室");
        verify(organizationMapper).insertOrganization(any(UUID.class), org.mockito.ArgumentMatchers.eq("LAB_B"), org.mockito.ArgumentMatchers.eq("乙实验室"));
        verify(organizationMapper).insertOrganizationAdmin(any(UUID.class), any(UUID.class), org.mockito.ArgumentMatchers.eq("encoded-password"),
                org.mockito.ArgumentMatchers.eq("lab-b-admin"), org.mockito.ArgumentMatchers.eq("乙管理员"), org.mockito.ArgumentMatchers.eq("admin@example.invalid"));
        verify(organizationMapper, org.mockito.Mockito.times(SystemIdentityCatalog.ROLES.size())).insertSystemRole(
                any(UUID.class), any(UUID.class), anyString(), anyString(), anyString());
        verify(organizationMapper, org.mockito.Mockito.times(1)).insertUserRole(any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class));
    }

    @Test
    void 非平台管理员不得读取或开通组织() {
        PlatformOrganizationMapper organizationMapper = mock(PlatformOrganizationMapper.class);
        PlatformOrganizationService service = new PlatformOrganizationService(organizationMapper, mock(IdentityMapper.class), mock(PasswordEncoder.class));
        UserPrincipal tenantAdmin = new UserPrincipal(new UserAccount(UUID.randomUUID(), UUID.randomUUID(), "tenant-admin", "",
                "组织管理员", "active", true, false, 0), List.of());

        assertThatThrownBy(() -> service.listOrganizations(tenantAdmin)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("permission_denied");
        assertThatThrownBy(() -> service.createOrganization(tenantAdmin, new PlatformOrganizationService.CreateOrganizationRequest(
                "LAB_C", "丙实验室", "lab-c-admin", "丙管理员", "", "StrongPassword123")))
                .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).code()).isEqualTo("permission_denied");
        verify(organizationMapper, never()).insertOrganization(any(UUID.class), anyString(), anyString());
    }

    private UserPrincipal platformPrincipal() {
        return new UserPrincipal(new UserAccount(UUID.randomUUID(), UUID.randomUUID(), "platform-admin", "",
                "平台管理员", "active", true, true, 0), List.of());
    }
}
