"""项目写操作与事务服务。"""

import hashlib
import json
from datetime import timedelta
from typing import Any

from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import PermissionDenied, ValidationError

from apps.common.exceptions import ResourceVersionConflict
from apps.common.models import IdempotencyRequest
from apps.identity.models import User

from .models import (
    BusinessNumberSequence,
    Project,
    ProjectMember,
    ProjectMemberRole,
    ProjectStatus,
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

    owner = validated_data["owner"]
    if owner.organization_id != actor.organization_id:
        raise ValidationError({"owner_id": ["项目负责人必须属于当前组织"]})

    route_key = "POST:/api/v1/projects"
    request_hash = _canonical_request_hash(validated_data)
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
    ProjectMember.objects.create(
        organization_id=actor.organization_id,
        project=project,
        user=owner,
        member_role=ProjectMemberRole.OWNER,
        created_by=actor,
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
        ProjectStatus.ACTIVE,
        ProjectStatus.SUSPENDED,
    }:
        raise ValidationError({"status": ["当前项目状态不可编辑"]})
    owner = validated_data.get("owner")
    if owner and owner.organization_id != actor.organization_id:
        raise ValidationError({"owner_id": ["项目负责人必须属于当前组织"]})

    previous_owner_id = project.owner_id
    for field_name, value in validated_data.items():
        setattr(project, field_name, value)
    project.updated_by = actor
    project.version += 1
    project.save()
    if owner and owner.id != previous_owner_id:
        ProjectMember.objects.filter(
            project=project,
            member_role=ProjectMemberRole.OWNER,
        ).update(member_role=ProjectMemberRole.RESEARCHER)
        ProjectMember.objects.update_or_create(
            project=project,
            user=owner,
            defaults={
                "organization_id": actor.organization_id,
                "member_role": ProjectMemberRole.OWNER,
                "created_by": actor,
            },
        )
    return project
