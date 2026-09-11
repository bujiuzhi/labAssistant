package com.materialslab.api.identity.mapper;

import com.materialslab.api.identity.domain.PlatformOrganization;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 平台控制面组织开通所需的最小数据库操作，不参与任何租户业务查询。 */
@Mapper
public interface PlatformOrganizationMapper {
    /** 返回所有组织的基础元数据，供平台管理员维护租户目录。 */
    @Select("""
            SELECT id, organization_code, name, status, created_at
            FROM organization WHERE is_platform = FALSE ORDER BY created_at DESC, organization_code
            """)
    List<PlatformOrganization> listOrganizations();

    /** 创建一个根组织；首版不开放组织层级维护。 */
    @Insert("""
            INSERT INTO organization (id, organization_code, name, status, created_at, updated_at)
            VALUES (#{id}, #{organizationCode}, #{name}, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertOrganization(@Param("id") UUID id, @Param("organizationCode") String organizationCode, @Param("name") String name);

    /** 创建该组织唯一的初始超级管理员；平台权限不会向新管理员继承。 */
    @Insert("""
            INSERT INTO user_account (id, organization_id, password, username, display_name, email, status, is_active,
                                      is_super_admin, is_platform_admin, is_superuser, is_staff, created_at, updated_at)
            VALUES (#{id}, #{organizationId}, #{password}, #{username}, #{displayName}, #{email}, 'active', TRUE,
                    TRUE, FALSE, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertOrganizationAdmin(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                                @Param("password") String password, @Param("username") String username,
                                @Param("displayName") String displayName, @Param("email") String email);

    /** 读取全局权限字典，初始化每个组织的内置角色。 */
    @Select("SELECT id, permission_code FROM permission")
    List<PermissionRow> listPermissions();

    /** 创建组织内置角色。 */
    @Insert("""
            INSERT INTO role (id, organization_id, role_code, name, description, is_system, status, created_at, updated_at)
            VALUES (#{id}, #{organizationId}, #{roleCode}, #{name}, #{description}, TRUE, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertSystemRole(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                         @Param("roleCode") String roleCode, @Param("name") String name, @Param("description") String description);

    /** 关联角色和权限。 */
    @Insert("INSERT INTO role_permission (id, role_id, permission_id) VALUES (#{id}, #{roleId}, #{permissionId})")
    int insertRolePermission(@Param("id") UUID id, @Param("roleId") UUID roleId, @Param("permissionId") UUID permissionId);

    /** 为新组织管理员绑定组织内超级管理员角色。 */
    @Insert("""
            INSERT INTO user_role (id, organization_id, user_id, role_id, created_at, created_by_id)
            VALUES (#{id}, #{organizationId}, #{userId}, #{roleId}, CURRENT_TIMESTAMP, #{createdById})
            """)
    int insertUserRole(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                       @Param("userId") UUID userId, @Param("roleId") UUID roleId, @Param("createdById") UUID createdById);

    /** 全局权限字典行。 */
    record PermissionRow(UUID id, String permissionCode) { }
}
