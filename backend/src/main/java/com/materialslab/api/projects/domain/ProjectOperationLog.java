package com.materialslab.api.projects.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** 项目操作日志展示信息。 */
public record ProjectOperationLog(
        UUID id,
        String actionType,
        String description,
        String actorDisplayName,
        Map<String, Object> changes,
        OffsetDateTime createdAt) { }
