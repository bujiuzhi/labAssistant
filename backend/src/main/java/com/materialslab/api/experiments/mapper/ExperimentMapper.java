package com.materialslab.api.experiments.mapper;

import com.materialslab.api.experiments.domain.Experiment;
import com.materialslab.api.experiments.domain.ExperimentAttachment;
import com.materialslab.api.experiments.domain.ExperimentParticipant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
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
            OR e.owner_id = #{userId}
            OR EXISTS (SELECT 1 FROM experiment_participant participant
                       WHERE participant.experiment_id = e.id AND participant.user_id = #{userId})
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

    /** 判断项目是否属于当前组织。 */
    @Select("SELECT EXISTS(SELECT 1 FROM project WHERE id = #{projectId} AND organization_id = #{organizationId})")
    boolean isOrganizationProject(@Param("organizationId") UUID organizationId, @Param("projectId") UUID projectId);

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
            WHERE participant.experiment_id = #{experimentId} AND account.status <> 'deleted'
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

    /** 查询实验附件正文读取所需元数据。 */
    @Select("""
            SELECT attachment.id, attachment.name, attachment.kind, attachment.mime_type, attachment.file_size, attachment.file
            FROM experiment_attachment attachment
            WHERE attachment.experiment_id = #{experimentId} AND attachment.id = #{attachmentId}
            """)
    ExperimentAttachmentContentRow findAttachmentContent(@Param("experimentId") UUID experimentId,
                                                         @Param("attachmentId") UUID attachmentId);
    /** 新建实验和默认 ELN。 */
    @Insert("""
        INSERT INTO experiment (id, organization_id, project_id, experiment_no, name, experiment_type, phase, status, purpose, estimated_start, estimated_end, owner_id, version, created_by_id, updated_by_id, created_at, updated_at)
        VALUES (#{id}, #{organizationId}, #{projectId}, #{experimentNo}, #{name}, #{experimentType}, #{phase}, #{status}, #{purpose}, #{estimatedStart}, #{estimatedEnd}, #{ownerId}, 1, #{actorId}, #{actorId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """)
    int insert(ExperimentCommand command);

    /** 更新实验基础信息并进行乐观锁校验。 */
    @Update("""
        UPDATE experiment SET project_id = #{projectId}, name = #{name}, experiment_type = #{experimentType}, phase = #{phase},
        purpose = #{purpose}, estimated_start = #{estimatedStart}, estimated_end = #{estimatedEnd},
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

    /** 删除实验现有参与人，以便在同一事务内整体替换。 */
    @Delete("DELETE FROM experiment_participant WHERE experiment_id = #{experimentId} AND organization_id = #{organizationId}")
    int deleteParticipants(@Param("organizationId") UUID organizationId, @Param("experimentId") UUID experimentId);

    /** 新增实验参与人。 */
    @Insert("""
            INSERT INTO experiment_participant (id, organization_id, experiment_id, user_id, participant_role, joined_at, created_by_id)
            VALUES (#{id}, #{organizationId}, #{experimentId}, #{userId}, 'participant', CURRENT_TIMESTAMP, #{actorId})
            """)
    int insertParticipant(ExperimentParticipantCommand command);

    /** 保存附件元数据。 */
    @Insert("""
            INSERT INTO experiment_attachment (id, organization_id, experiment_id, kind, name, file, mime_type, file_size, uploaded_by_id, created_at, updated_at)
            VALUES (#{id}, #{organizationId}, #{experimentId}, #{kind}, #{name}, #{file}, #{mimeType}, #{fileSize}, #{actorId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertAttachment(ExperimentAttachmentCommand command);

    /** 读取旧版本保存在数据库的附件正文。 */
    @Select("SELECT content FROM experiment_attachment_content WHERE attachment_id = #{attachmentId}")
    byte[] findLegacyAttachmentContent(@Param("attachmentId") UUID attachmentId);

    /** 保存附件二进制正文，仅用于历史兼容迁移。 */
    @Insert("INSERT INTO experiment_attachment_content (attachment_id, content) VALUES (#{attachmentId}, #{content})")
    int insertAttachmentContent(@Param("attachmentId") UUID attachmentId, @Param("content") byte[] content);

    /** 删除指定实验的附件；附件正文通过外键级联删除。 */
    @Delete("DELETE FROM experiment_attachment WHERE id = #{attachmentId} AND experiment_id = #{experimentId}")
    int deleteAttachment(@Param("experimentId") UUID experimentId, @Param("attachmentId") UUID attachmentId);

    /** 附件变化后更新实验版本，使 ETag 能反映完整响应变化。 */
    @Update("""
            UPDATE experiment SET version = version + 1, updated_by_id = #{actorId}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{experimentId}
            """)
    int touchExperiment(@Param("experimentId") UUID experimentId, @Param("actorId") UUID actorId);
    /** 状态顺序迁移。 */
    @Update("""
        UPDATE experiment SET status=#{status}, started_at=CASE WHEN #{status}='in_progress' THEN CURRENT_TIMESTAMP ELSE started_at END,
        completed_at=CASE WHEN #{status}='completed' THEN CURRENT_TIMESTAMP ELSE completed_at END, updated_by_id=#{actorId}, version=version+1, updated_at=CURRENT_TIMESTAMP
        WHERE id=#{experimentId} AND version=#{expectedVersion}
        """)
    int transition(@Param("experimentId") UUID experimentId, @Param("status") String status, @Param("actorId") UUID actorId, @Param("expectedVersion") int expectedVersion);
    /** 实验写入参数。 */
    record ExperimentCommand(UUID id, UUID organizationId, UUID projectId, String experimentNo, String name,
                             String experimentType, String phase, String status, String purpose,
                             java.time.OffsetDateTime estimatedStart, java.time.OffsetDateTime estimatedEnd,
                             UUID ownerId, UUID actorId) { }

    /** 实验基础信息更新参数。 */
    record ExperimentUpdateCommand(UUID id, UUID projectId, String name, String experimentType, String phase, String purpose,
                                   java.time.OffsetDateTime estimatedStart, java.time.OffsetDateTime estimatedEnd,
                                   UUID ownerId, UUID actorId, int expectedVersion) { }

    /** 实验参与人写入参数。 */
    record ExperimentParticipantCommand(UUID id, UUID organizationId, UUID experimentId, UUID userId, UUID actorId) { }

    /** 实验附件元数据写入参数。 */
    record ExperimentAttachmentCommand(UUID id, UUID organizationId, UUID experimentId, String kind, String name,
                                       String file, String mimeType, long fileSize, UUID actorId) { }

    /** 实验电子记录写入参数。 */
    record ExperimentRecordCommand(UUID id, UUID experimentId, String formulaColumns, String formulaRows, String extraTables,
                                   String processText, String extraProcesses, String resultText) { }

    /** 实验记录数据库行。 */
    record ExperimentRecordRow(String formulaColumns, String formulaRows, String extraTables, String processText,
                              String extraProcesses, String resultText) { }

    /** 实验附件正文数据库行。 */
    record ExperimentAttachmentContentRow(UUID id, String name, String kind, String mimeType, long fileSize, String file) { }
}
