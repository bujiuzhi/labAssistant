package com.materialslab.api.identity.mapper;

import com.materialslab.api.identity.domain.ManagedRoleOption;
import com.materialslab.api.identity.domain.RegistrationInvitation;
import com.materialslab.api.identity.domain.UserAccount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

/** 身份域数据库访问定义。 */
@Mapper
public interface IdentityMapper {
    /** 按登录名查询正常账户。 */
    @Select("""
            SELECT id, organization_id, username, password, display_name, status, is_super_admin, is_platform_admin, session_version
            FROM user_account
            WHERE username = #{username} AND status = 'active'
            ORDER BY is_super_admin DESC, created_at ASC LIMIT 1
            """)
    UserAccount findActiveByUsername(@Param("username") String username);

    /** 按主键查询账户。 */
    @Select("""
            SELECT id, organization_id, username, password, display_name, status, is_super_admin, is_platform_admin, session_version
            FROM user_account WHERE id = #{userId}
            """)
    UserAccount findById(@Param("userId") UUID userId);

    /** 校验登录名是否已由其他账户使用；登录界面不携带组织，因此登录名全局唯一。 */
    @Select("SELECT EXISTS(SELECT 1 FROM user_account WHERE username = #{username} AND id <> #{excludedUserId})")
    boolean existsUsernameExcluding(@Param("username") String username, @Param("excludedUserId") UUID excludedUserId);

    /** 返回当前组织及下级组织的有效用户。 */
    @Select("""
            WITH RECURSIVE visible_org AS (
                SELECT id FROM organization WHERE id = #{organizationId}
                UNION ALL
                SELECT child.id FROM organization child JOIN visible_org parent ON child.parent_id = parent.id
            )
            SELECT id, organization_id, username, password, display_name, status, is_super_admin, is_platform_admin, session_version
            FROM user_account WHERE organization_id IN (SELECT id FROM visible_org) AND status = 'active'
            ORDER BY display_name, username
            """)
    List<UserAccount> listVisibleActiveUsers(@Param("organizationId") UUID organizationId);

    /** 查询用户已分配角色代码。 */
    @Select("""
            SELECT role.role_code FROM role
            JOIN user_role ON user_role.role_id = role.id
            WHERE user_role.user_id = #{userId} AND role.status = 'active'
            ORDER BY role.role_code
            """)
    List<String> listRoleCodes(@Param("userId") UUID userId);

    /** 查询用户已分配角色名称。 */
    @Select("""
            SELECT role.name FROM role
            JOIN user_role ON user_role.role_id = role.id
            WHERE user_role.user_id = #{userId} AND role.status = 'active'
            ORDER BY role.role_code
            """)
    List<String> listRoleNames(@Param("userId") UUID userId);

    /** 查询用户业务权限代码。 */
    @Select("""
            SELECT DISTINCT permission.permission_code FROM permission
            JOIN role_permission ON role_permission.permission_id = permission.id
            JOIN user_role ON user_role.role_id = role_permission.role_id
            JOIN role ON role.id = user_role.role_id
            WHERE user_role.user_id = #{userId} AND role.status = 'active'
            ORDER BY permission.permission_code
            """)
    List<String> listPermissionCodes(@Param("userId") UUID userId);

    /** 查询当前组织的管理员用户列表。 */
    @SelectProvider(type = IdentitySqlProvider.class, method = "listManagedUsers")
    List<ManagedUserRow> listManagedUsers(@Param("organizationId") UUID organizationId, @Param("status") String status,
                                          @Param("search") String search, @Param("roleCode") String roleCode,
                                          @Param("limit") int limit, @Param("offset") int offset);

    /** 统计当前组织的管理员用户总数。 */
    @SelectProvider(type = IdentitySqlProvider.class, method = "countManagedUsers")
    long countManagedUsers(@Param("organizationId") UUID organizationId, @Param("status") String status,
                           @Param("search") String search, @Param("roleCode") String roleCode);

    /** 查询当前组织可分配的有效系统角色。 */
    @Select("""
            SELECT role_code, name, description FROM role
            WHERE organization_id = #{organizationId} AND status = 'active'
            ORDER BY is_system DESC, role_code
            """)
    List<ManagedRoleOption> listManagedRoleOptions(@Param("organizationId") UUID organizationId);

    /** 创建组织用户。 */
    @Insert("""
            INSERT INTO user_account (id, organization_id, password, username, display_name, email, status, is_active, is_super_admin, created_at, updated_at)
            VALUES (#{id}, #{organizationId}, #{password}, #{username}, #{displayName}, #{email}, #{status}, #{active}, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertManagedUser(UserWriteCommand command);

    /** 更新组织用户基础信息。 */
    @Update("""
            UPDATE user_account SET username = #{username}, display_name = #{displayName}, email = #{email}, status = #{status},
            is_active = #{active}, session_version = session_version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND organization_id = #{organizationId}
            """)
    int updateManagedUser(UserWriteCommand command);

    /** 重置组织用户密码。 */
    @Update("UPDATE user_account SET password = #{password}, session_version = session_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{userId} AND organization_id = #{organizationId}")
    int resetPassword(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId, @Param("password") String password);

    /** 创建只保存哈希的一次性邀请码。 */
    @Insert("""
            INSERT INTO registration_invitation (id, organization_id, role_id, created_by_id, code_hash, expires_at)
            VALUES (#{id}, #{organizationId}, #{roleId}, #{createdById}, #{codeHash}, #{expiresAt})
            """)
    int insertRegistrationInvitation(RegistrationInvitationCommand command);

    /** 查询当前组织的邀请码元数据，不返回邀请码哈希。 */
    @Select("""
            SELECT invitation.id, role.role_code, role.name AS role_name,
                   CASE WHEN invitation.status = 'active' AND invitation.expires_at <= CURRENT_TIMESTAMP THEN 'expired' ELSE invitation.status END AS status,
                   invitation.expires_at,
                   invitation.used_at, invitation.created_at
            FROM registration_invitation invitation JOIN role ON role.id = invitation.role_id
            WHERE invitation.organization_id = #{organizationId}
            ORDER BY invitation.created_at DESC LIMIT 100
            """)
    List<RegistrationInvitation> listRegistrationInvitations(@Param("organizationId") UUID organizationId);

    /** 按邀请码哈希读取仍可使用的邀请码；过期邀请码永不返回。 */
    @Select("""
            SELECT invitation.id, invitation.organization_id, invitation.role_id, invitation.created_by_id
            FROM registration_invitation invitation
            JOIN role ON role.id = invitation.role_id AND role.organization_id = invitation.organization_id
            WHERE invitation.code_hash = #{codeHash} AND invitation.status = 'active' AND invitation.expires_at > CURRENT_TIMESTAMP
              AND role.status = 'active' AND role.role_code <> 'super_admin'
            """)
    ActiveRegistrationInvitation findActiveRegistrationInvitation(@Param("codeHash") String codeHash);

    /** 原子标记邀请码已使用；并发注册时仅一个事务可成功。 */
    @Update("""
            UPDATE registration_invitation
            SET status = 'used', used_at = CURRENT_TIMESTAMP, used_by_id = #{userId}
            WHERE id = #{invitationId} AND status = 'active' AND expires_at > CURRENT_TIMESTAMP
            """)
    int consumeRegistrationInvitation(@Param("invitationId") UUID invitationId, @Param("userId") UUID userId);

    /** 撤销当前组织仍未使用的邀请码。 */
    @Update("""
            UPDATE registration_invitation SET status = 'revoked'
            WHERE id = #{invitationId} AND organization_id = #{organizationId} AND status = 'active'
            """)
    int revokeRegistrationInvitation(@Param("organizationId") UUID organizationId, @Param("invitationId") UUID invitationId);

    /** 删除用户原有角色。 */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId} AND organization_id = #{organizationId}")
    int deleteUserRoles(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    /** 按角色代码查询组织角色主键。 */
    @Select("SELECT id FROM role WHERE organization_id = #{organizationId} AND role_code = #{roleCode} AND status = 'active'")
    UUID findRoleId(@Param("organizationId") UUID organizationId, @Param("roleCode") String roleCode);

    /** 新增用户角色关联。 */
    @Insert("INSERT INTO user_role (id, organization_id, user_id, role_id, created_at, created_by_id) VALUES (#{id}, #{organizationId}, #{userId}, #{roleId}, CURRENT_TIMESTAMP, #{actorId})")
    int insertUserRole(@Param("id") UUID id, @Param("organizationId") UUID organizationId, @Param("userId") UUID userId, @Param("roleId") UUID roleId, @Param("actorId") UUID actorId);

    /** 管理员用户查询的原始行，角色字符串由服务层转换为列表。 */
    record ManagedUserRow(UUID id, String username, String displayName, String email, String status, boolean active,
                          boolean superAdmin, String roleCodes, String roleNames, OffsetDateTime lastLogin,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt) { }

    /** 用户管理写入参数。 */
    record UserWriteCommand(UUID id, UUID organizationId, String password, String username, String displayName, String email,
                            String status, boolean active) { }

    /** 签发邀请码的持久化参数，明文邀请码绝不进入该对象。 */
    record RegistrationInvitationCommand(UUID id, UUID organizationId, UUID roleId, UUID createdById, String codeHash,
                                         OffsetDateTime expiresAt) { }

    /** 注册时的内部邀请码信息，不能序列化给客户端。 */
    record ActiveRegistrationInvitation(UUID id, UUID organizationId, UUID roleId, UUID createdById) { }
}
