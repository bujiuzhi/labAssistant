package com.materialslab.api.identity.domain;

import java.util.UUID;

/** 认证和对象范围计算所需的用户账户。 */
public record UserAccount(
        UUID id,
        UUID organizationId,
        String username,
        String password,
        String displayName,
        String status,
        boolean superAdmin) {
}
