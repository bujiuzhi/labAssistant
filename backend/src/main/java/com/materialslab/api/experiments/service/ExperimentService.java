package com.materialslab.api.experiments.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.experiments.domain.Experiment;
import com.materialslab.api.experiments.domain.ExperimentRecordResponse;
import com.materialslab.api.experiments.domain.ExperimentResponse;
import com.materialslab.api.experiments.mapper.ExperimentMapper;
import com.materialslab.api.experiments.mapper.ExperimentMapper.ExperimentCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 实验计划、ELN 初始化和状态迁移应用服务。 */
@Service
public class ExperimentService {
    private final ExperimentMapper experimentMapper;
    private final ObjectMapper objectMapper;
    public ExperimentService(ExperimentMapper experimentMapper, ObjectMapper objectMapper) {
        this.experimentMapper = experimentMapper;
        this.objectMapper = objectMapper;
    }
    /** 查询实验计划。 */
    public List<Experiment> list(UUID organizationId, UUID projectId, String status, String search, int page, int pageSize) {
        int limit = Math.min(Math.max(pageSize, 1), 100); return experimentMapper.findVisible(organizationId, projectId, status, search, limit, Math.max(page - 1, 0) * limit);
    }
    /** 统计当前组织内符合筛选条件的实验总数。 */
    public long count(UUID organizationId, UUID projectId, String status, String search) {
        return experimentMapper.countVisible(organizationId, projectId, status, search);
    }
    /** 查询一个实验。 */
    public Experiment get(UUID organizationId, String experimentNo) {
        Experiment experiment = experimentMapper.findByNo(organizationId, experimentNo);
        if (experiment == null) throw new BusinessException(HttpStatus.NOT_FOUND, "experiment_not_found", "实验不存在或无权访问"); return experiment;
    }

    /** 将数据库实验投影转换为前端电子记录响应。 */
    public ExperimentResponse response(Experiment experiment) {
        var record = experimentMapper.findRecord(experiment.id());
        var participants = experimentMapper.listParticipants(experiment.id());
        return new ExperimentResponse(
                experiment.id(), experiment.experimentNo(), experiment.name(), experiment.projectId(), experiment.projectNo(),
                experiment.projectName(), experiment.experimentType(), experiment.phase(), experiment.status(), experiment.purpose(),
                experiment.estimatedStart(), experiment.estimatedEnd(), experiment.startedAt(), experiment.completedAt(), experiment.ownerId(),
                experiment.ownerDisplayName(), participants.stream().map(item -> item.userId()).toList(),
                participants.stream().map(item -> item.displayName()).toList(), record(record, experiment.id()), experiment.version(),
                experiment.createdAt(), experiment.updatedAt());
    }

    /** 将实验列表转换为前端电子记录响应。 */
    public List<ExperimentResponse> responses(List<Experiment> experiments) {
        return experiments.stream().map(this::response).toList();
    }
    /** 创建实验与空 ELN。 */
    @Transactional
    public Experiment create(UUID organizationId, UUID actorId, JsonNode payload) {
        UUID projectId = uuid(payload, "project_id"); String name = required(payload, "name");
        String number = "EXP-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ExperimentCommand command = new ExperimentCommand(UUID.randomUUID(), organizationId, projectId, number, name, payload.path("experiment_type").asText("research"), payload.path("phase").asText("方案设计"), "not_started", payload.path("purpose").asText(""), uuidOr(payload, "owner_id", actorId), actorId);
        experimentMapper.insert(command); experimentMapper.insertDefaultRecord(UUID.randomUUID(), command.id()); return get(organizationId, number);
    }
    /** 执行不可跳级的实验状态迁移。 */
    @Transactional
    public Experiment transition(UUID organizationId, UUID actorId, String experimentNo, int version, String targetStatus) {
        Experiment existing = get(organizationId, experimentNo);
        boolean valid = ("not_started".equals(existing.status()) && "in_progress".equals(targetStatus)) || ("in_progress".equals(existing.status()) && "completed".equals(targetStatus));
        if (!valid) throw new BusinessException(HttpStatus.CONFLICT, "invalid_transition", "实验状态只能按未开始、进行中、已完成顺序迁移");
        if (experimentMapper.transition(existing.id(), targetStatus, actorId, version) == 0) throw new BusinessException(HttpStatus.PRECONDITION_FAILED, "version_conflict", "实验已被其他用户更新，请刷新后重试");
        return get(organizationId, experimentNo);
    }
    private String required(JsonNode payload, String key) { String value=payload.path(key).asText(); if (value.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key+" 不能为空"); return value; }
    private UUID uuid(JsonNode payload, String key) { return uuidOr(payload, key, null); }
    private UUID uuidOr(JsonNode payload, String key, UUID fallback) { try { return payload.hasNonNull(key) ? UUID.fromString(payload.get(key).asText()) : fallback; } catch (IllegalArgumentException error) { throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key+" 必须为 UUID"); } }

    private ExperimentRecordResponse record(ExperimentMapper.ExperimentRecordRow row, UUID experimentId) {
        return new ExperimentRecordResponse(
                array(row == null ? null : row.formulaColumns()), array(row == null ? null : row.formulaRows()),
                array(row == null ? null : row.extraTables()), row == null ? "" : row.processText(),
                array(row == null ? null : row.extraProcesses()), experimentMapper.listAttachments(experimentId, "process_image"),
                row == null ? "" : row.resultText(), experimentMapper.listAttachments(experimentId, "result_file"));
    }

    private JsonNode array(String source) {
        try {
            return objectMapper.readTree(source == null || source.isBlank() ? "[]" : source);
        } catch (Exception error) {
            return objectMapper.createArrayNode();
        }
    }
}
