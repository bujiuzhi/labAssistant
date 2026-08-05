package com.materialslab.api.experiments.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 实验计划与 ELN 基础数据投影。 */
public record Experiment(UUID id, UUID organizationId, UUID projectId, String projectNo, String projectName, String experimentNo, String name,
                         String experimentType, String phase, String status, String purpose, UUID ownerId,
                         String ownerDisplayName, Integer version, OffsetDateTime estimatedStart, OffsetDateTime estimatedEnd,
                         OffsetDateTime startedAt, OffsetDateTime completedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
