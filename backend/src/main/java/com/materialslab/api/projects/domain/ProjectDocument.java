package com.materialslab.api.projects.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 项目文档元数据；正文由 RustFS 对象标识关联，旧记录可暂存于数据库内容表。 */
public record ProjectDocument(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String category,
        String name,
        String versionLabel,
        String file,
        String mimeType,
        long fileSize,
        UUID uploadedById,
        String uploadedByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) { }
