package com.materialslab.api.identity.mapper;

import com.materialslab.api.identity.domain.UserAccount;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
