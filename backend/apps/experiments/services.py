"""电子实验记录本写操作与状态流转服务。"""

import logging
from typing import Any

from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import PermissionDenied, ValidationError

from apps.common.exceptions import BusinessRuleConflict, ResourceVersionConflict
from apps.common.services import record_business_operation
from apps.identity.models import User
from apps.identity.selectors import visible_organization_ids
from apps.projects.models import Project
from apps.projects.selectors import projects_for_user

from .models import (
    Experiment,
    ExperimentAttachment,
    ExperimentAttachmentKind,
    ExperimentParticipant,
    ExperimentParticipantRole,
    ExperimentRecord,
    ExperimentStatus,
)
from .selectors import experiments_for_user

logger = logging.getLogger(__name__)

RECORD_FIELDS = {
    "formula_columns",
    "formula_rows",
    "extra_tables",
    "process_text",
    "extra_processes",
    "result_text",
}


@transaction.atomic
def create_experiment_attachment(
    *,
    experiment: Experiment,
    actor: User,
    validated_data: dict[str, Any],
) -> ExperimentAttachment:
    """保存电子实验记录的真实附件文件。"""
    if not actor.has_permission_code("experiment.update"):
        raise PermissionDenied("无实验记录编辑权限")
    if not experiments_for_user(actor).filter(id=experiment.id).exists():
        raise PermissionDenied("只能为本人参与的实验上传附件")
    upload = validated_data["file"]
    kind = validated_data["kind"]
    attachment_limit = 20 if kind == ExperimentAttachmentKind.PROCESS_IMAGE else 30
    if experiment.attachments.filter(kind=kind).count() >= attachment_limit:
        label = "过程图片" if kind == ExperimentAttachmentKind.PROCESS_IMAGE else "结果附件"
        raise ValidationError({"file": [f"{label}最多 {attachment_limit} 个"]})
    attachment = ExperimentAttachment.objects.create(
        organization_id=experiment.organization_id,
        experiment=experiment,
        kind=kind,
        name=upload.name,
        file=upload,
        mime_type=getattr(upload, "content_type", "") or "",
        file_size=upload.size,
        uploaded_by=actor,
    )
    logger.info(
        "上传实验附件",
        extra={
            "experiment_no": experiment.experiment_no,
            "attachment_id": str(attachment.id),
            "actor_id": str(actor.id),
        },
    )
    record_business_operation(
        actor=actor,
        domain="experiment",
        object_id=experiment.id,
        object_no=experiment.experiment_no,
        action_type="attachment_uploaded",
        description=f"上传{attachment.get_kind_display()}“{attachment.name}”",
        changes={"attachment_id": str(attachment.id), "kind": attachment.kind},
        organization_id=experiment.organization_id,
    )
    return attachment


@transaction.atomic
def delete_experiment_attachment(
    *,
    experiment: Experiment,
    attachment_id,
    actor: User,
) -> None:
    """删除实验记录附件及其存储文件。

    Args:
        experiment: 当前可见实验。
        attachment_id: 附件主键。
        actor: 当前操作用户。
    """
    if not actor.has_permission_code("experiment.update"):
        raise PermissionDenied("无实验记录编辑权限")
    if not experiments_for_user(actor).filter(id=experiment.id).exists():
        raise PermissionDenied("只能维护本人可见实验的附件")
    attachment = ExperimentAttachment.objects.select_for_update().get(
        id=attachment_id,
        experiment=experiment,
        organization_id=experiment.organization_id,
    )
    storage = attachment.file.storage
    storage_name = attachment.file.name
    attachment_name = attachment.name
    attachment_kind = attachment.kind
    attachment_kind_label = attachment.get_kind_display()
    attachment.delete()
    transaction.on_commit(lambda: storage.delete(storage_name))
    record_business_operation(
        actor=actor,
        domain="experiment",
        object_id=experiment.id,
        object_no=experiment.experiment_no,
        action_type="attachment_deleted",
        description=f"删除{attachment_kind_label}“{attachment_name}”",
        changes={"attachment_id": str(attachment_id), "kind": attachment_kind},
        organization_id=experiment.organization_id,
    )


def _sync_project_experiment_count(project_id) -> None:
    """同步项目实验计数。

    Args:
        project_id: 项目主键。
    """
    Project.objects.filter(id=project_id).update(
        experiment_count=Experiment.objects.filter(project_id=project_id).count()
    )


def _resolve_participants(actor: User, participant_ids: list) -> list[User]:
    """解析并校验实验参与用户。

    Args:
        actor: 当前操作用户。
        participant_ids: 参与用户 ID。

    Returns:
        当前组织有效用户列表。
    """
    users = list(
        User.objects.filter(
            organization_id__in=visible_organization_ids(actor),
            id__in=participant_ids,
            is_active=True,
        )
    )
    if len(users) != len(set(participant_ids)):
        raise ValidationError(
            {"participant_ids": ["实验参与人员必须是当前组织的有效用户"]}
        )
    return users


def _sync_participants(
    *,
    experiment: Experiment,
    owner: User,
    participant_users: list[User],
    actor: User,
) -> None:
    """同步实验负责人和参与人员。

    Args:
        experiment: 实验计划。
        owner: 实验负责人。
        participant_users: 普通参与人员。
        actor: 当前操作用户。
    """
    selected_users = {user.id: user for user in participant_users}
    selected_users[owner.id] = owner
    ExperimentParticipant.objects.filter(experiment=experiment).exclude(
        user_id__in=selected_users
    ).delete()
    for user_id, user in selected_users.items():
        ExperimentParticipant.objects.update_or_create(
            experiment=experiment,
            user=user,
            defaults={
                "organization_id": experiment.organization_id,
                "participant_role": (
                    ExperimentParticipantRole.OWNER
                    if user_id == owner.id
                    else ExperimentParticipantRole.PARTICIPANT
                ),
                "created_by": actor,
            },
        )


def _next_experiment_number(organization_id) -> str:
    """生成组织内实验编号。

    Args:
        organization_id: 组织主键。

    Returns:
        `EXP-年份-三位序号` 格式编号。
    """
    year = timezone.localdate().year
    prefix = f"EXP-{year}-"
    last_number = (
        Experiment.objects.filter(
            organization_id=organization_id,
            experiment_no__startswith=prefix,
        )
        .order_by("-experiment_no")
        .values_list("experiment_no", flat=True)
        .first()
    )
    serial = 1
    if last_number:
        try:
            serial = int(last_number.rsplit("-", 1)[-1]) + 1
        except ValueError:
            serial = Experiment.objects.filter(
                organization_id=organization_id,
                experiment_no__startswith=prefix,
            ).count() + 1
    return f"{prefix}{serial:03d}"


def _record_data(validated_data: dict[str, Any]) -> dict[str, Any]:
    """从请求数据提取实验记录字段。

    Args:
        validated_data: 已校验数据。

    Returns:
        实验记录字段。
    """
    return {
        field_name: validated_data.pop(field_name)
        for field_name in list(validated_data)
        if field_name in RECORD_FIELDS
    }


@transaction.atomic
def create_experiment(*, actor: User, validated_data: dict[str, Any]) -> Experiment:
    """创建实验计划和电子记录。

    Args:
        actor: 当前操作用户。
        validated_data: 已校验请求数据。

    Returns:
        新建实验。
    """
    if not actor.has_permission_code("experiment.create"):
        raise PermissionDenied("无实验计划创建权限")
    project = validated_data["project"]
    if not projects_for_user(actor).filter(id=project.id).exists():
        raise PermissionDenied("无关联项目访问权限")
    owner = validated_data.pop("owner", actor)
    if owner.organization_id not in visible_organization_ids(actor):
        raise ValidationError({"owner_id": ["实验负责人必须属于当前可见组织范围"]})
    participant_ids = validated_data.pop("participant_ids", [])
    record_data = _record_data(validated_data)
    experiment = Experiment.objects.create(
        organization_id=project.organization_id,
        experiment_no=_next_experiment_number(actor.organization_id),
        owner=owner,
        created_by=actor,
        updated_by=actor,
        **validated_data,
    )
    default_columns = [
        {
            "id": f"col_{index + 1}",
            "label": "原料名称" if index == 0 else "",
        }
        for index in range(4)
    ]
    ExperimentRecord.objects.create(
        experiment=experiment,
        formula_columns=record_data.pop(
            "formula_columns",
            default_columns,
        ),
        formula_rows=record_data.pop(
            "formula_rows",
            [
                {column["id"]: "" for column in default_columns},
                {column["id"]: "" for column in default_columns},
            ],
        ),
        **record_data,
    )
    _sync_participants(
        experiment=experiment,
        owner=owner,
        participant_users=_resolve_participants(actor, participant_ids),
        actor=actor,
    )
    _sync_project_experiment_count(project.id)
    logger.info(
        "创建实验计划",
        extra={"experiment_no": experiment.experiment_no, "actor_id": str(actor.id)},
    )
    record_business_operation(
        actor=actor,
        domain="experiment",
        object_id=experiment.id,
        object_no=experiment.experiment_no,
        action_type="experiment_created",
        description=f"创建实验计划“{experiment.name}”",
        changes={"project_id": str(project.id), "status": experiment.status},
        organization_id=experiment.organization_id,
    )
    return experiment


@transaction.atomic
def update_experiment(
    *,
    experiment_id,
    actor: User,
    validated_data: dict[str, Any],
    expected_version: int,
) -> Experiment:
    """更新实验计划和记录。

    Args:
        experiment_id: 实验主键。
        actor: 当前操作用户。
        validated_data: 已校验请求数据。
        expected_version: 客户端资源版本。

    Returns:
        更新后的实验。
    """
    experiment = Experiment.objects.select_for_update().get(
        id=experiment_id,
    )
    if not actor.has_permission_code("experiment.update"):
        raise PermissionDenied("无实验记录编辑权限")
    if not experiments_for_user(actor).filter(id=experiment.id).exists():
        raise PermissionDenied("只能编辑本人参与的实验")
    if experiment.version != expected_version:
        raise ResourceVersionConflict()
    previous_project_id = experiment.project_id
    new_project = validated_data.get("project")
    if new_project and not projects_for_user(actor).filter(id=new_project.id).exists():
        raise PermissionDenied("无关联项目访问权限")
    previous_owner_id = experiment.owner_id
    new_owner = validated_data.get("owner", experiment.owner)
    if new_owner.organization_id not in visible_organization_ids(actor):
        raise ValidationError({"owner_id": ["实验负责人必须属于当前可见组织范围"]})
    participant_ids = validated_data.pop("participant_ids", None)
    retained_participant_ids = list(
        experiment.participants.exclude(
            user_id__in={previous_owner_id, new_owner.id}
        ).values_list("user_id", flat=True)
    )
    record_data = _record_data(validated_data)
    for field_name, value in validated_data.items():
        setattr(experiment, field_name, value)
    experiment.owner = new_owner
    experiment.updated_by = actor
    experiment.version += 1
    experiment.save()
    record, _ = ExperimentRecord.objects.get_or_create(experiment=experiment)
    for field_name, value in record_data.items():
        setattr(record, field_name, value)
    if record_data:
        record.save()
    if participant_ids is not None or new_owner.id != previous_owner_id:
        users = _resolve_participants(
            actor,
            participant_ids
            if participant_ids is not None
            else retained_participant_ids,
        )
        _sync_participants(
            experiment=experiment,
            owner=new_owner,
            participant_users=users,
            actor=actor,
        )
    _sync_project_experiment_count(experiment.project_id)
    if previous_project_id != experiment.project_id:
        _sync_project_experiment_count(previous_project_id)
    logger.info(
        "更新实验记录",
        extra={"experiment_no": experiment.experiment_no, "actor_id": str(actor.id)},
    )
    record_business_operation(
        actor=actor,
        domain="experiment",
        object_id=experiment.id,
        object_no=experiment.experiment_no,
        action_type="experiment_updated",
        description=f"保存实验记录“{experiment.name}”",
        changes={"status": experiment.status, "version": experiment.version},
        organization_id=experiment.organization_id,
    )
    return experiment


@transaction.atomic
def copy_experiment(*, source: Experiment, actor: User) -> Experiment:
    """复制实验计划。

    Args:
        source: 源实验计划。
        actor: 当前操作用户。

    Returns:
        新的未开始实验计划。
    """
    if not actor.has_permission_code("experiment.create"):
        raise PermissionDenied("无实验计划创建权限")
    source_record = source.record
    return create_experiment(
        actor=actor,
        validated_data={
            "project": source.project,
            "name": f"{source.name}-副本",
            "experiment_type": source.experiment_type,
            "purpose": source.purpose,
            "estimated_start": source.estimated_start,
            "estimated_end": source.estimated_end,
            "owner": actor,
            "participant_ids": [],
            "formula_columns": source_record.formula_columns,
            "formula_rows": source_record.formula_rows,
            "extra_tables": source_record.extra_tables,
            "process_text": source_record.process_text,
            "extra_processes": source_record.extra_processes,
            "result_text": "",
        },
    )


@transaction.atomic
def transition_experiment(
    *,
    experiment_id,
    actor: User,
    target_status: str,
    expected_version: int,
) -> Experiment:
    """执行实验状态迁移。

    Args:
        experiment_id: 实验主键。
        actor: 当前操作用户。
        target_status: 目标状态。
        expected_version: 客户端资源版本。

    Returns:
        更新后的实验。
    """
    experiment = Experiment.objects.select_for_update().get(
        id=experiment_id,
    )
    if not actor.has_permission_code("experiment.execute"):
        raise PermissionDenied("无实验执行权限")
    if not experiments_for_user(actor).filter(id=experiment.id).exists():
        raise PermissionDenied("只能执行本人可见的实验")
    if experiment.version != expected_version:
        raise ResourceVersionConflict()
    transitions = {
        ExperimentStatus.NOT_STARTED: ExperimentStatus.IN_PROGRESS,
        ExperimentStatus.IN_PROGRESS: ExperimentStatus.COMPLETED,
    }
    if transitions.get(experiment.status) != target_status:
        raise BusinessRuleConflict("当前实验状态不允许执行目标迁移")
    if target_status == ExperimentStatus.COMPLETED:
        record = ExperimentRecord.objects.filter(experiment=experiment).first()
        if not record or not record.result_text.strip():
            raise ValidationError({"result_text": ["完成实验前必须填写实验结果"]})
    now = timezone.now()
    experiment.status = target_status
    experiment.phase = "实验执行" if target_status == ExperimentStatus.IN_PROGRESS else "检测分析"
    if target_status == ExperimentStatus.IN_PROGRESS:
        experiment.started_at = now
    else:
        experiment.completed_at = now
    experiment.updated_by = actor
    experiment.version += 1
    experiment.save()
    logger.info(
        "迁移实验状态",
        extra={
            "experiment_no": experiment.experiment_no,
            "target_status": target_status,
            "actor_id": str(actor.id),
        },
    )
    record_business_operation(
        actor=actor,
        domain="experiment",
        object_id=experiment.id,
        object_no=experiment.experiment_no,
        action_type="experiment_status_changed",
        description=(
            f"开始实验“{experiment.name}”"
            if target_status == ExperimentStatus.IN_PROGRESS
            else f"完成实验“{experiment.name}”"
        ),
        changes={"target_status": target_status},
        organization_id=experiment.organization_id,
    )
    return experiment
