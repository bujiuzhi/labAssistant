package com.materialslab.api.projects.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 面向前端项目列表和详情的完整响应模型。 */
public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String projectNo,
        String name,
        String projectTypeCode,
        String description,
        String currentStage,
        int progressPercent,
        int documentCount,
        int experimentCount,
        int dataResourceCount,
        List<String> objectives,
        List<ProjectMilestone> milestones,
        String status,
        UUID ownerId,
        String ownerDisplayName,
        List<ProjectMember> members,
        OffsetDateTime plannedStartDate,
        OffsetDateTime plannedEndDate,
        OffsetDateTime actualEndAt,
        OffsetDateTime archivedAt,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) { }
