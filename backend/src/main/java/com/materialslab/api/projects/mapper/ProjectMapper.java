package com.materialslab.api.projects.mapper;

import com.materialslab.api.projects.domain.Project;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

/** 项目域 Mapper。 */
@Mapper
public interface ProjectMapper {
    /** 查询用户可见项目。 */
    @SelectProvider(type = ProjectSqlProvider.class, method = "findVisible")
    List<Project> findVisible(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                              @Param("status") String status, @Param("search") String search,
                              @Param("limit") int limit, @Param("offset") int offset);

    /** 按项目编号查询项目。 */
    @Select("""
            SELECT p.id, p.organization_id, p.project_no, p.name, p.project_type_code, p.description,
                   p.current_stage, p.progress_percent, p.status, p.owner_id, owner.display_name owner_name,
                   p.objectives::text, p.milestones::text, p.planned_start_date, p.planned_end_date,
                   p.version, p.created_at, p.updated_at
            FROM project p JOIN user_account owner ON owner.id = p.owner_id
            WHERE p.project_no = #{projectNo} AND p.organization_id = #{organizationId}
            """)
    Project findByNo(@Param("organizationId") UUID organizationId, @Param("projectNo") String projectNo);

    /** 新增项目。 */
    @Insert("""
            INSERT INTO project (id, organization_id, project_no, name, project_type_code, description, current_stage,
              progress_percent, document_count, experiment_count, data_resource_count, objectives, milestones, status,
              owner_id, planned_start_date, planned_end_date, version, created_by, updated_by, created_at, updated_at)
            VALUES (#{id}, #{organizationId}, #{projectNo}, #{name}, #{projectTypeCode}, #{description}, #{currentStage},
              #{progressPercent}, 0, 0, 0, CAST(#{objectives} AS jsonb), CAST(#{milestones} AS jsonb), #{status},
              #{ownerId}, #{plannedStartDate}, #{plannedEndDate}, 1, #{actorId}, #{actorId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insert(ProjectWriteCommand command);

    /** 以版本号更新项目；返回零代表并发冲突。 */
    @Update("""
            UPDATE project SET name = #{name}, project_type_code = #{projectTypeCode}, description = #{description},
              current_stage = #{currentStage}, progress_percent = #{progressPercent}, objectives = CAST(#{objectives} AS jsonb),
              milestones = CAST(#{milestones} AS jsonb), status = #{status}, owner_id = #{ownerId},
              planned_start_date = #{plannedStartDate}, planned_end_date = #{plannedEndDate}, updated_by = #{actorId},
              version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{expectedVersion} AND status <> 'archived'
            """)
    int update(ProjectWriteCommand command);

    /** 以版本号归档项目。 */
    @Update("""
            UPDATE project SET status = 'archived', archived_at = CURRENT_TIMESTAMP, updated_by = #{actorId},
              version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{projectId} AND version = #{expectedVersion} AND status <> 'archived'
            """)
    int archive(@Param("projectId") UUID projectId, @Param("expectedVersion") int expectedVersion, @Param("actorId") UUID actorId);

    /** 创建关注关系。 */
    @Insert("""
            INSERT INTO project_follow (id, project_id, user_id, created_at) VALUES (#{id}, #{projectId}, #{userId}, CURRENT_TIMESTAMP)
            ON CONFLICT (project_id, user_id) DO NOTHING
            """)
    int follow(@Param("id") UUID id, @Param("projectId") UUID projectId, @Param("userId") UUID userId);

    /** 删除关注关系。 */
    @Update("DELETE FROM project_follow WHERE project_id = #{projectId} AND user_id = #{userId}")
    int unfollow(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    /** 统计当前用户数据范围内的项目数。 */
    @Select("SELECT COUNT(*) FROM project WHERE organization_id = #{organizationId}")
    long countByOrganization(@Param("organizationId") UUID organizationId);

    /** 项目写入参数。 */
    record ProjectWriteCommand(UUID id, UUID organizationId, String projectNo, String name, String projectTypeCode,
                               String description, String currentStage, int progressPercent, String objectives, String milestones,
                               String status, UUID ownerId, Object plannedStartDate, Object plannedEndDate,
                               UUID actorId, int expectedVersion) { }
}
