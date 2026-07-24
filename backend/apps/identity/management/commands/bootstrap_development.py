"""初始化开发组织、权限、角色和角色账号。"""

import os

from django.core.management.base import BaseCommand
from django.db import transaction

from apps.identity.models import (
    Organization,
    Permission,
    Role,
    RolePermission,
    User,
    UserRole,
)

PERMISSIONS = [
    ("project.view", "查看项目", "projects"),
    ("project.view_all", "查看组织全部项目", "projects"),
    ("project.create", "创建项目", "projects"),
    ("project.update", "更新项目", "projects"),
    ("project.manage_members", "管理项目成员", "projects"),
    ("experiment.view", "查看实验记录", "experiments"),
    ("experiment.view_all", "查看组织全部实验", "experiments"),
    ("experiment.create", "创建实验计划", "experiments"),
    ("experiment.update", "编辑实验记录", "experiments"),
    ("experiment.execute", "执行实验状态流转", "experiments"),
]

ROLE_PERMISSIONS = {
    "system_admin": [code for code, _, _ in PERMISSIONS],
    "project_manager": [code for code, _, _ in PERMISSIONS],
    "researcher": [
        "project.view",
        "experiment.view",
        "experiment.create",
        "experiment.update",
        "experiment.execute",
    ],
    "inspector": ["project.view", "experiment.view"],
}

ROLE_NAMES = {
    "system_admin": "超级管理员",
    "project_manager": "项目负责人",
    "researcher": "研究人员",
    "inspector": "检测人员",
}

DEFAULT_DEVELOPMENT_PASSWORD = "00000000"

DEVELOPMENT_USERS = [
    ("manager", "张伟", "project_manager"),
    ("researcher", "李娜", "researcher"),
    ("inspector", "王强", "inspector"),
]


class Command(BaseCommand):
    """初始化可重复执行的开发数据。"""

    help = "初始化开发组织、系统角色、权限和四类开发账号"

    def add_arguments(self, parser) -> None:
        """注册命令参数。

        Args:
            parser: Django 命令参数解析器。
        """
        parser.add_argument("--organization-code", default="LAB")
        parser.add_argument("--organization-name", default="材料研发中心")
        parser.add_argument("--admin-username", default="admin")
        parser.add_argument("--admin-display-name", default="刘李园")
        parser.add_argument(
            "--password-environment",
            default="MATERIALS_LAB_DEVELOPMENT_PASSWORD",
            help="读取开发账号统一密码的环境变量名，未设置时使用 8 个 0",
        )

    @transaction.atomic
    def handle(self, *args, **options) -> None:
        """执行初始化。

        Args:
            *args: 未使用的位置参数。
            **options: 命令参数。

        """
        password_environment = options["password_environment"]
        password = os.getenv(password_environment, DEFAULT_DEVELOPMENT_PASSWORD)

        organization, _ = Organization.objects.get_or_create(
            organization_code=options["organization_code"],
            defaults={"name": options["organization_name"]},
        )
        permission_map = {}
        for permission_code, name, module_code in PERMISSIONS:
            permission, _ = Permission.objects.update_or_create(
                permission_code=permission_code,
                defaults={"name": name, "module_code": module_code},
            )
            permission_map[permission_code] = permission

        roles = {}
        for role_code, permission_codes in ROLE_PERMISSIONS.items():
            role, _ = Role.objects.update_or_create(
                organization=organization,
                role_code=role_code,
                defaults={"name": ROLE_NAMES[role_code], "is_system": True},
            )
            RolePermission.objects.filter(role=role).delete()
            RolePermission.objects.bulk_create(
                [
                    RolePermission(role=role, permission=permission_map[permission_code])
                    for permission_code in permission_codes
                ]
            )
            roles[role_code] = role

        user_specs = [
            (
                options["admin_username"],
                options["admin_display_name"],
                "system_admin",
                True,
            ),
            *[
                (username, display_name, role_code, False)
                for username, display_name, role_code in DEVELOPMENT_USERS
            ],
        ]
        for username, display_name, role_code, is_admin in user_specs:
            user, created = User.objects.get_or_create(
                organization=organization,
                username=username,
                defaults={"display_name": display_name},
            )
            user.display_name = display_name
            user.is_active = True
            user.is_staff = is_admin
            user.is_superuser = is_admin
            user.is_super_admin = is_admin
            user.set_password(password)
            user.save()
            UserRole.objects.get_or_create(
                organization=organization,
                user=user,
                role=roles[role_code],
            )
            action = "创建" if created else "更新"
            self.stdout.write(
                self.style.SUCCESS(
                    f"已{action}开发账号 {user.username}（{ROLE_NAMES[role_code]}）"
                )
            )
