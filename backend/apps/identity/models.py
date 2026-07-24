"""组织、用户、角色和权限模型。"""

import uuid

from django.contrib.auth.models import AbstractUser
from django.db import models
from django.db.models import Q

from apps.common.models import TimeStampedModel


class OrganizationStatus(models.TextChoices):
    """组织状态。"""

    ACTIVE = "active", "正常"
    SUSPENDED = "suspended", "停用"


class UserStatus(models.TextChoices):
    """用户业务状态。"""

    ACTIVE = "active", "正常"
    LOCKED = "locked", "锁定"
    DISABLED = "disabled", "禁用"


class RoleStatus(models.TextChoices):
    """角色状态。"""

    ACTIVE = "active", "正常"
    DISABLED = "disabled", "禁用"


class Organization(TimeStampedModel):
    """用户和业务数据的顶级隔离空间。"""

    organization_code = models.CharField(
        max_length=32,
        unique=True,
        db_comment="组织代码",
    )
    name = models.CharField(max_length=200, db_comment="组织名称")
    status = models.CharField(
        max_length=16,
        choices=OrganizationStatus.choices,
        default=OrganizationStatus.ACTIVE,
        db_comment="组织状态",
    )

    class Meta:
        """组织表配置。"""

        db_table = "organization"
        db_table_comment = "组织"
        ordering = ["organization_code"]

    def __str__(self) -> str:
        """返回组织展示名称。"""
        return self.name


class User(AbstractUser):
    """组织内登录用户。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    organization = models.ForeignKey(
        Organization,
        on_delete=models.PROTECT,
        related_name="users",
        db_comment="所属组织",
    )
    username = models.CharField(max_length=64, db_comment="登录名")
    display_name = models.CharField(max_length=100, db_comment="显示名称")
    email = models.EmailField(blank=True, db_comment="邮箱")
    status = models.CharField(
        max_length=16,
        choices=UserStatus.choices,
        default=UserStatus.ACTIVE,
        db_comment="用户状态",
    )
    is_super_admin = models.BooleanField(default=False, db_comment="平台超级管理员标记")
    created_at = models.DateTimeField(auto_now_add=True, db_comment="创建时间")
    updated_at = models.DateTimeField(auto_now=True, db_comment="最后更新时间")

    REQUIRED_FIELDS = ["organization_id", "display_name"]

    class Meta:
        """用户表配置。"""

        db_table = "user_account"
        db_table_comment = "用户账户"
        constraints = [
            models.UniqueConstraint(
                fields=["organization", "username"],
                name="uk_user_account_org_username",
            ),
            models.UniqueConstraint(
                fields=["organization", "email"],
                condition=~Q(email=""),
                name="uk_user_account_org_email",
            ),
        ]
        indexes = [
            models.Index(
                fields=["organization", "status"],
                name="idx_user_org_status",
            )
        ]

    def __str__(self) -> str:
        """返回用户展示名称。"""
        return self.display_name

    def has_permission_code(self, permission_code: str) -> bool:
        """判断用户是否具有指定业务权限。

        Args:
            permission_code: 稳定权限代码。

        Returns:
            用户是否具有该权限。
        """
        if self.is_super_admin:
            return True
        return self.user_roles.filter(
            role__status=RoleStatus.ACTIVE,
            role__role_permissions__permission__permission_code=permission_code,
        ).exists()

    def permission_codes(self) -> list[str]:
        """返回用户当前全部权限码。

        Returns:
            排序后的权限码列表。
        """
        if self.is_super_admin:
            return list(
                Permission.objects.order_by("permission_code").values_list(
                    "permission_code",
                    flat=True,
                )
            )
        return list(
            Permission.objects.filter(
                role_permissions__role__user_roles__user=self,
                role_permissions__role__status=RoleStatus.ACTIVE,
            )
            .values_list("permission_code", flat=True)
            .distinct()
            .order_by("permission_code")
        )


class Role(TimeStampedModel):
    """组织内角色。"""

    organization = models.ForeignKey(
        Organization,
        on_delete=models.CASCADE,
        related_name="roles",
        db_comment="所属组织",
    )
    role_code = models.CharField(max_length=64, db_comment="稳定角色代码")
    name = models.CharField(max_length=100, db_comment="角色名称")
    description = models.CharField(max_length=500, blank=True, db_comment="角色说明")
    is_system = models.BooleanField(default=False, db_comment="是否系统角色")
    status = models.CharField(
        max_length=16,
        choices=RoleStatus.choices,
        default=RoleStatus.ACTIVE,
        db_comment="角色状态",
    )

    class Meta:
        """角色表配置。"""

        db_table = "role"
        db_table_comment = "角色"
        constraints = [
            models.UniqueConstraint(
                fields=["organization", "role_code"],
                name="uk_role_org_code",
            )
        ]

    def __str__(self) -> str:
        """返回角色名称。"""
        return self.name


class Permission(models.Model):
    """稳定业务权限。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    permission_code = models.CharField(max_length=100, unique=True, db_comment="权限代码")
    name = models.CharField(max_length=100, db_comment="权限名称")
    module_code = models.CharField(max_length=64, db_comment="所属模块")
    description = models.CharField(max_length=500, blank=True, db_comment="权限说明")

    class Meta:
        """权限表配置。"""

        db_table = "permission"
        db_table_comment = "权限"
        ordering = ["module_code", "permission_code"]

    def __str__(self) -> str:
        """返回权限名称。"""
        return self.name


class UserRole(models.Model):
    """用户角色关联。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    organization = models.ForeignKey(
        Organization,
        on_delete=models.CASCADE,
        related_name="user_role_links",
        db_comment="所属组织",
    )
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name="user_roles",
        db_comment="用户",
    )
    role = models.ForeignKey(
        Role,
        on_delete=models.CASCADE,
        related_name="user_roles",
        db_comment="角色",
    )
    created_at = models.DateTimeField(auto_now_add=True, db_comment="分配时间")
    created_by = models.ForeignKey(
        User,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="created_user_roles",
        db_comment="分配用户",
    )

    class Meta:
        """用户角色关联表配置。"""

        db_table = "user_role"
        db_table_comment = "用户角色关联"
        constraints = [
            models.UniqueConstraint(
                fields=["user", "role"],
                name="uk_user_role_user_role",
            )
        ]

    def __str__(self) -> str:
        """返回用户与角色关系。"""
        return f"{self.user} - {self.role}"


class RolePermission(models.Model):
    """角色权限关联。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    role = models.ForeignKey(
        Role,
        on_delete=models.CASCADE,
        related_name="role_permissions",
        db_comment="角色",
    )
    permission = models.ForeignKey(
        Permission,
        on_delete=models.CASCADE,
        related_name="role_permissions",
        db_comment="权限",
    )
    created_at = models.DateTimeField(auto_now_add=True, db_comment="授权时间")
    created_by = models.ForeignKey(
        User,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="created_role_permissions",
        db_comment="授权用户",
    )

    class Meta:
        """角色权限关联表配置。"""

        db_table = "role_permission"
        db_table_comment = "角色权限关联"
        constraints = [
            models.UniqueConstraint(
                fields=["role", "permission"],
                name="uk_role_permission_role_permission",
            )
        ]

    def __str__(self) -> str:
        """返回角色与权限关系。"""
        return f"{self.role} - {self.permission}"
