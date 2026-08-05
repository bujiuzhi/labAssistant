package com.materialslab.api.identity.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 系统管理员管理的组织用户。 */
public record ManagedUser(
        UUID id,
        String username,
        String displayName,
        String email,
        String status,
        boolean active,
        boolean superAdmin,
        List<String> roleCodes,
        List<String> roleNames,
        OffsetDateTime lastLogin,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) { }
