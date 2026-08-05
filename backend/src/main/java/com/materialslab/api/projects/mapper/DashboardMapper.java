package com.materialslab.api.projects.mapper;

import com.materialslab.api.projects.domain.DashboardRows.ActiveProject;
import com.materialslab.api.projects.domain.DashboardRows.Metrics;
import com.materialslab.api.projects.domain.DashboardRows.ProjectOption;
import com.materialslab.api.projects.domain.DashboardRows.TrendEntry;
import com.materialslab.api.projects.domain.DashboardRows.TypeDistribution;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作台实时聚合数据 Mapper。 */
@Mapper
public interface DashboardMapper {
    /** 查询项目状态统计。 */
    @Select("""
            SELECT COUNT(*) total, COUNT(*) FILTER (WHERE status = 'active') active,
              COUNT(*) FILTER (WHERE status = 'archived') archived, COUNT(*) FILTER (WHERE status = 'at_risk') at_risk,
              0 in_progress, 0 completed
            FROM project WHERE organization_id = #{organizationId}
            """)
    Metrics projectMetrics(@Param("organizationId") UUID organizationId);

    /** 查询实验状态统计。 */
    @Select("""
            SELECT COUNT(*) total, 0 active, 0 archived, 0 at_risk,
              COUNT(*) FILTER (WHERE status = 'in_progress') in_progress,
              COUNT(*) FILTER (WHERE status = 'completed') completed
            FROM experiment WHERE organization_id = #{organizationId}
            """)
    Metrics experimentMetrics(@Param("organizationId") UUID organizationId);

    /** 按研发类型聚合项目和实验。 */
    @Select("""
            SELECT project_type_code name, COUNT(*) project_count,
              (SELECT COUNT(*) FROM experiment e WHERE e.organization_id = #{organizationId}
                AND e.experiment_type = project.project_type_code) experiment_count
            FROM project WHERE organization_id = #{organizationId}
            GROUP BY project_type_code ORDER BY project_type_code
            """)
    List<TypeDistribution> typeDistribution(@Param("organizationId") UUID organizationId);

    /** 查询近三十日的真实实验创建趋势。 */
    @Select("""
            SELECT DATE(created_at) date, experiment_type type_name, COUNT(*) experiment_count
            FROM experiment WHERE organization_id = #{organizationId} AND created_at >= #{startDate}
            GROUP BY DATE(created_at), experiment_type ORDER BY DATE(created_at), experiment_type
            """)
    List<TrendEntry> trendEntries(@Param("organizationId") UUID organizationId, @Param("startDate") LocalDate startDate);

    /** 查询进行中或风险项目。 */
    @Select("""
            SELECT p.id, p.project_no, p.name, p.project_type_code, owner.display_name owner_name, p.objectives::text,
              p.milestones::text, p.progress_percent, p.planned_start_date, p.planned_end_date,
              EXISTS(SELECT 1 FROM project_follow follow WHERE follow.project_id = p.id AND follow.user_id = #{userId}) followed
            FROM project p JOIN user_account owner ON owner.id = p.owner_id
            WHERE p.organization_id = #{organizationId} AND p.status IN ('active', 'at_risk')
            ORDER BY p.updated_at DESC LIMIT 6
            """)
    List<ActiveProject> activeProjects(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    /** 查询可筛选的项目选项。 */
    @Select("SELECT id, project_no, name FROM project WHERE organization_id = #{organizationId} ORDER BY project_no")
    List<ProjectOption> projectOptions(@Param("organizationId") UUID organizationId);
}
