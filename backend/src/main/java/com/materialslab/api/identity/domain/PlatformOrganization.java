package com.materialslab.api.identity.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 平台管理员可见的组织开通摘要；不包含任何组织业务数据。 */
public record PlatformOrganization(
        UUID id,
        String organizationCode,
        String name,
        String status,
        OffsetDateTime createdAt) {
}
