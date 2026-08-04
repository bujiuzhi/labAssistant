package com.materialslab.api.projects.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.projects.domain.Project;
import com.materialslab.api.projects.mapper.ProjectMapper;
import com.materialslab.api.projects.mapper.ProjectMapper.ProjectWriteCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目创建、编辑、归档和关注的应用服务。 */
@Service
public class ProjectService {
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;
    public ProjectService(ProjectMapper projectMapper, ObjectMapper objectMapper) { this.projectMapper = projectMapper; this.objectMapper = objectMapper; }

    /** 查询当前用户可见项目。 */
    public List<Project> list(UUID organizationId, UUID userId, String status, String search, int page, int pageSize) {
        return projectMapper.findVisible(organizationId, userId, normalizeStatus(status), search, Math.min(Math.max(pageSize, 1), 100), Math.max(page - 1, 0) * Math.min(Math.max(pageSize, 1), 100));
    }

    /** 获取当前组织的项目。 */
    public Project get(UUID organizationId, String projectNo) {
        Project project = projectMapper.findByNo(organizationId, projectNo);
        if (project == null) throw new BusinessException(HttpStatus.NOT_FOUND, "project_not_found", "项目不存在或无权访问");
        return project;
    }

    /** 创建带 PRJ 编号的项目。 */
    @Transactional
    public Project create(UUID organizationId, UUID actorId, JsonNode payload) {
        ProjectWriteCommand command = command(null, organizationId, actorId, payload, 0, nextProjectNo());
        projectMapper.insert(command);
        return get(organizationId, command.projectNo());
    }

    /** 使用乐观锁更新项目。 */
    @Transactional
    public Project update(UUID organizationId, UUID actorId, String projectNo, int version, JsonNode payload) {
        Project existing = get(organizationId, projectNo);
        ProjectWriteCommand command = command(existing.id(), organizationId, actorId, payload, version, existing.projectNo());
        if (projectMapper.update(command) == 0) throw new BusinessException(HttpStatus.PRECONDITION_FAILED, "version_conflict", "项目已被其他用户更新，请刷新后重试");
        return get(organizationId, projectNo);
    }

    /** 归档项目。 */
    @Transactional
    public Project archive(UUID organizationId, UUID actorId, String projectNo, int version) {
        Project project = get(organizationId, projectNo);
        if (projectMapper.archive(project.id(), version, actorId) == 0) throw new BusinessException(HttpStatus.PRECONDITION_FAILED, "version_conflict", "项目已被更新或已归档");
        return get(organizationId, projectNo);
    }

    /** 设置项目关注状态。 */
    @Transactional
    public boolean setFollow(UUID organizationId, UUID userId, String projectNo, boolean followed) {
        Project project = get(organizationId, projectNo);
        if (followed) projectMapper.follow(UUID.randomUUID(), project.id(), userId); else projectMapper.unfollow(project.id(), userId);
        return followed;
    }

    /** 汇总工作台核心实时指标。 */
    public Map<String, Object> dashboard(UUID organizationId) { return Map.of("project_count", projectMapper.countByOrganization(organizationId)); }

    private ProjectWriteCommand command(UUID id, UUID organizationId, UUID actorId, JsonNode payload, int expectedVersion, String projectNo) {
        String name = requiredText(payload, "name");
        int progress = payload.path("progress_percent").asInt(0);
        if (progress < 0 || progress > 100) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "项目进度必须在 0 到 100 之间");
        return new ProjectWriteCommand(id == null ? UUID.randomUUID() : id, organizationId, projectNo, name,
                payload.path("project_type_code").asText("research"), payload.path("description").asText(""),
                payload.path("current_stage").asText("方案设计"), progress, json(payload.path("objectives")), json(payload.path("milestones")),
                payload.path("status").asText("not_started"), uuid(payload, "owner_id", actorId), null, null, actorId, expectedVersion);
    }
    private String requiredText(JsonNode payload, String key) { String value = payload.path(key).asText(); if (value.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key + " 不能为空"); return value; }
    private UUID uuid(JsonNode payload, String key, UUID fallback) { try { return payload.hasNonNull(key) ? UUID.fromString(payload.get(key).asText()) : fallback; } catch (IllegalArgumentException error) { throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", key + " 必须为 UUID"); } }
    private String json(JsonNode value) { try { return value.isMissingNode() || value.isNull() ? "[]" : objectMapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalArgumentException("JSON 序列化失败", error); } }
    private String nextProjectNo() { return "PRJ-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
    private String normalizeStatus(String status) { return switch (status == null ? "" : status) { case "running" -> "active"; case "ended" -> "completed"; default -> status; }; }
}
