package com.materialslab.api.identity.domain;

/** 系统管理员可分配的角色选项。 */
public record ManagedRoleOption(String roleCode, String name, String description) { }
