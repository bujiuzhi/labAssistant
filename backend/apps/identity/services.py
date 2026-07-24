"""组织用户创建、更新与密码重置服务。"""

import logging
from typing import Any

from django.db import transaction
from rest_framework.exceptions import ValidationError

from apps.common.exceptions import BusinessRuleConflict

from .models import Role, RoleStatus, User, UserRole, UserStatus

logger = logging.getLogger(__name__)

ASSIGNABLE_ROLE_CODES = {"project_manager", "researcher", "inspector"}


def _resolve_assignable_roles(actor: User, role_codes: list[str]) -> list[Role]:
    """解析当前组织内可分配的系统角色。

    Args:
        actor: 当前超级管理员。
        role_codes: 待分配的角色代码。

    Returns:
        已校验角色列表。

    Raises:
        ValidationError: 角色为空、不存在、已停用或不可分配。
    """
    normalized_codes = sorted(set(role_codes))
    if not normalized_codes:
        raise ValidationError({"role_codes": ["至少选择一个系统角色"]})
    if any(code not in ASSIGNABLE_ROLE_CODES for code in normalized_codes):
        raise ValidationError({"role_codes": ["包含不可分配的系统角色"]})
    roles = list(
        Role.objects.filter(
            organization_id=actor.organization_id,
            role_code__in=normalized_codes,
            status=RoleStatus.ACTIVE,
        ).order_by("role_code")
    )
    if len(roles) != len(normalized_codes):
        raise ValidationError({"role_codes": ["角色不存在或已停用"]})
    return roles


def _ensure_username_available(
    *,
    organization_id,
    username: str,
    exclude_user_id=None,
) -> None:
    """校验组织内用户名唯一。

    Args:
        organization_id: 组织主键。
        username: 待校验用户名。
        exclude_user_id: 更新时排除的用户主键。

    Raises:
        ValidationError: 用户名已经存在。
    """
    queryset = User.objects.filter(
        organization_id=organization_id,
        username=username,
    )
    if exclude_user_id:
        queryset = queryset.exclude(id=exclude_user_id)
    if queryset.exists():
        raise ValidationError({"username": ["当前组织已存在该用户名"]})


def _replace_user_roles(*, actor: User, user: User, roles: list[Role]) -> None:
    """替换用户的组织角色。

    Args:
        actor: 当前超级管理员。
        user: 待更新用户。
        roles: 新角色列表。
    """
    UserRole.objects.filter(user=user).delete()
    UserRole.objects.bulk_create(
        [
            UserRole(
                organization_id=actor.organization_id,
                user=user,
                role=role,
                created_by=actor,
            )
            for role in roles
        ]
    )


@transaction.atomic
def create_organization_user(
    *,
    actor: User,
    validated_data: dict[str, Any],
) -> User:
    """创建组织用户并分配角色。

    Args:
        actor: 当前超级管理员。
        validated_data: 已校验创建参数。

    Returns:
        新建用户。
    """
    role_codes = validated_data.pop("role_codes")
    password = validated_data.pop("password")
    username = validated_data["username"].strip()
    _ensure_username_available(
        organization_id=actor.organization_id,
        username=username,
    )
    roles = _resolve_assignable_roles(actor, role_codes)
    user_status = validated_data.get("status", UserStatus.ACTIVE)
    user = User(
        organization_id=actor.organization_id,
        username=username,
        display_name=validated_data["display_name"].strip(),
        email=validated_data.get("email", "").strip().lower(),
        status=user_status,
        is_active=user_status == UserStatus.ACTIVE,
    )
    user.set_password(password)
    user.save()
    _replace_user_roles(actor=actor, user=user, roles=roles)
    logger.info(
        "超级管理员创建组织用户",
        extra={
            "actor_user_id": str(actor.id),
            "target_user_id": str(user.id),
            "organization_id": str(actor.organization_id),
        },
    )
    return user


@transaction.atomic
def update_organization_user(
    *,
    actor: User,
    user_id,
    validated_data: dict[str, Any],
) -> User:
    """更新组织用户基本信息、状态和角色。

    Args:
        actor: 当前超级管理员。
        user_id: 待更新用户主键。
        validated_data: 已校验更新参数。

    Returns:
        更新后的用户。

    Raises:
        BusinessRuleConflict: 目标为受保护的超级管理员。
    """
    user = User.objects.select_for_update().get(
        id=user_id,
        organization_id=actor.organization_id,
    )
    if user.is_super_admin:
        raise BusinessRuleConflict("超级管理员账号受保护，不能通过用户管理修改")

    role_codes = validated_data.pop("role_codes", None)
    roles = (
        _resolve_assignable_roles(actor, role_codes)
        if role_codes is not None
        else None
    )
    username = validated_data.get("username")
    if username is not None:
        username = username.strip()
        _ensure_username_available(
            organization_id=actor.organization_id,
            username=username,
            exclude_user_id=user.id,
        )
        validated_data["username"] = username
    if "display_name" in validated_data:
        validated_data["display_name"] = validated_data["display_name"].strip()
    if "email" in validated_data:
        validated_data["email"] = validated_data["email"].strip().lower()
    if "status" in validated_data:
        validated_data["is_active"] = validated_data["status"] == UserStatus.ACTIVE

    for field_name, value in validated_data.items():
        setattr(user, field_name, value)
    user.save()
    if roles is not None:
        _replace_user_roles(actor=actor, user=user, roles=roles)
    logger.info(
        "超级管理员更新组织用户",
        extra={
            "actor_user_id": str(actor.id),
            "target_user_id": str(user.id),
            "organization_id": str(actor.organization_id),
        },
    )
    return user


@transaction.atomic
def reset_organization_user_password(
    *,
    actor: User,
    user_id,
    password: str,
) -> User:
    """重置组织用户登录密码。

    Args:
        actor: 当前超级管理员。
        user_id: 待重置用户主键。
        password: 新密码明文。

    Returns:
        已更新密码的用户。

    Raises:
        BusinessRuleConflict: 目标为受保护的超级管理员。
    """
    user = User.objects.select_for_update().get(
        id=user_id,
        organization_id=actor.organization_id,
    )
    if user.is_super_admin:
        raise BusinessRuleConflict("超级管理员账号受保护，不能在此处重置密码")
    user.set_password(password)
    user.save(update_fields=["password", "updated_at"])
    logger.info(
        "超级管理员重置组织用户密码",
        extra={
            "actor_user_id": str(actor.id),
            "target_user_id": str(user.id),
            "organization_id": str(actor.organization_id),
        },
    )
    return user
