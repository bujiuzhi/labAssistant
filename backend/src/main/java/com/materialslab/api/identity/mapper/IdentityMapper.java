package com.materialslab.api.identity.mapper;

import com.materialslab.api.identity.domain.ManagedRoleOption;
import com.materialslab.api.identity.domain.UserAccount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

/** 身份域数据库访问定义。 */
@Mapper
public interface IdentityMapper {
    /** 按登录名查询正常账户。 */
    @Select("""
            SELECT id, organization_id, username, password, display_name, status, is_super_admin
            FROM user_account
            WHERE username = #{username} AND status = 'active'
            ORDER BY is_super_admin DESC, created_at ASC LIMIT 1
            """)
    UserAccount findActiveByUsername(@Param("username") String username);

    /** 按主键查询账户。 */
    @Select("""
            SELECT id, organization_id, username, password, display_name, status, is_super_admin
            FROM user_account WHERE id = #{userId}
            """)
    UserAccount findById(@Param("userId") UUID userId);

    /** 返回当前组织及下级组织的有效用户。 */
    @Select("""
            WITH RECURSIVE visible_org AS (
                SELECT id FROM organization WHERE id = #{organizationId}
                UNION ALL
                SELECT child.id FROM organization child JOIN visible_org parent ON child.parent_id = parent.id
            )
            SELECT id, organization_id, username, password, display_name, status, is_super_admin
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

    /** 管理员用户查询的原始行，角色字符串由服务层转换为列表。 */
    record ManagedUserRow(UUID id, String username, String displayName, String email, String status, boolean active,
                          boolean superAdmin, String roleCodes, String roleNames, OffsetDateTime lastLogin,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
}
