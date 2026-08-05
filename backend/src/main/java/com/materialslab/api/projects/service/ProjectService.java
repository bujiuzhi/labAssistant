package com.materialslab.api.projects.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.projects.domain.DashboardRows.ActiveProject;
import com.materialslab.api.projects.domain.Project;
import com.materialslab.api.projects.domain.ProjectMilestone;
import com.materialslab.api.projects.domain.ProjectOperationLog;
import com.materialslab.api.projects.domain.ProjectResponse;
import com.materialslab.api.projects.mapper.DashboardMapper;
import com.materialslab.api.projects.mapper.ProjectMapper;
import com.materialslab.api.projects.mapper.ProjectMapper.ProjectWriteCommand;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final DashboardMapper dashboardMapper;
    private final ObjectMapper objectMapper;
    public ProjectService(ProjectMapper projectMapper, DashboardMapper dashboardMapper, ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.dashboardMapper = dashboardMapper;
        this.objectMapper = objectMapper;
    }

    /** 查询当前用户可见项目。 */
    public List<Project> list(UUID organizationId, UUID userId, String status, String search, int page, int pageSize) {
        return projectMapper.findVisible(organizationId, userId, normalizeStatus(status), search, Math.min(Math.max(pageSize, 1), 100), Math.max(page - 1, 0) * Math.min(Math.max(pageSize, 1), 100));
    }

    /** 统计当前用户可见项目总数。 */
    public long count(UUID organizationId, UUID userId, String status, String search) {
        return projectMapper.countVisible(organizationId, userId, normalizeStatus(status), search);
    }

    /** 按项目主键或项目编号获取当前组织项目。 */
    public Project get(UUID organizationId, String projectKey) {
        Project project = projectMapper.findByKey(organizationId, projectKey);
        if (project == null) throw new BusinessException(HttpStatus.NOT_FOUND, "project_not_found", "项目不存在或无权访问");
        return project;
    }

    /** 将数据库项目投影转换为前端所需的完整响应。 */
    public ProjectResponse response(Project project) {
        return new ProjectResponse(
                project.id(), project.organizationId(), project.projectNo(), project.name(), project.projectTypeCode(),
                project.description(), project.currentStage(), project.progressPercent(), project.documentCount(),
                project.experimentCount(), project.dataResourceCount(), readTextArray(project.objectives()),
                readMilestones(project.milestones()), project.status(), project.ownerId(), project.ownerName(),
                projectMapper.listMembers(project.id()), project.plannedStartDate(), project.plannedEndDate(), project.actualEndAt(),
                project.archivedAt(), project.version(), project.createdAt(), project.updatedAt());
    }

    /** 将项目列表转换为前端所需的完整响应。 */
    public List<ProjectResponse> responses(List<Project> projects) {
        return projects.stream().map(this::response).toList();
    }

    /** 查询项目真实操作日志。 */
    public List<ProjectOperationLog> operationLogs(UUID organizationId, String projectKey) {
        Project project = get(organizationId, projectKey);
        return projectMapper.listOperationLogs(organizationId, project.id()).stream()
                .map(row -> new ProjectOperationLog(row.id(), row.actionType(), row.description(), row.actorDisplayName(),
                        readObject(row.changes()), row.createdAt()))
                .toList();
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

    /** 汇总当前组织测试数据库中的项目与实验实时指标。 */
    public Map<String, Object> dashboard(UUID organizationId, UUID userId, UUID selectedProjectId) {
        var projectMetrics = dashboardMapper.projectMetrics(organizationId);
        var experimentMetrics = dashboardMapper.experimentMetrics(organizationId);
        var typeDistribution = dashboardMapper.typeDistribution(organizationId);
        LocalDate startDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(29);
        var trendEntries = dashboardMapper.trendEntries(organizationId, startDate);

        List<String> dates = new ArrayList<>();
        for (int offset = 0; offset < 30; offset++) {
            dates.add(startDate.plusDays(offset).toString());
        }
        LinkedHashSet<String> typeNames = new LinkedHashSet<>();
        typeDistribution.forEach(item -> typeNames.add(item.name()));
        trendEntries.forEach(item -> typeNames.add(item.typeName()));
        List<Map<String, Object>> series = new ArrayList<>();
        for (String typeName : typeNames) {
            List<Long> values = new ArrayList<>();
            for (String date : dates) {
                long count = trendEntries.stream()
                        .filter(item -> item.typeName().equals(typeName) && item.date().toString().equals(date))
                        .mapToLong(item -> item.experimentCount())
                        .sum();
                values.add(count);
            }
            series.add(Map.of("name", typeName, "values", values));
        }

        List<Map<String, Object>> activeProjects = dashboardMapper.activeProjects(organizationId, userId).stream()
                .map(this::toDashboardProject)
                .toList();
        List<Map<String, Object>> projectOptions = dashboardMapper.projectOptions(organizationId).stream()
                .map(item -> Map.<String, Object>of("id", item.id(), "project_no", item.projectNo(), "name", item.name()))
                .toList();

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("dates", dates);
        trend.put("series", series);
        trend.put("selected_project_id", selectedProjectId == null ? "" : selectedProjectId.toString());
        trend.put("project_options", projectOptions);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("project_metrics", metrics(projectMetrics.total(), projectMetrics.active(), projectMetrics.archived(), projectMetrics.atRisk(), 0L, 0L));
        response.put("experiment_metrics", metrics(experimentMetrics.total(), 0L, 0L, 0L, experimentMetrics.inProgress(), experimentMetrics.completed()));
        response.put("type_distribution", typeDistribution);
        response.put("trend", trend);
        response.put("active_projects", activeProjects);
        return response;
    }

    private Map<String, Object> metrics(long total, long active, long archived, long atRisk, long inProgress, long completed) {
        return Map.of("total", total, "active", active, "archived", archived, "at_risk", atRisk,
                "in_progress", inProgress, "completed", completed);
    }

    private Map<String, Object> toDashboardProject(ActiveProject project) {
        List<ProjectMilestone> milestones = readMilestones(project.milestones());
        ProjectMilestone milestone = milestones.stream()
                .filter(item -> "current".equals(item.state()))
                .findFirst()
                .orElse(milestones.isEmpty() ? null : milestones.getFirst());
        LocalDate dueDate = parseDate(milestone == null ? null : milestone.date());
        long riskDays = dueDate == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of("Asia/Shanghai")), dueDate);
        String riskLevel = dueDate == null || riskDays >= 7 ? "normal" : riskDays >= 0 ? "countdown" : "overdue";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", project.id());
        response.put("project_no", project.projectNo());
        response.put("name", project.name());
        response.put("project_type", project.projectTypeCode());
        response.put("owner_name", project.ownerName());
        response.put("objectives", readTextArray(project.objectives()));
        response.put("planned_start_date", project.plannedStartDate());
        response.put("planned_end_date", project.plannedEndDate());
        response.put("milestone", milestone == null ? null : Map.of("name", milestone.name(), "date", milestone.date(), "state", milestone.state()));
        response.put("progress_percent", project.progressPercent());
        response.put("is_followed", project.followed());
        response.put("risk_level", riskLevel);
        response.put("risk_days", dueDate == null ? null : riskDays);
        return response;
    }

    private List<String> readTextArray(String source) {
        List<String> values = new ArrayList<>();
        try {
            for (JsonNode node : objectMapper.readTree(source)) {
                values.add(node.asText());
            }
        } catch (Exception ignored) {
            // 旧数据格式异常时返回空列表，避免影响总览接口。
        }
        return values;
    }

    private Map<String, Object> readObject(String source) {
        try {
            JsonNode node = objectMapper.readTree(source);
            Map<String, Object> values = new LinkedHashMap<>();
            if (node.isObject()) node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
            return values;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<ProjectMilestone> readMilestones(String source) {
        List<ProjectMilestone> values = new ArrayList<>();
        try {
            for (JsonNode node : objectMapper.readTree(source)) {
                values.add(new ProjectMilestone(node.path("date").asText(), node.path("name").asText(), node.path("state").asText()));
            }
        } catch (Exception ignored) {
            // 旧数据格式异常时返回空列表，避免影响总览接口。
        }
        return values;
    }

    private LocalDate parseDate(String source) {
        try {
            return source == null || source.isBlank() ? null : LocalDate.parse(source);
        } catch (Exception ignored) {
            return null;
        }
    }

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
