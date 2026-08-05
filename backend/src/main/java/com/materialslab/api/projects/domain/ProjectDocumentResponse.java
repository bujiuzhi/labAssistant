package com.materialslab.api.projects.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 面向项目文档页的文档响应。 */
public record ProjectDocumentResponse(
        UUID id,
        String name,
        String extension,
        String mimeType,
        long fileSize,
        String category,
        String categoryLabel,
        String versionLabel,
        UUID uploadedById,
        String uploadedByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) { }
