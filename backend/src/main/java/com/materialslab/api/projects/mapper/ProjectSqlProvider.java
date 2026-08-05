package com.materialslab.api.projects.mapper;

import java.util.Map;
import org.apache.ibatis.jdbc.SQL;

/** 项目组合筛选 SQL 生成器。 */
public class ProjectSqlProvider {
    /** 根据组织范围、关键字和状态生成项目列表查询。 */
    public String findVisible(Map<String, Object> parameters) {
        String status = (String) parameters.get("status");
        String search = (String) parameters.get("search");
        StringBuilder sql = new StringBuilder("""
                WITH RECURSIVE visible_org AS (
                  SELECT id FROM organization WHERE id = #{organizationId}
                  UNION ALL SELECT child.id FROM organization child JOIN visible_org parent ON child.parent_id = parent.id
                )
                SELECT p.id, p.organization_id, p.project_no, p.name, p.project_type_code, p.description,
                       p.current_stage, p.progress_percent, p.status, p.owner_id, owner.display_name owner_name,
                       p.objectives::text, p.milestones::text, p.document_count, p.experiment_count, p.data_resource_count,
                       p.planned_start_date, p.planned_end_date, p.actual_end_at, p.archived_at,
                       p.version, p.created_at, p.updated_at
                FROM project p JOIN user_account owner ON owner.id = p.owner_id
                WHERE (p.organization_id IN (SELECT id FROM visible_org)
                  OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
                """);
        if (status != null && !status.isBlank()) sql.append(" AND p.status = #{status}");
        if (search != null && !search.isBlank()) sql.append(" AND (p.project_no ILIKE '%' || #{search} || '%' OR p.name ILIKE '%' || #{search} || '%')");
        sql.append(" ORDER BY p.updated_at DESC LIMIT #{limit} OFFSET #{offset}");
        return sql.toString();
    }

    /** 根据与列表一致的数据范围和筛选条件生成项目总数查询。 */
    public String countVisible(Map<String, Object> parameters) {
        String status = (String) parameters.get("status");
        String search = (String) parameters.get("search");
        StringBuilder sql = new StringBuilder("""
                WITH RECURSIVE visible_org AS (
                  SELECT id FROM organization WHERE id = #{organizationId}
                  UNION ALL SELECT child.id FROM organization child JOIN visible_org parent ON child.parent_id = parent.id
                )
                SELECT COUNT(*) FROM project p
                WHERE (p.organization_id IN (SELECT id FROM visible_org)
                  OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
                """);
        if (status != null && !status.isBlank()) sql.append(" AND p.status = #{status}");
        if (search != null && !search.isBlank()) sql.append(" AND (p.project_no ILIKE '%' || #{search} || '%' OR p.name ILIKE '%' || #{search} || '%')");
        return sql.toString();
    }
}
