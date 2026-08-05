package com.materialslab.api.experiments.mapper;

import java.util.Map;

/** 实验列表筛选 SQL 生成器。 */
public class ExperimentSqlProvider {
    /** 根据数据范围、状态、项目和关键字拼装查询。 */
    public String findVisible(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.organization_id, e.project_id, project.project_no, project.name project_name, e.experiment_no, e.name, e.experiment_type, e.phase, e.status,
                       e.purpose, e.owner_id, owner.display_name owner_display_name, e.version, e.estimated_start, e.estimated_end,
                       e.started_at, e.completed_at, e.created_at, e.updated_at
                FROM experiment e JOIN user_account owner ON owner.id = e.owner_id JOIN project ON project.id = e.project_id
                WHERE e.organization_id = #{organizationId}
                  AND (#{readAll} = TRUE OR project.owner_id = #{userId}
                    OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = project.id AND member.user_id = #{userId}))
                """);
        if (parameters.get("projectId") != null) sql.append(" AND e.project_id = #{projectId}");
        if (parameters.get("status") != null && !parameters.get("status").toString().isBlank()) sql.append(" AND e.status = #{status}");
        if (parameters.get("search") != null && !parameters.get("search").toString().isBlank()) sql.append(" AND (e.experiment_no ILIKE '%' || #{search} || '%' OR e.name ILIKE '%' || #{search} || '%')");
        sql.append(" ORDER BY e.updated_at DESC LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    /** 根据与列表一致的筛选条件生成实验总数查询。 */
    public String countVisible(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM experiment e JOIN project ON project.id = e.project_id
                WHERE e.organization_id = #{organizationId}
                  AND (#{readAll} = TRUE OR project.owner_id = #{userId}
                    OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = project.id AND member.user_id = #{userId}))
                """);
        if (parameters.get("projectId") != null) sql.append(" AND e.project_id = #{projectId}");
        if (parameters.get("status") != null && !parameters.get("status").toString().isBlank()) sql.append(" AND e.status = #{status}");
        if (parameters.get("search") != null && !parameters.get("search").toString().isBlank()) sql.append(" AND (e.experiment_no ILIKE '%' || #{search} || '%' OR e.name ILIKE '%' || #{search} || '%')");
        return sql.toString();
    }
}
