package com.materialslab.api.identity.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 超级管理员签发的注册邀请码元数据；邀请码明文仅在创建响应中出现一次。 */
public record RegistrationInvitation(
        UUID id,
        String roleCode,
        String roleName,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime usedAt,
        OffsetDateTime createdAt) { }
