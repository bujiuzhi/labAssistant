package com.materialslab.api.experiments.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
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
    private final AccessControlService accessControlService;
    public ExperimentService(ExperimentMapper experimentMapper, ObjectMapper objectMapper, AccessControlService accessControlService) {
        this.experimentMapper = experimentMapper;
        this.objectMapper = objectMapper;
        this.accessControlService = accessControlService;
    }
    /** 查询实验计划。 */
    public List<Experiment> list(UserPrincipal principal, UUID projectId, String status, String search, int page, int pageSize) {
        accessControlService.requirePermission(principal, "experiment.read");
        int limit = Math.min(Math.max(pageSize, 1), 100);
        return experimentMapper.findVisible(principal.organizationId(), principal.userId(),
                accessControlService.canReadAllOrganizationData(principal), projectId, status, search, limit, Math.max(page - 1, 0) * limit);
    }
    /** 统计当前组织内符合筛选条件的实验总数。 */
    public long count(UserPrincipal principal, UUID projectId, String status, String search) {
        accessControlService.requirePermission(principal, "experiment.read");
        return experimentMapper.countVisible(principal.organizationId(), principal.userId(),
                accessControlService.canReadAllOrganizationData(principal), projectId, status, search);
    }
    /** 查询一个实验。 */
    public Experiment get(UserPrincipal principal, String experimentNo) {
        accessControlService.requirePermission(principal, "experiment.read");
        Experiment experiment = experimentMapper.findByNo(principal.organizationId(), principal.userId(),
                accessControlService.canReadAllOrganizationData(principal), experimentNo);
        if (experiment == null) throw new BusinessException(HttpStatus.NOT_FOUND, "experiment_not_found", "实验不存在或无权访问"); return experiment;
    }

    /** 将数据库实验投影转换为前端电子记录响应。 */
    public ExperimentResponse response(UserPrincipal principal, Experiment experiment) {
        var record = experimentMapper.findRecord(experiment.id());
        var participants = experimentMapper.listParticipants(experiment.id());
        return new ExperimentResponse(
                experiment.id(), experiment.experimentNo(), experiment.name(), experiment.projectId(), experiment.projectNo(),
                experiment.projectName(), experiment.experimentType(), experiment.phase(), experiment.status(), experiment.purpose(),
                experiment.estimatedStart(), experiment.estimatedEnd(), experiment.startedAt(), experiment.completedAt(), experiment.ownerId(),
                experiment.ownerDisplayName(), participants.stream().map(item -> item.userId()).toList(),
                participants.stream().map(item -> item.displayName()).toList(), record(record, experiment.id()), canEdit(principal, experiment), experiment.version(),
                experiment.createdAt(), experiment.updatedAt());
    }

    /** 将实验列表转换为前端电子记录响应。 */
    public List<ExperimentResponse> responses(UserPrincipal principal, List<Experiment> experiments) {
        return experiments.stream().map(experiment -> response(principal, experiment)).toList();
    }
    /** 创建实验与空 ELN。 */
    @Transactional
    public Experiment create(UserPrincipal principal, JsonNode payload) {
        accessControlService.requirePermission(principal, "experiment.create");
        UUID projectId = uuid(payload, "project_id"); String name = required(payload, "name");
        String number = "EXP-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        requireProjectManageAccess(principal, projectId);
        UUID ownerId = uuidOr(payload, "owner_id", principal.userId());
        requireOrganizationUser(principal.organizationId(), ownerId);
        ExperimentCommand command = new ExperimentCommand(UUID.randomUUID(), principal.organizationId(), projectId, number, name, payload.path("experiment_type").asText("research"), payload.path("phase").asText("方案设计"), "not_started", payload.path("purpose").asText(""), ownerId, principal.userId());
        experimentMapper.insert(command);
        saveRecord(command.id(), payload);
        return get(principal, number);
    }

    /** 更新实验计划和电子记录内容。 */
    @Transactional
    public Experiment update(UserPrincipal principal, String experimentNo, int version, JsonNode payload) {
        accessControlService.requirePermission(principal, "experiment.update");
        Experiment existing = get(principal, experimentNo);
        requireExperimentWriteAccess(principal, existing);
        UUID projectId = uuidOr(payload, "project_id", existing.projectId());
        UUID ownerId = uuidOr(payload, "owner_id", existing.ownerId());
        if (!accessControlService.hasPermission(principal, "experiment.create")
                && (!projectId.equals(existing.projectId()) || !ownerId.equals(existing.ownerId()))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "实验员不可变更实验归属项目或负责人");
        }
        if (!projectId.equals(existing.projectId())) {
            requireProjectManageAccess(principal, projectId);
        }
        String name = textOr(payload, "name", existing.name());
        String experimentType = textOr(payload, "experiment_type", existing.experimentType());
        String phase = textOr(payload, "phase", existing.phase());
        String purpose = textOr(payload, "purpose", existing.purpose());
        requireOrganizationUser(principal.organizationId(), ownerId);
        var command = new ExperimentMapper.ExperimentUpdateCommand(existing.id(), projectId, name, experimentType, phase, purpose,
                nullableText(payload, "estimated_start"), nullableText(payload, "estimated_end"), ownerId, principal.userId(), version);
        if (experimentMapper.update(command) == 0) throw new BusinessException(HttpStatus.PRECONDITION_FAILED, "version_conflict", "实验已被其他用户更新，请刷新后重试");
        saveRecord(existing.id(), payload);
        return get(principal, experimentNo);
    }
    /** 执行不可跳级的实验状态迁移。 */
    @Transactional
    public Experiment transition(UserPrincipal principal, String experimentNo, int version, String targetStatus) {
        accessControlService.requirePermission(principal, "experiment.transition");
        Experiment existing = get(principal, experimentNo);
        requireExperimentWriteAccess(principal, existing);
        boolean valid = ("not_started".equals(existing.status()) && "in_progress".equals(targetStatus)) || ("in_progress".equals(existing.status()) && "completed".equals(targetStatus));
        if (!valid) throw new BusinessException(HttpStatus.CONFLICT, "invalid_transition", "实验状态只能按未开始、进行中、已完成顺序迁移");
        if (experimentMapper.transition(existing.id(), targetStatus, principal.userId(), version) == 0) throw new BusinessException(HttpStatus.PRECONDITION_FAILED, "version_conflict", "实验已被其他用户更新，请刷新后重试");
        return get(principal, experimentNo);
    }

    private void requireProjectManageAccess(UserPrincipal principal, UUID projectId) {
        if (!principal.isSuperAdmin()
                && !experimentMapper.hasProjectManageAccess(principal.organizationId(), projectId, principal.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "当前用户无权在该项目下创建实验");
        }
    }

    private void requireExperimentWriteAccess(UserPrincipal principal, Experiment experiment) {
        if (!canEdit(principal, experiment)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "当前用户无权编辑该实验");
        }
    }

    private boolean canEdit(UserPrincipal principal, Experiment experiment) {
        if (!accessControlService.hasPermission(principal, "experiment.update")) return false;
        return principal.isSuperAdmin()
                || (accessControlService.hasPermission(principal, "experiment.create")
                    ? experimentMapper.hasWriteAccess(principal.organizationId(), experiment.id(), principal.userId())
                    : experimentMapper.hasDirectWriteAccess(principal.organizationId(), experiment.id(), principal.userId()));
    }

    private void requireOrganizationUser(UUID organizationId, UUID userId) {
        if (!experimentMapper.isActiveOrganizationUser(organizationId, userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "实验负责人必须是当前组织的有效用户");
        }
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

    private void saveRecord(UUID experimentId, JsonNode payload) {
        experimentMapper.saveRecord(new ExperimentMapper.ExperimentRecordCommand(UUID.randomUUID(), experimentId,
                json(payload, "formula_columns", "[]"), json(payload, "formula_rows", "[]"), json(payload, "extra_tables", "[]"),
                textOr(payload, "process_text", ""), json(payload, "extra_processes", "[]"), textOr(payload, "result_text", "")));
    }

    private String json(JsonNode payload, String key, String fallback) {
        try {
            JsonNode value = payload.get(key);
            return value == null || value.isNull() ? fallback : objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key + " 必须是有效 JSON");
        }
    }

    private String textOr(JsonNode payload, String key, String fallback) {
        return payload.hasNonNull(key) ? payload.path(key).asText() : fallback;
    }

    private String nullableText(JsonNode payload, String key) {
        return payload.hasNonNull(key) && !payload.path(key).asText().isBlank() ? payload.path(key).asText() : null;
    }
}
