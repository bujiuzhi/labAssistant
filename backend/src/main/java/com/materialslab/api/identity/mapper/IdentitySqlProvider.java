package com.materialslab.api.identity.mapper;

import java.util.Map;

/** 身份域组合筛选 SQL 生成器。 */
public class IdentitySqlProvider {
    /** 根据组织、状态、角色和关键字生成管理员用户列表查询。 */
    public String listManagedUsers(Map<String, Object> parameters) {
        return managedUsersQuery(parameters, false) + " ORDER BY u.created_at DESC LIMIT #{limit} OFFSET #{offset}";
    }

    /** 根据与列表一致的筛选条件生成管理员用户总数查询。 */
    public String countManagedUsers(Map<String, Object> parameters) {
        return managedUsersQuery(parameters, true);
    }

    private String managedUsersQuery(Map<String, Object> parameters, boolean countOnly) {
        StringBuilder sql = new StringBuilder(countOnly ? "SELECT COUNT(*) FROM user_account u" : """
                SELECT u.id, u.username, u.display_name, u.email, u.status, u.is_active, u.is_super_admin,
                       COALESCE(string_agg(DISTINCT r.role_code, ','), '') AS role_codes,
                       COALESCE(string_agg(DISTINCT r.name, ','), '') AS role_names,
                       u.last_login, u.created_at, u.updated_at
                FROM user_account u
                LEFT JOIN user_role ur ON ur.user_id = u.id
                LEFT JOIN role r ON r.id = ur.role_id AND r.status = 'active'
                """);
        sql.append(" WHERE u.organization_id = #{organizationId} AND u.status <> 'deleted'");
        if (parameters.get("status") != null && !parameters.get("status").toString().isBlank()) sql.append(" AND u.status = #{status}");
        if (parameters.get("search") != null && !parameters.get("search").toString().isBlank()) {
            sql.append(" AND (u.username ILIKE '%' || #{search} || '%' OR u.display_name ILIKE '%' || #{search} || '%' OR u.email ILIKE '%' || #{search} || '%')");
        }
        if (parameters.get("roleCode") != null && !parameters.get("roleCode").toString().isBlank()) {
            sql.append(" AND EXISTS (SELECT 1 FROM user_role filtered_ur JOIN role filtered_r ON filtered_r.id = filtered_ur.role_id WHERE filtered_ur.user_id = u.id AND filtered_r.role_code = #{roleCode} AND filtered_r.status = 'active')");
        }
        if (!countOnly) sql.append(" GROUP BY u.id");
        return sql.toString();
    }
}
