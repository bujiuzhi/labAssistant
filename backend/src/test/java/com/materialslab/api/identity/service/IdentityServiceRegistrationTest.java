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
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.DatabaseUserDetailsService;
import com.materialslab.api.identity.security.LoginAttemptGuard;
import com.materialslab.api.identity.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/** 验证邀请码注册与当前用户改密的安全边界。 */
class IdentityServiceRegistrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 邀请码明文只能返回给签发者一次，持久化参数仅允许保存 SHA-256 哈希。 */
    @Test
    void 签发邀请码不应持久化明文且拒绝超级管理员角色() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IdentityService service = service(mapper, encoder);
        UserPrincipal admin = principal(true, "admin", "stored");
        UUID roleId = UUID.randomUUID();
        when(mapper.findRoleId(admin.organizationId(), "researcher")).thenReturn(roleId);

        var issue = service.createRegistrationInvitation(admin, "researcher", 24);

        ArgumentCaptor<IdentityMapper.RegistrationInvitationCommand> command = ArgumentCaptor.forClass(IdentityMapper.RegistrationInvitationCommand.class);
        verify(mapper).insertRegistrationInvitation(command.capture());
        assertThat(issue.invitationCode()).startsWith("MLI-");
        assertThat(command.getValue().codeHash()).hasSize(64).isNotEqualTo(issue.invitationCode());
        assertThatThrownBy(() -> service.createRegistrationInvitation(admin, "super_admin", 24))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("validation_error");
    }

    /** 邀请码注册使用预绑定组织和角色，并原子消费邀请码。 */
    @Test
    void 注册应绑定邀请码预设角色且消费邀请码() throws Exception {
        IdentityMapper mapper = mock(IdentityMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IdentityService service = service(mapper, encoder);
        UUID organizationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        when(mapper.findActiveRegistrationInvitation(anyString())).thenReturn(
                new IdentityMapper.ActiveRegistrationInvitation(UUID.randomUUID(), organizationId, roleId, creatorId));
        when(mapper.consumeRegistrationInvitation(any(UUID.class), any(UUID.class))).thenReturn(1);
        when(encoder.encode("Strong!Password123")).thenReturn("encoded-password");

        service.registerByInvitation(objectMapper.readTree("""
                {"invitation_code":"MLI-example","username":"new-user","display_name":"新成员",
                 "email":"member@example.invalid","password":"Strong!Password123"}
                """));

        ArgumentCaptor<IdentityMapper.UserWriteCommand> account = ArgumentCaptor.forClass(IdentityMapper.UserWriteCommand.class);
        verify(mapper).insertManagedUser(account.capture());
        assertThat(account.getValue().organizationId()).isEqualTo(organizationId);
        assertThat(account.getValue().status()).isEqualTo("active");
        verify(mapper).consumeRegistrationInvitation(any(UUID.class), any(UUID.class));
        verify(mapper).insertUserRole(any(UUID.class), org.mockito.ArgumentMatchers.eq(organizationId), any(UUID.class),
                org.mockito.ArgumentMatchers.eq(roleId), org.mockito.ArgumentMatchers.eq(creatorId));
    }

    /** 改密必须验证旧密码、拒绝复用，并写入当前账号。 */
    @Test
    void 修改当前密码应验证旧密码并拒绝复用() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IdentityService service = service(mapper, encoder);
        UserPrincipal user = principal(false, "researcher", "stored-password");
        when(mapper.findById(user.userId())).thenReturn(user.account());
        when(encoder.matches("Current!Password123", "stored-password")).thenReturn(true);
        when(encoder.matches("New!Password123456", "stored-password")).thenReturn(false);
        when(encoder.encode("New!Password123456")).thenReturn("new-password-hash");

        service.changeOwnPassword(user, "Current!Password123", "New!Password123456");

        verify(mapper).resetPassword(user.organizationId(), user.userId(), "new-password-hash");
        when(encoder.matches("New!Password123456", "stored-password")).thenReturn(true);
        assertThatThrownBy(() -> service.changeOwnPassword(user, "Current!Password123", "New!Password123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("validation_error");
    }

    /** 会话只显式声明平台控制面权限，不将其伪装成跨组织业务权限。 */
    @Test
    void 会话应返回平台管理员标记() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService service = service(mapper, mock(PasswordEncoder.class));
        UserPrincipal platformAdmin = new UserPrincipal(new UserAccount(UUID.randomUUID(), UUID.randomUUID(), "platform", "",
                "平台管理员", "active", true, true, 0), List.of());

        var session = service.session(platformAdmin);

        assertThat(session).containsEntry("is_platform_admin", true).containsEntry("is_super_admin", true);
    }

    /** 普通用户管理不能伪造超级管理员角色；该特权仅能由受控初始化流程建立。 */
    @Test
    void 创建普通用户时不得分配超级管理员角色() throws Exception {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService service = service(mapper, mock(PasswordEncoder.class));

        assertThatThrownBy(() -> service.createManagedUser(principal(true, "admin", "stored"), objectMapper.readTree("""
                {"username":"member","display_name":"普通成员","password":"Member123",
                 "status":"active","role_codes":["super_admin"]}
                """)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("validation_error");

        verify(mapper, never()).insertManagedUser(any());
    }

    /** 兼容旧双身份数据时，平台控制面账号仍不得进入租户用户管理。 */
    @Test
    void 双身份旧账号不得创建租户成员() throws Exception {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService service = service(mapper, mock(PasswordEncoder.class));
        UserPrincipal legacyDualAdmin = new UserPrincipal(new UserAccount(UUID.randomUUID(), UUID.randomUUID(), "legacy-admin", "stored",
                "旧双身份管理员", "active", true, true, 0), List.of());

        assertThatThrownBy(() -> service.createManagedUser(legacyDualAdmin, objectMapper.readTree("""
                {"username":"member","display_name":"普通成员","password":"Member123",
                 "status":"active","role_codes":["researcher"]}
                """)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("permission_denied");

        verify(mapper, never()).insertManagedUser(any());
    }

    /** 删除普通用户必须撤销可继续发放账号的能力、清除角色，并使原会话在下一请求失效。 */
    @Test
    void 删除普通用户应逻辑删除并留下最小审计() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IdentityService service = service(mapper, encoder);
        UserPrincipal admin = principal(true, "admin", "stored");
        UUID userId = UUID.randomUUID();
        UserAccount member = new UserAccount(userId, admin.organizationId(), "member", "old-password",
                "普通成员", "active", false, false, 0);
        when(mapper.findById(userId)).thenReturn(member);
        when(encoder.encode(anyString())).thenReturn("unusable-password-hash");
        when(mapper.anonymizeDeletedUser(any())).thenReturn(1);

        service.deleteManagedUser(admin, userId);

        verify(mapper).revokeActiveRegistrationInvitationsByCreator(admin.organizationId(), userId);
        verify(mapper).deleteUserRoles(admin.organizationId(), userId);
        ArgumentCaptor<IdentityMapper.UserDeletionCommand> deletion = ArgumentCaptor.forClass(IdentityMapper.UserDeletionCommand.class);
        verify(mapper).anonymizeDeletedUser(deletion.capture());
        assertThat(deletion.getValue().anonymizedUsername()).isEqualTo("deleted_" + userId.toString().replace("-", ""));
        verify(mapper).insertUserDeletionOperationLog(any());
    }

    /** 活动项目或未完成实验的负责人不能被删除，以免制造没有负责人的业务资源。 */
    @Test
    void 删除仍负责活动资源的普通用户应要求先交接() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService service = service(mapper, mock(PasswordEncoder.class));
        UserPrincipal admin = principal(true, "admin", "stored");
        UUID userId = UUID.randomUUID();
        when(mapper.findById(userId)).thenReturn(new UserAccount(userId, admin.organizationId(), "member", "old-password",
                "普通成员", "active", false, false, 0));
        when(mapper.countActiveOwnedProjects(admin.organizationId(), userId)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteManagedUser(admin, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("user_resource_handover_required");

        verify(mapper, never()).anonymizeDeletedUser(any());
    }

    private IdentityService service(IdentityMapper mapper, PasswordEncoder encoder) {
        return new IdentityService(mock(DatabaseUserDetailsService.class), mapper, encoder,
                new AccessControlService(), new LoginAttemptGuard());
    }

    private UserPrincipal principal(boolean superAdmin, String username, String password) {
        return new UserPrincipal(new UserAccount(UUID.randomUUID(), UUID.randomUUID(), username, password,
                "测试用户", "active", superAdmin, false, 0), List.of());
    }
}
