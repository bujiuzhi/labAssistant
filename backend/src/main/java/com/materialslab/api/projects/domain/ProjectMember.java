package com.materialslab.api.projects.domain;

import java.util.UUID;

/** 项目成员展示信息。 */
public record ProjectMember(UUID userId, String displayName, String memberRole) { }
