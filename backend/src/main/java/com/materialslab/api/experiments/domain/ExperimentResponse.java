package com.materialslab.api.experiments.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 面向前端实验计划和电子实验记录的完整响应模型。 */
public record ExperimentResponse(
        UUID id,
        String experimentNo,
        String name,
        UUID projectId,
        String projectNo,
        String projectName,
        String experimentType,
        String phase,
        String status,
        String purpose,
        OffsetDateTime estimatedStart,
        OffsetDateTime estimatedEnd,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        UUID ownerId,
        String ownerDisplayName,
        List<UUID> participantIds,
        List<String> participantNames,
        ExperimentRecordResponse record,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) { }
