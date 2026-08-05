package com.materialslab.api.experiments.mapper;

import com.materialslab.api.experiments.domain.Experiment;
import com.materialslab.api.experiments.domain.ExperimentAttachment;
import com.materialslab.api.experiments.domain.ExperimentParticipant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

/** 实验域 Mapper。 */
@Mapper
public interface ExperimentMapper {
    /** 查询实验列表。 */
    @SelectProvider(type = ExperimentSqlProvider.class, method = "findVisible")
    List<Experiment> findVisible(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                                 @Param("readAll") boolean readAll, @Param("projectId") UUID projectId,
                                 @Param("status") String status, @Param("search") String search,
                                 @Param("limit") int limit, @Param("offset") int offset);
    /** 统计当前组织内符合条件的实验总数。 */
    @SelectProvider(type = ExperimentSqlProvider.class, method = "countVisible")
    long countVisible(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                      @Param("readAll") boolean readAll, @Param("projectId") UUID projectId,
                      @Param("status") String status, @Param("search") String search);
    /** 按业务编号查询实验。 */
    @Select("""
        SELECT e.id, e.organization_id, e.project_id, project.project_no, project.name project_name, e.experiment_no, e.name, e.experiment_type, e.phase, e.status,
        e.purpose, e.owner_id, owner.display_name owner_display_name, e.version, e.estimated_start, e.estimated_end,
        e.started_at, e.completed_at, e.created_at, e.updated_at FROM experiment e JOIN user_account owner ON owner.id=e.owner_id JOIN project ON project.id=e.project_id
        WHERE e.organization_id=#{organizationId} AND e.experiment_no=#{experimentNo}
          AND (#{readAll} = TRUE OR project.owner_id = #{userId}
            OR EXISTS (SELECT 1 FROM project_member member WHERE member.project_id = project.id AND member.user_id = #{userId}))
        """)
    Experiment findByNo(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId,
                        @Param("readAll") boolean readAll, @Param("experimentNo") String experimentNo);

    /** 判断用户是否是实验负责人、参与人或项目管理成员。 */
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM experiment e JOIN project p ON p.id = e.project_id
              WHERE e.id = #{experimentId} AND e.organization_id = #{organizationId}
                AND (e.owner_id = #{userId}
                  OR EXISTS (SELECT 1 FROM experiment_participant participant
                             WHERE participant.experiment_id = e.id AND participant.user_id = #{userId})
                  OR EXISTS (SELECT 1 FROM project_member member
                             WHERE member.project_id = p.id AND member.user_id = #{userId}
                               AND member.member_role IN ('owner', 'manager')))
            )
            """)
    boolean hasWriteAccess(@Param("organizationId") UUID organizationId, @Param("experimentId") UUID experimentId,
                           @Param("userId") UUID userId);

    /** 判断用户是否为实验负责人或参与人，不包含项目负责人权限。 */
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM experiment e
              WHERE e.id = #{experimentId} AND e.organization_id = #{organizationId}
                AND (e.owner_id = #{userId}
                  OR EXISTS (SELECT 1 FROM experiment_participant participant
                             WHERE participant.experiment_id = e.id AND participant.user_id = #{userId}))
            )
            """)
    boolean hasDirectWriteAccess(@Param("organizationId") UUID organizationId, @Param("experimentId") UUID experimentId,
                                 @Param("userId") UUID userId);

    /** 判断用户是否拥有项目管理成员资格。 */
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
    boolean hasProjectManageAccess(@Param("organizationId") UUID organizationId, @Param("projectId") UUID projectId,
                                   @Param("userId") UUID userId);

    /** 校验实验负责人属于当前组织且处于启用状态。 */
    @Select("""
            SELECT EXISTS(SELECT 1 FROM user_account
              WHERE id = #{userId} AND organization_id = #{organizationId} AND status = 'active')
            """)
    boolean isActiveOrganizationUser(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    /** 查询实验电子记录内容。 */
    @Select("""
            SELECT formula_columns::text, formula_rows::text, extra_tables::text, process_text, extra_processes::text, result_text
            FROM experiment_record WHERE experiment_id = #{experimentId}
            """)
    ExperimentRecordRow findRecord(@Param("experimentId") UUID experimentId);

    /** 查询实验参与人。 */
    @Select("""
            SELECT participant.user_id, account.display_name
            FROM experiment_participant participant JOIN user_account account ON account.id = participant.user_id
            WHERE participant.experiment_id = #{experimentId}
            ORDER BY participant.joined_at, account.display_name
            """)
    List<ExperimentParticipant> listParticipants(@Param("experimentId") UUID experimentId);

    /** 查询实验附件元数据。 */
    @Select("""
            SELECT id, name, file AS url, CASE WHEN file_size < 1024 THEN file_size || ' B' ELSE ROUND(file_size / 1024.0, 1) || ' KB' END AS size
            FROM experiment_attachment WHERE experiment_id = #{experimentId} AND kind = #{kind}
            ORDER BY created_at
            """)
    List<ExperimentAttachment> listAttachments(@Param("experimentId") UUID experimentId, @Param("kind") String kind);
    /** 新建实验和默认 ELN。 */
    @Insert("""
        INSERT INTO experiment (id, organization_id, project_id, experiment_no, name, experiment_type, phase, status, purpose, owner_id, version, created_by_id, updated_by_id, created_at, updated_at)
        VALUES (#{id}, #{organizationId}, #{projectId}, #{experimentNo}, #{name}, #{experimentType}, #{phase}, #{status}, #{purpose}, #{ownerId}, 1, #{actorId}, #{actorId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """)
    int insert(ExperimentCommand command);

    /** 更新实验基础信息并进行乐观锁校验。 */
    @Update("""
        UPDATE experiment SET project_id = #{projectId}, name = #{name}, experiment_type = #{experimentType}, phase = #{phase},
        purpose = #{purpose}, estimated_start = CAST(#{estimatedStart} AS timestamptz), estimated_end = CAST(#{estimatedEnd} AS timestamptz),
        owner_id = #{ownerId}, updated_by_id = #{actorId}, version = version + 1, updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND version = #{expectedVersion}
        """)
    int update(ExperimentUpdateCommand command);
    /** 创建默认 ELN 行。 */
    @Insert("""
        INSERT INTO experiment_record (id, experiment_id, formula_columns, formula_rows, extra_tables, process_text, extra_processes, result_text, created_at, updated_at)
        VALUES (#{id}, #{experimentId}, '[]', '[]', '[]', '', '[]', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """)
    int insertDefaultRecord(@Param("id") UUID id, @Param("experimentId") UUID experimentId);

    /** 新增或覆盖实验电子记录内容。 */
    @Insert("""
        INSERT INTO experiment_record (id, experiment_id, formula_columns, formula_rows, extra_tables, process_text, extra_processes, result_text, created_at, updated_at)
        VALUES (#{id}, #{experimentId}, CAST(#{formulaColumns} AS jsonb), CAST(#{formulaRows} AS jsonb), CAST(#{extraTables} AS jsonb), #{processText}, CAST(#{extraProcesses} AS jsonb), #{resultText}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (experiment_id) DO UPDATE SET formula_columns = EXCLUDED.formula_columns, formula_rows = EXCLUDED.formula_rows,
        extra_tables = EXCLUDED.extra_tables, process_text = EXCLUDED.process_text, extra_processes = EXCLUDED.extra_processes,
        result_text = EXCLUDED.result_text, updated_at = CURRENT_TIMESTAMP
        """)
    int saveRecord(ExperimentRecordCommand command);
    /** 状态顺序迁移。 */
    @Update("""
        UPDATE experiment SET status=#{status}, started_at=CASE WHEN #{status}='in_progress' THEN CURRENT_TIMESTAMP ELSE started_at END,
        completed_at=CASE WHEN #{status}='completed' THEN CURRENT_TIMESTAMP ELSE completed_at END, updated_by_id=#{actorId}, version=version+1, updated_at=CURRENT_TIMESTAMP
        WHERE id=#{experimentId} AND version=#{expectedVersion}
        """)
    int transition(@Param("experimentId") UUID experimentId, @Param("status") String status, @Param("actorId") UUID actorId, @Param("expectedVersion") int expectedVersion);
    /** 实验写入参数。 */
    record ExperimentCommand(UUID id, UUID organizationId, UUID projectId, String experimentNo, String name,
                             String experimentType, String phase, String status, String purpose, UUID ownerId, UUID actorId) { }

    /** 实验基础信息更新参数。 */
    record ExperimentUpdateCommand(UUID id, UUID projectId, String name, String experimentType, String phase, String purpose,
                                   String estimatedStart, String estimatedEnd, UUID ownerId, UUID actorId, int expectedVersion) { }

    /** 实验电子记录写入参数。 */
    record ExperimentRecordCommand(UUID id, UUID experimentId, String formulaColumns, String formulaRows, String extraTables,
                                   String processText, String extraProcesses, String resultText) { }

    /** 实验记录数据库行。 */
    record ExperimentRecordRow(String formulaColumns, String formulaRows, String extraTables, String processText,
                              String extraProcesses, String resultText) { }
}
