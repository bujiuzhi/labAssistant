package com.materialslab.api.projects.mapper;

import com.materialslab.api.projects.domain.Project;
import com.materialslab.api.projects.domain.ProjectMember;
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
                              @Param("readAll") boolean readAll,
                              @Param("status") String status, @Param("search") String search,
                              @Param("limit") int limit, @Param("offset") int offset);

    /** 统计当前用户可见项目总数。 */
    @SelectProvider(type = ProjectSqlProvider.class, method = "countVisible")
    long countVisible(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                      @Param("readAll") boolean readAll,
                      @Param("status") String status, @Param("search") String search);

    /** 按项目主键或项目编号查询当前用户可见项目。 */
    @Select("""
            SELECT p.id, p.organization_id, p.project_no, p.name, p.project_type_code, p.description,
                   p.current_stage, p.progress_percent, p.status, p.owner_id, owner.display_name owner_name,
                   p.objectives::text, p.milestones::text, p.document_count, p.experiment_count, p.data_resource_count,
                   p.planned_start_date, p.planned_end_date, p.actual_end_at, p.archived_at,
                   p.version, p.created_at, p.updated_at
            FROM project p JOIN user_account owner ON owner.id = p.owner_id
            WHERE (p.id::text = #{projectKey} OR p.project_no = #{projectKey})
              AND p.organization_id = #{organizationId}
              AND (#{readAll} = TRUE OR p.owner_id = #{userId}
                OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = p.id AND member.user_id = #{userId}))
            """)
    Project findByKey(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                      @Param("readAll") boolean readAll, @Param("projectKey") String projectKey);

    /** 判断项目成员是否拥有管理权限。 */
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM project p
              WHERE p.id = #{projectId} AND p.organization_id = #{organizationId}
                AND (p.owner_id = #{userId} OR EXISTS (
                  SELECT 1 FROM project_member member
                  WHERE member.project_id = p.id AND member.user_id = #{userId}
                    AND member.member_role IN ('owner', 'manager')))
            )
            """)
    boolean hasManageAccess(@Param("organizationId") UUID organizationId, @Param("projectId") UUID projectId,
                            @Param("userId") UUID userId);

    /** 校验负责人属于当前组织且处于启用状态。 */
    @Select("""
            SELECT EXISTS(SELECT 1 FROM user_account
              WHERE id = #{userId} AND organization_id = #{organizationId} AND status = 'active')
            """)
    boolean isActiveOrganizationUser(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    /** 查询项目实际成员。 */
    @Select("""
            SELECT member.user_id, account.display_name, member.member_role
            FROM project_member member JOIN user_account account ON account.id = member.user_id
            WHERE member.project_id = #{projectId} AND account.status <> 'deleted'
            ORDER BY member.joined_at, account.display_name
            """)
    List<ProjectMember> listMembers(@Param("projectId") UUID projectId);

    /** 查询项目的可见操作日志。 */
    @Select("""
            SELECT log.id, log.action_type, log.description, COALESCE(actor.display_name, '系统') AS actor_display_name,
                   log.changes::text, log.created_at
            FROM business_operation_log log LEFT JOIN user_account actor ON actor.id = log.actor_id
            WHERE log.organization_id = #{organizationId} AND log.domain = 'project' AND log.object_id = #{projectId}
            ORDER BY log.created_at DESC
            """)
    List<ProjectOperationLogRow> listOperationLogs(@Param("organizationId") UUID organizationId, @Param("projectId") UUID projectId);

    /** 新增项目。 */
    @Insert("""
            INSERT INTO project (id, organization_id, project_no, name, project_type_code, description, current_stage,
              progress_percent, document_count, experiment_count, data_resource_count, objectives, milestones, status,
              owner_id, planned_start_date, planned_end_date, version, created_by_id, updated_by_id, created_at, updated_at)
            VALUES (#{id}, #{organizationId}, #{projectNo}, #{name}, #{projectTypeCode}, #{description}, #{currentStage},
              #{progressPercent}, 0, 0, 0, CAST(#{objectives} AS jsonb), CAST(#{milestones} AS jsonb), #{status},
              #{ownerId}, #{plannedStartDate}, #{plannedEndDate}, 1, #{actorId}, #{actorId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insert(ProjectWriteCommand command);

    /** 将创建人加入项目，保证创建后可见并可继续管理。 */
    @Insert("""
            INSERT INTO project_member(id, organization_id, project_id, user_id, member_role, created_by_id)
            VALUES (#{id}, #{organizationId}, #{projectId}, #{userId}, 'manager', #{userId})
            ON CONFLICT (project_id, user_id) DO NOTHING
            """)
    int addCreatorAsManager(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                            @Param("projectId") UUID projectId, @Param("userId") UUID userId);

    /** 清除项目中由“人员组成”维护的普通实验员成员关系，保留负责人和项目管理成员。 */
    @Update("""
            DELETE FROM project_member
            WHERE organization_id = #{organizationId} AND project_id = #{projectId} AND member_role = 'researcher'
            """)
    int deleteResearcherMembers(@Param("organizationId") UUID organizationId, @Param("projectId") UUID projectId);

    /** 将当前组织的有效账号作为普通实验员加入项目。 */
    @Insert("""
            INSERT INTO project_member(id, organization_id, project_id, user_id, member_role, created_by_id)
            VALUES (#{id}, #{organizationId}, #{projectId}, #{userId}, 'researcher', #{createdById})
            ON CONFLICT (project_id, user_id) DO NOTHING
            """)
    int addResearcherMember(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                            @Param("projectId") UUID projectId, @Param("userId") UUID userId,
                            @Param("createdById") UUID createdById);

    /** 以版本号更新项目；返回零代表并发冲突。 */
    @Update("""
            UPDATE project SET name = #{name}, project_type_code = #{projectTypeCode}, description = #{description},
              current_stage = #{currentStage}, progress_percent = #{progressPercent}, objectives = CAST(#{objectives} AS jsonb),
              milestones = CAST(#{milestones} AS jsonb), status = #{status}, owner_id = #{ownerId},
              planned_start_date = #{plannedStartDate}, planned_end_date = #{plannedEndDate}, updated_by_id = #{actorId},
              version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{expectedVersion} AND status <> 'archived'
            """)
    int update(ProjectWriteCommand command);

    /** 以版本号归档项目。 */
    @Update("""
            UPDATE project SET status = 'archived', archived_at = CURRENT_TIMESTAMP, updated_by_id = #{actorId},
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

    /** 项目操作日志原始行，变更 JSON 由服务层转换。 */
    record ProjectOperationLogRow(UUID id, String actionType, String description, String actorDisplayName,
                                  String changes, java.time.OffsetDateTime createdAt) { }
}
