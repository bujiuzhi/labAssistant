package com.materialslab.api.identity.domain;

import java.util.UUID;

/** 当前会话所属组织，用于向前端明确 RBAC 的数据边界。 */
public record OrganizationSummary(UUID id, String organizationCode, String name, UUID parentId, String status) { }
