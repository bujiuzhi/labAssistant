package com.materialslab.api.projects.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 项目文档元数据，文件正文单独保存在数据库内容表。 */
public record ProjectDocument(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String category,
        String name,
        String versionLabel,
        String mimeType,
        long fileSize,
        UUID uploadedById,
        String uploadedByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) { }
