package com.materialslab.api.experiments.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.common.storage.ObjectStorageService;
import com.materialslab.api.experiments.domain.ExperimentAttachment;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.experiments.domain.Experiment;
import com.materialslab.api.experiments.domain.ExperimentRecordResponse;
import com.materialslab.api.experiments.domain.ExperimentResponse;
import com.materialslab.api.experiments.mapper.ExperimentMapper;
import com.materialslab.api.experiments.mapper.ExperimentMapper.ExperimentCommand;
import com.materialslab.api.projects.service.ProjectDocumentFilePolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 实验计划、ELN 初始化和状态迁移应用服务。 */
@Service
public class ExperimentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> RECORD_FIELDS = Set.of(
            "formula_columns", "formula_rows", "extra_tables", "process_text", "extra_processes", "result_text");
    private final ExperimentMapper experimentMapper;
    private final ObjectMapper objectMapper;
    private final AccessControlService accessControlService;
    private final ObjectStorageService objectStorageService;
    public ExperimentService(ExperimentMapper experimentMapper, ObjectMapper objectMapper, AccessControlService accessControlService,
                             ObjectStorageService objectStorageService) {
        this.experimentMapper = experimentMapper;
        this.objectMapper = objectMapper;
        this.accessControlService = accessControlService;
        this.objectStorageService = objectStorageService;
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
                participants.stream().map(item -> item.displayName()).toList(), record(record, experiment), canEdit(principal, experiment), experiment.version(),
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
        requireProjectManageAccess(principal, projectId);
        UUID ownerId = uuidOr(payload, "owner_id", principal.userId());
        requireOrganizationUser(principal.organizationId(), ownerId);
        OffsetDateTime estimatedStart = timestamp(payload, "estimated_start", null);
        OffsetDateTime estimatedEnd = timestamp(payload, "estimated_end", null);
        validateDateRange(estimatedStart, estimatedEnd);
        String number = nextNumber();
        ExperimentCommand command = new ExperimentCommand(UUID.randomUUID(), principal.organizationId(), projectId, number,
                name, payload.path("experiment_type").asText("research"), payload.path("phase").asText("方案设计"),
                "not_started", payload.path("purpose").asText(""), estimatedStart, estimatedEnd, ownerId, principal.userId());
        experimentMapper.insert(command);
        saveRecord(command.id(), payload);
        replaceParticipants(principal, command.id(), participantIds(payload));
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
        OffsetDateTime estimatedStart = timestamp(payload, "estimated_start", existing.estimatedStart());
        OffsetDateTime estimatedEnd = timestamp(payload, "estimated_end", existing.estimatedEnd());
        validateDateRange(estimatedStart, estimatedEnd);
        requireOrganizationUser(principal.organizationId(), ownerId);
        var command = new ExperimentMapper.ExperimentUpdateCommand(existing.id(), projectId, name, experimentType, phase, purpose,
                estimatedStart, estimatedEnd, ownerId, principal.userId(), version);
        if (experimentMapper.update(command) == 0) throw new BusinessException(HttpStatus.PRECONDITION_FAILED, "version_conflict", "实验已被其他用户更新，请刷新后重试");
        updateRecord(existing.id(), payload);
        if (payload.hasNonNull("participant_ids")) {
            replaceParticipants(principal, existing.id(), participantIds(payload));
        }
        return get(principal, experimentNo);
    }

    /** 复制实验计划与 ELN 过程内容；结果、附件和状态不继承。 */
    @Transactional
    public Experiment copy(UserPrincipal principal, String experimentNo) {
        accessControlService.requirePermission(principal, "experiment.create");
        Experiment source = get(principal, experimentNo);
        requireProjectManageAccess(principal, source.projectId());
        requireOrganizationUser(principal.organizationId(), source.ownerId());
        String number = nextNumber();
        String copiedName = source.name().length() <= 197 ? source.name() + "-副本" : source.name().substring(0, 197) + "-副本";
        UUID experimentId = UUID.randomUUID();
        experimentMapper.insert(new ExperimentCommand(experimentId, principal.organizationId(), source.projectId(), number,
                copiedName, source.experimentType(), source.phase(), "not_started", source.purpose(), source.estimatedStart(),
                source.estimatedEnd(), source.ownerId(), principal.userId()));
        ExperimentMapper.ExperimentRecordRow record = experimentMapper.findRecord(source.id());
        experimentMapper.saveRecord(new ExperimentMapper.ExperimentRecordCommand(UUID.randomUUID(), experimentId,
                record == null ? "[]" : record.formulaColumns(), record == null ? "[]" : record.formulaRows(),
                record == null ? "[]" : record.extraTables(), record == null ? "" : record.processText(),
                record == null ? "[]" : record.extraProcesses(), ""));
        replaceParticipants(principal, experimentId,
                experimentMapper.listParticipants(source.id()).stream().map(item -> item.userId()).toList());
        return get(principal, number);
    }

    /** 上传实验附件正文到 RustFS，数据库仅保留元数据、授权关系与对象标识。 */
    @Transactional
    public Experiment uploadAttachment(UserPrincipal principal, String experimentNo, MultipartFile file, String kind) {
        Experiment experiment = writableExperiment(principal, experimentNo);
        String normalizedKind = attachmentKind(kind);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "请选择非空附件");
        }
        long maximumSize = "process_image".equals(normalizedKind) ? 10L * 1024 * 1024 : 300L * 1024 * 1024;
        if (file.getSize() > maximumSize) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error",
                    "process_image".equals(normalizedKind) ? "过程图片不能超过 10 MB" : "结果附件不能超过 300 MB");
        }
        String name = safeFileName(file.getOriginalFilename());
        UUID attachmentId = UUID.randomUUID();
        String key = "organizations/" + principal.organizationId() + "/experiments/" + experiment.id()
                + "/attachments/" + normalizedKind + "/" + attachmentId;
        String mimeType;
        String storageReference;
        if ("process_image".equals(normalizedKind)) {
            byte[] content;
            try {
                content = file.getBytes();
            } catch (IOException error) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "attachment_read_failed", "读取上传附件失败");
            }
            mimeType = ProjectDocumentFilePolicy.validateExperimentAttachment(normalizedKind, name, content);
            storageReference = objectStorageService.put(key, content, mimeType);
        } else {
            // 结果附件始终强制为下载，类型不参与浏览器内联解析，也不信任客户端 MIME。
            mimeType = "application/octet-stream";
            try (InputStream content = file.getInputStream()) {
                storageReference = objectStorageService.put(key, content, file.getSize(), mimeType);
            } catch (IOException error) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "attachment_read_failed", "读取上传附件失败");
            }
        }
        try {
            experimentMapper.insertAttachment(new ExperimentMapper.ExperimentAttachmentCommand(attachmentId,
                    principal.organizationId(), experiment.id(), normalizedKind, name, storageReference, mimeType, file.getSize(), principal.userId()));
            experimentMapper.touchExperiment(experiment.id(), principal.userId());
            return get(principal, experimentNo);
        } catch (RuntimeException error) {
            objectStorageService.deleteBestEffort(storageReference);
            throw error;
        }
    }

    /** 删除当前实验内的指定附件。 */
    @Transactional
    public Experiment deleteAttachment(UserPrincipal principal, String experimentNo, UUID attachmentId) {
        Experiment experiment = writableExperiment(principal, experimentNo);
        var attachment = experimentMapper.findAttachmentContent(experiment.id(), attachmentId);
        if (attachment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "attachment_not_found", "实验附件不存在");
        }
        if (experimentMapper.deleteAttachment(experiment.id(), attachmentId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "attachment_not_found", "实验附件不存在");
        }
        experimentMapper.touchExperiment(experiment.id(), principal.userId());
        if (objectStorageService.isObjectReference(attachment.file())) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    objectStorageService.deleteBestEffort(attachment.file());
                }
            });
        }
        return get(principal, experimentNo);
    }

    /** 打开当前用户可见实验的附件正文流，调用方负责在响应结束后关闭。 */
    public AttachmentStream attachmentContent(UserPrincipal principal, String experimentNo, UUID attachmentId) {
        Experiment experiment = get(principal, experimentNo);
        ExperimentMapper.ExperimentAttachmentContentRow row = experimentMapper.findAttachmentContent(experiment.id(), attachmentId);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "attachment_not_found", "实验附件不存在");
        }
        InputStream content = objectStorageService.isObjectReference(row.file())
                ? objectStorageService.open(row.file())
                : legacyAttachmentContent(attachmentId);
        if (content == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "attachment_not_found", "实验附件不存在");
        }
        return new AttachmentStream(row.name(), row.kind(), row.mimeType(), row.fileSize(), content);
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
        if (!experimentMapper.isOrganizationProject(principal.organizationId(), projectId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "project_not_found", "项目不存在或不属于当前组织");
        }
        if (!principal.isSuperAdmin() && !experimentMapper.hasProjectManageAccess(
                principal.organizationId(), projectId, principal.userId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "当前用户无权在该项目下创建实验");
        }
    }

    private Experiment writableExperiment(UserPrincipal principal, String experimentNo) {
        accessControlService.requirePermission(principal, "experiment.update");
        Experiment experiment = get(principal, experimentNo);
        requireExperimentWriteAccess(principal, experiment);
        return experiment;
    }

    private void requireExperimentWriteAccess(UserPrincipal principal, Experiment experiment) {
        if (!canEdit(principal, experiment)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "permission_denied", "当前用户无权编辑该实验");
        }
    }

    private boolean canEdit(UserPrincipal principal, Experiment experiment) {
        if ("completed".equals(experiment.status())) return false;
        if (!accessControlService.hasPermission(principal, "experiment.update")) return false;
        return principal.isSuperAdmin()
                || (accessControlService.hasPermission(principal, "experiment.create")
                    ? experimentMapper.hasWriteAccess(principal.organizationId(), experiment.id(), principal.userId())
                    : experimentMapper.hasDirectWriteAccess(principal.organizationId(), experiment.id(), principal.userId()));
    }

    private void requireOrganizationUser(UUID organizationId, UUID userId) {
        if (!experimentMapper.isActiveOrganizationUser(organizationId, userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "实验负责人或参与人必须是当前组织的有效用户");
        }
    }
    private String required(JsonNode payload, String key) { String value=payload.path(key).asText(); if (value.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key+" 不能为空"); return value; }
    private UUID uuid(JsonNode payload, String key) {
        UUID value = uuidOr(payload, key, null);
        if (value == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key + " 不能为空");
        return value;
    }
    private UUID uuidOr(JsonNode payload, String key, UUID fallback) { try { return payload.hasNonNull(key) ? UUID.fromString(payload.get(key).asText()) : fallback; } catch (IllegalArgumentException error) { throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key+" 必须为 UUID"); } }

    private ExperimentRecordResponse record(ExperimentMapper.ExperimentRecordRow row, Experiment experiment) {
        return new ExperimentRecordResponse(
                array(row == null ? null : row.formulaColumns()), array(row == null ? null : row.formulaRows()),
                array(row == null ? null : row.extraTables()), row == null ? "" : row.processText(),
                array(row == null ? null : row.extraProcesses()), attachmentResponses(experiment, "process_image"),
                row == null ? "" : row.resultText(), attachmentResponses(experiment, "result_file"));
    }

    /** 仅返回受会话保护的内容路由，绝不向前端泄露私有 RustFS 对象标识。 */
    private List<ExperimentAttachment> attachmentResponses(Experiment experiment, String kind) {
        return experimentMapper.listAttachments(experiment.id(), kind).stream()
                .map(item -> new ExperimentAttachment(item.id(), item.name(),
                        "/api/v1/experiments/" + experiment.experimentNo() + "/attachments/" + item.id() + "/content", item.size()))
                .toList();
    }

    private InputStream legacyAttachmentContent(UUID attachmentId) {
        byte[] content = experimentMapper.findLegacyAttachmentContent(attachmentId);
        return content == null ? null : new ByteArrayInputStream(content);
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

    private void updateRecord(UUID experimentId, JsonNode payload) {
        if (RECORD_FIELDS.stream().noneMatch(payload::has)) return;
        ExperimentMapper.ExperimentRecordRow existing = experimentMapper.findRecord(experimentId);
        experimentMapper.saveRecord(new ExperimentMapper.ExperimentRecordCommand(UUID.randomUUID(), experimentId,
                json(payload, "formula_columns", existing == null ? "[]" : existing.formulaColumns()),
                json(payload, "formula_rows", existing == null ? "[]" : existing.formulaRows()),
                json(payload, "extra_tables", existing == null ? "[]" : existing.extraTables()),
                textOr(payload, "process_text", existing == null ? "" : existing.processText()),
                json(payload, "extra_processes", existing == null ? "[]" : existing.extraProcesses()),
                textOr(payload, "result_text", existing == null ? "" : existing.resultText())));
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

    private OffsetDateTime timestamp(JsonNode payload, String key, OffsetDateTime fallback) {
        if (!payload.has(key)) return fallback;
        if (payload.get(key) == null || payload.get(key).isNull() || payload.path(key).asText().isBlank()) return null;
        String value = payload.path(key).asText();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).atZone(BUSINESS_ZONE).toOffsetDateTime();
            } catch (DateTimeParseException error) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error",
                        key + " 必须为 ISO-8601 日期时间，未带时区时按 Asia/Shanghai 解释");
            }
        }
    }

    private void validateDateRange(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "estimated_end 不能早于 estimated_start");
        }
    }

    private List<UUID> participantIds(JsonNode payload) {
        JsonNode participants = payload.get("participant_ids");
        if (participants == null) return List.of();
        if (!participants.isArray()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "participant_ids 必须为 UUID 数组");
        }
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (JsonNode participant : participants) {
            try {
                result.add(UUID.fromString(participant.asText()));
            } catch (IllegalArgumentException error) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "participant_ids 必须为 UUID 数组");
            }
        }
        return List.copyOf(result);
    }

    private void replaceParticipants(UserPrincipal principal, UUID experimentId, List<UUID> participantIds) {
        for (UUID participantId : participantIds) requireOrganizationUser(principal.organizationId(), participantId);
        experimentMapper.deleteParticipants(principal.organizationId(), experimentId);
        for (UUID participantId : participantIds) {
            experimentMapper.insertParticipant(new ExperimentMapper.ExperimentParticipantCommand(
                    UUID.randomUUID(), principal.organizationId(), experimentId, participantId, principal.userId()));
        }
    }

    private String attachmentKind(String kind) {
        if (!List.of("process_image", "result_file").contains(kind)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "kind 参数无效");
        }
        return kind;
    }

    private String safeFileName(String source) {
        String name = source == null ? "" : source.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "文件名无效");
        }
        return name;
    }

    private String nextNumber() {
        return "EXP-" + OffsetDateTime.now(BUSINESS_ZONE).toLocalDate().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** 实验附件流与响应元数据，正文在 HTTP 响应完成后关闭。 */
    public record AttachmentStream(String name, String kind, String mimeType, long fileSize, InputStream content) { }
}
