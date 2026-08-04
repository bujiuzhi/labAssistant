package com.materialslab.api.projects.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 项目列表和详情的持久化投影。 */
public record Project(
        UUID id, UUID organizationId, String projectNo, String name, String projectTypeCode,
        String description, String currentStage, Integer progressPercent, String status,
        UUID ownerId, String ownerName, String objectives, String milestones,
        OffsetDateTime plannedStartDate, OffsetDateTime plannedEndDate, Integer version,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
