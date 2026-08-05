package com.materialslab.api.experiments.mapper;

import com.materialslab.api.experiments.domain.Experiment;
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
    List<Experiment> findVisible(@Param("organizationId") UUID organizationId, @Param("projectId") UUID projectId,
                                 @Param("status") String status, @Param("search") String search,
                                 @Param("limit") int limit, @Param("offset") int offset);
    /** 统计当前组织内符合条件的实验总数。 */
    @SelectProvider(type = ExperimentSqlProvider.class, method = "countVisible")
    long countVisible(@Param("organizationId") UUID organizationId, @Param("projectId") UUID projectId,
                      @Param("status") String status, @Param("search") String search);
    /** 按业务编号查询实验。 */
    @Select("""
        SELECT e.id, e.organization_id, e.project_id, e.experiment_no, e.name, e.experiment_type, e.phase, e.status,
        e.purpose, e.owner_id, owner.display_name owner_name, e.version, e.estimated_start, e.estimated_end,
        e.started_at, e.completed_at, e.created_at, e.updated_at FROM experiment e JOIN user_account owner ON owner.id=e.owner_id
        WHERE e.organization_id=#{organizationId} AND e.experiment_no=#{experimentNo}
        """)
    Experiment findByNo(@Param("organizationId") UUID organizationId, @Param("experimentNo") String experimentNo);
    /** 新建实验和默认 ELN。 */
    @Insert("""
        INSERT INTO experiment (id, organization_id, project_id, experiment_no, name, experiment_type, phase, status, purpose, owner_id, version, created_by_id, updated_by_id, created_at, updated_at)
        VALUES (#{id}, #{organizationId}, #{projectId}, #{experimentNo}, #{name}, #{experimentType}, #{phase}, #{status}, #{purpose}, #{ownerId}, 1, #{actorId}, #{actorId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """)
    int insert(ExperimentCommand command);
    /** 创建默认 ELN 行。 */
    @Insert("""
        INSERT INTO experiment_record (id, experiment_id, formula_columns, formula_rows, extra_tables, process_text, extra_processes, result_text, created_at, updated_at)
        VALUES (#{id}, #{experimentId}, '[]', '[]', '[]', '', '[]', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """)
    int insertDefaultRecord(@Param("id") UUID id, @Param("experimentId") UUID experimentId);
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
}
