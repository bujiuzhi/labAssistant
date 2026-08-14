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

/** 工作台实时聚合数据 Mapper，所有聚合均复用当前用户的项目数据范围。 */
@Mapper
public interface DashboardMapper {
    /** 查询用户可见项目状态统计。 */
    @Select("""
            SELECT COUNT(*) total, COUNT(*) FILTER (WHERE status = 'active') active,
              COUNT(*) FILTER (WHERE status = 'archived') archived, COUNT(*) FILTER (WHERE status = 'at_risk') at_risk,
              0 in_progress, 0 completed
            FROM project p WHERE p.organization_id = #{organizationId}
              AND (#{readAll} = TRUE OR p.owner_id = #{userId}
                OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
            """)
    Metrics projectMetrics(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                           @Param("readAll") boolean readAll);

    /** 查询用户可见实验状态统计。 */
    @Select("""
            SELECT COUNT(*) total, 0 active, 0 archived, 0 at_risk,
              COUNT(*) FILTER (WHERE e.status = 'in_progress') in_progress,
              COUNT(*) FILTER (WHERE e.status = 'completed') completed
            FROM experiment e JOIN project p ON p.id = e.project_id
            WHERE e.organization_id = #{organizationId}
              AND (#{readAll} = TRUE OR p.owner_id = #{userId}
                OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
            """)
    Metrics experimentMetrics(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                              @Param("readAll") boolean readAll);

    /** 按研发类型聚合用户可见项目和实验。 */
    @Select("""
            WITH visible_project AS (
              SELECT p.id, p.project_type_code FROM project p WHERE p.organization_id = #{organizationId}
                AND (#{readAll} = TRUE OR p.owner_id = #{userId}
                  OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
            )
            SELECT project_type_code name, COUNT(*) project_count,
              (SELECT COUNT(*) FROM experiment e JOIN visible_project experiment_project ON experiment_project.id = e.project_id
                WHERE experiment_project.project_type_code = visible_project.project_type_code) experiment_count
            FROM visible_project GROUP BY project_type_code ORDER BY project_type_code
            """)
    List<TypeDistribution> typeDistribution(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                                            @Param("readAll") boolean readAll);

    /** 查询近三十日用户可见实验的真实创建趋势。 */
    @Select("""
            SELECT DATE(e.created_at) date, e.experiment_type type_name, COUNT(*) experiment_count
            FROM experiment e JOIN project p ON p.id = e.project_id
            WHERE e.organization_id = #{organizationId} AND e.created_at >= #{startDate}
              AND (#{readAll} = TRUE OR p.owner_id = #{userId}
                OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
            GROUP BY DATE(e.created_at), e.experiment_type ORDER BY DATE(e.created_at), e.experiment_type
            """)
    List<TrendEntry> trendEntries(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                                  @Param("readAll") boolean readAll, @Param("startDate") LocalDate startDate);

    /** 查询用户可见的进行中或风险项目。 */
    @Select("""
            SELECT p.id, p.project_no, p.name, p.project_type_code, owner.display_name owner_name, p.objectives::text,
              p.milestones::text, p.progress_percent, p.planned_start_date, p.planned_end_date,
              EXISTS(SELECT 1 FROM project_follow follow WHERE follow.project_id = p.id AND follow.user_id = #{userId}) followed
            FROM project p JOIN user_account owner ON owner.id = p.owner_id
            WHERE p.organization_id = #{organizationId} AND p.status IN ('active', 'at_risk')
              AND (#{readAll} = TRUE OR p.owner_id = #{userId}
                OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
            ORDER BY followed DESC, p.updated_at DESC LIMIT 6
            """)
    List<ActiveProject> activeProjects(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                                       @Param("readAll") boolean readAll);

    /** 查询当前用户可筛选的项目选项。 */
    @Select("""
            SELECT p.id, p.project_no, p.name FROM project p
            WHERE p.organization_id = #{organizationId}
              AND (#{readAll} = TRUE OR p.owner_id = #{userId}
                OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
            ORDER BY p.project_no
            """)
    List<ProjectOption> projectOptions(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                                       @Param("readAll") boolean readAll);
}
