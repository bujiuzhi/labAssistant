"""项目写操作与事务服务。"""

import hashlib
import json
from datetime import timedelta
from pathlib import Path
from typing import Any

from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import PermissionDenied, ValidationError

from apps.common.exceptions import BusinessRuleConflict, ResourceVersionConflict
from apps.common.models import IdempotencyRequest
from apps.identity.models import User

from .models import (
    BusinessNumberSequence,
    Project,
    ProjectDocument,
    ProjectMember,
    ProjectMemberRole,
    ProjectStatus,
)


def _resolve_member_users(actor: User, member_ids: list) -> list[User]:
    """解析并校验项目成员。

    Args:
        actor: 当前操作用户。
        member_ids: 待设置的成员用户 ID。

    Returns:
        同组织的有效用户列表。

    Raises:
        ValidationError: 用户不存在、已停用或跨组织。
    """
    users = list(
        User.objects.filter(
            organization_id=actor.organization_id,
            id__in=member_ids,
            is_active=True,
        )
    )
    if len(users) != len(set(member_ids)):
        raise ValidationError({"member_ids": ["项目成员必须是当前组织的有效用户"]})
    return users


def _sync_project_members(
    *,
    project: Project,
    owner: User,
    member_users: list[User],
    actor: User,
) -> None:
    """同步项目负责人和成员关系。

    Args:
        project: 待同步项目。
        owner: 项目负责人。
        member_users: 普通项目成员。
        actor: 当前操作用户。
    """
    selected_users = {user.id: user for user in member_users}
    selected_users[owner.id] = owner
    ProjectMember.objects.filter(project=project).exclude(
        user_id__in=selected_users.keys()
    ).delete()

    existing_members = {
        member.user_id: member
        for member in ProjectMember.objects.filter(project=project)
    }
    for user_id, user in selected_users.items():
        existing = existing_members.get(user_id)
        member_role = ProjectMemberRole.OWNER if user_id == owner.id else (
            existing.member_role
            if existing and existing.member_role != ProjectMemberRole.OWNER
            else ProjectMemberRole.RESEARCHER
        )
        ProjectMember.objects.update_or_create(
            project=project,
            user=user,
            defaults={
                "organization_id": project.organization_id,
                "member_role": member_role,
                "created_by": actor,
            },
        )


def _canonical_request_hash(data: dict[str, Any]) -> str:
    """计算规范化请求摘要。

    Args:
        data: 已校验请求数据。

    Returns:
        SHA-256 十六进制摘要。
    """
    normalized = json.dumps(data, sort_keys=True, default=str, ensure_ascii=False)
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _next_project_number(organization_id) -> str:
    """生成组织内项目编号。

    Args:
        organization_id: 组织主键。

    Returns:
        `PRJ-年份-六位序号` 格式编号。
    """
    year = timezone.localdate().year
    sequence, _ = BusinessNumberSequence.objects.select_for_update().get_or_create(
        organization_id=organization_id,
        business_type="project",
        period_key=str(year),
        defaults={"current_value": 0},
    )
    sequence.current_value += 1
    sequence.save(update_fields=["current_value", "updated_at"])
    return f"PRJ-{year}-{sequence.current_value:06d}"


@transaction.atomic
def create_project(
    *,
    actor: User,
    validated_data: dict[str, Any],
    idempotency_key: str,
) -> tuple[Project, bool]:
    """创建项目并建立负责人成员关系。

    Args:
        actor: 当前操作用户。
        validated_data: 已校验项目数据。
        idempotency_key: 客户端幂等键。

    Returns:
        项目与是否首次创建。

    Raises:
        PermissionDenied: 用户无创建权限。
        ValidationError: 幂等键复用不同请求或负责人跨组织。
    """
    if not actor.has_permission_code("project.create"):
        raise PermissionDenied("无项目创建权限")

    member_ids = validated_data.pop("member_ids", [])
    if member_ids and not actor.has_permission_code("project.manage_members"):
        raise PermissionDenied("无项目成员管理权限")
    owner = validated_data["owner"]
    if owner.organization_id != actor.organization_id:
        raise ValidationError({"owner_id": ["项目负责人必须属于当前组织"]})

    route_key = "POST:/api/v1/projects"
    request_hash = _canonical_request_hash(
        {**validated_data, "member_ids": member_ids}
    )
    existing = (
        IdempotencyRequest.objects.select_for_update()
        .filter(
            organization_id=actor.organization_id,
            user=actor,
            route_key=route_key,
            idempotency_key=idempotency_key,
        )
        .first()
    )
    if existing:
        if existing.request_hash != request_hash:
            raise ValidationError({"idempotency_key": ["同一幂等键不能用于不同请求"]})
        if existing.status == IdempotencyRequest.Status.COMPLETED and existing.response_body:
            project = Project.objects.get(id=existing.response_body["project_id"])
            return project, False
        raise ValidationError({"idempotency_key": ["相同请求正在处理中"]})

    idempotency_record = IdempotencyRequest.objects.create(
        organization_id=actor.organization_id,
        user=actor,
        route_key=route_key,
        idempotency_key=idempotency_key,
        request_hash=request_hash,
        expires_at=timezone.now() + timedelta(hours=24),
    )
    project = Project.objects.create(
        organization_id=actor.organization_id,
        project_no=_next_project_number(actor.organization_id),
        created_by=actor,
        updated_by=actor,
        **validated_data,
    )
    member_users = _resolve_member_users(actor, member_ids)
    _sync_project_members(
        project=project,
        owner=owner,
        member_users=member_users,
        actor=actor,
    )
    idempotency_record.status = IdempotencyRequest.Status.COMPLETED
    idempotency_record.response_status = 201
    idempotency_record.response_body = {"project_id": str(project.id)}
    idempotency_record.save(
        update_fields=["status", "response_status", "response_body"]
    )
    return project, True


@transaction.atomic
def update_project(
    *,
    project_id,
    actor: User,
    validated_data: dict[str, Any],
    expected_version: int,
) -> Project:
    """更新项目并执行乐观锁校验。

    Args:
        project_id: 项目主键。
        actor: 当前操作用户。
        validated_data: 已校验更新数据。
        expected_version: 客户端资源版本。

    Returns:
        更新后的项目。

    Raises:
        PermissionDenied: 用户无更新权限或对象范围不符。
        ResourceVersionConflict: 资源版本已变化。
        ValidationError: 负责人跨组织或项目不可编辑。
    """
    project = Project.objects.select_for_update().get(
        id=project_id,
        organization_id=actor.organization_id,
    )
    if not actor.has_permission_code("project.update"):
        raise PermissionDenied("无项目更新权限")
    if not actor.has_permission_code("project.view_all") and project.owner_id != actor.id:
        raise PermissionDenied("只能更新本人负责的项目")
    if project.version != expected_version:
        raise ResourceVersionConflict()
    if project.status not in {
        ProjectStatus.DRAFT,
        ProjectStatus.NOT_STARTED,
        ProjectStatus.ACTIVE,
        ProjectStatus.AT_RISK,
        ProjectStatus.SUSPENDED,
    }:
        raise BusinessRuleConflict("当前项目状态不可编辑")
    member_ids = validated_data.pop("member_ids", None)
    if member_ids is not None and not actor.has_permission_code(
        "project.manage_members"
    ):
        raise PermissionDenied("无项目成员管理权限")
    owner = validated_data.get("owner")
    if owner and owner.organization_id != actor.organization_id:
        raise ValidationError({"owner_id": ["项目负责人必须属于当前组织"]})

    previous_member_users = [
        member.user
        for member in ProjectMember.objects.select_related("user").filter(
            project=project
        )
    ]
    for field_name, value in validated_data.items():
        setattr(project, field_name, value)
    project.updated_by = actor
    project.version += 1
    project.save()
    effective_owner = owner or project.owner
    member_users = (
        _resolve_member_users(actor, member_ids)
        if member_ids is not None
        else previous_member_users
    )
    _sync_project_members(
        project=project,
        owner=effective_owner,
        member_users=member_users,
        actor=actor,
    )
    return project


@transaction.atomic
def create_project_document(
    *,
    project: Project,
    actor: User,
    validated_data: dict[str, Any],
) -> ProjectDocument:
    """上传项目文档并同步项目文档计数。

    Args:
        project: 关联项目。
        actor: 当前操作用户。
        validated_data: 已校验上传字段。

    Returns:
        新建项目文档。

    Raises:
        PermissionDenied: 用户无上传权限。
        BusinessRuleConflict: 项目已完成或归档。
    """
    if not actor.has_permission_code("document.upload"):
        raise PermissionDenied("无项目文档上传权限")
    if project.status in {ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED}:
        raise BusinessRuleConflict("已完成或归档项目不可上传文档")

    upload = validated_data.pop("file")
    extension = Path(upload.name).suffix.lower().lstrip(".") or "file"
    document = ProjectDocument.objects.create(
        organization_id=project.organization_id,
        project=project,
        name=upload.name,
        file=upload,
        extension=extension,
        mime_type=getattr(upload, "content_type", "") or "",
        file_size=upload.size,
        uploaded_by=actor,
        updated_by=actor,
        **validated_data,
    )
    project.document_count = project.documents.count()
    project.save(update_fields=["document_count", "updated_at"])
    return document
