package com.materialslab.api.identity.service;

import java.util.List;

/** 开发种子与生产首次初始化共用的系统角色、权限字典；不包含账号或业务样例。 */
public final class SystemIdentityCatalog {
    private SystemIdentityCatalog() {}

    public record PermissionDefinition(String code, String name, String moduleCode) {}

    public record RoleDefinition(String code, String name, List<String> permissions) {}

    public static final List<PermissionDefinition> PERMISSIONS = List.of(
            new PermissionDefinition("organization.read", "查看组织信息", "organization"),
            new PermissionDefinition("project.read", "查看项目", "project"),
            new PermissionDefinition("project.create", "创建项目", "project"),
            new PermissionDefinition("project.update", "编辑项目", "project"),
            new PermissionDefinition("project.archive", "归档项目", "project"),
            new PermissionDefinition("document.view", "查看项目文档", "document"),
            new PermissionDefinition("document.upload", "上传项目文档", "document"),
            new PermissionDefinition("experiment.read", "查看实验", "experiment"),
            new PermissionDefinition("experiment.create", "创建实验", "experiment"),
            new PermissionDefinition("experiment.update", "编辑实验记录", "experiment"),
            new PermissionDefinition("experiment.transition", "迁移实验状态", "experiment"));

    // 超级管理员由 is_super_admin 授权，不通过重复写入全部权限表达。
    public static final List<RoleDefinition> ROLES = List.of(
            new RoleDefinition("super_admin", "超级管理员", List.of()),
            new RoleDefinition("project_manager", "项目管理员", PERMISSIONS.stream().map(PermissionDefinition::code).toList()),
            new RoleDefinition("researcher", "实验员", List.of("organization.read", "project.read", "document.view",
                    "experiment.read", "experiment.update", "experiment.transition")));
}
