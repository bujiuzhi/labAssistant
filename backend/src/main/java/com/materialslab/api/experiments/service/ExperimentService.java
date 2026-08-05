package com.materialslab.api.experiments.service;

import tools.jackson.databind.JsonNode;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.experiments.domain.Experiment;
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
    public ExperimentService(ExperimentMapper experimentMapper) { this.experimentMapper = experimentMapper; }
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
}
