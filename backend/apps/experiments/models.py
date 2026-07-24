"""实验计划、参与人员与电子实验记录模型。"""

import uuid
from pathlib import Path

from django.conf import settings
from django.db import models
from django.db.models import Q

from apps.common.models import TimeStampedModel
from apps.identity.models import Organization
from apps.projects.models import Project


def experiment_attachment_upload_to(instance: "ExperimentAttachment", filename: str) -> str:
    """生成实验附件的组织和实验隔离路径。"""
    suffix = Path(filename).suffix.lower()
    return (
        f"experiment-attachments/{instance.organization_id}/{instance.experiment_id}/"
        f"{uuid.uuid4().hex}{suffix}"
    )


class ExperimentStatus(models.TextChoices):
    """实验状态。"""

    NOT_STARTED = "not_started", "未开始"
    IN_PROGRESS = "in_progress", "进行中"
    COMPLETED = "completed", "已完成"


class ExperimentParticipantRole(models.TextChoices):
    """实验参与角色。"""

    OWNER = "owner", "实验员"
    PARTICIPANT = "participant", "参与人员"
    REVIEWER = "reviewer", "复核人员"


class Experiment(TimeStampedModel):
    """实验计划。"""

    organization = models.ForeignKey(
        Organization,
        on_delete=models.PROTECT,
        related_name="experiments",
        db_comment="所属组织",
    )
    project = models.ForeignKey(
        Project,
        on_delete=models.PROTECT,
        related_name="experiments",
        db_comment="关联项目",
    )
    experiment_no = models.CharField(max_length=32, db_comment="实验编号")
    name = models.CharField(max_length=200, db_comment="实验名称")
    experiment_type = models.CharField(max_length=64, db_comment="实验类型")
    phase = models.CharField(max_length=64, default="方案设计", db_comment="实验阶段")
    status = models.CharField(
        max_length=24,
        choices=ExperimentStatus.choices,
        default=ExperimentStatus.NOT_STARTED,
        db_comment="实验状态",
    )
    purpose = models.TextField(blank=True, db_comment="实验目的")
    estimated_start = models.DateTimeField(
        null=True,
        blank=True,
        db_comment="预估开始时间",
    )
    estimated_end = models.DateTimeField(
        null=True,
        blank=True,
        db_comment="预估结束时间",
    )
    started_at = models.DateTimeField(null=True, blank=True, db_comment="实际开始时间")
    completed_at = models.DateTimeField(null=True, blank=True, db_comment="实际完成时间")
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="owned_experiments",
        db_comment="实验负责人",
    )
    version = models.PositiveIntegerField(default=1, db_comment="乐观锁版本")
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="created_experiments",
        db_comment="创建用户",
    )
    updated_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="updated_experiments",
        db_comment="最后更新用户",
    )

    class Meta:
        """实验计划表配置。"""

        db_table = "experiment"
        db_table_comment = "实验计划"
        constraints = [
            models.UniqueConstraint(
                fields=["organization", "experiment_no"],
                name="uk_experiment_org_no",
            ),
            models.CheckConstraint(
                condition=(
                    Q(estimated_end__isnull=True)
                    | Q(estimated_start__isnull=True)
                    | Q(estimated_end__gte=models.F("estimated_start"))
                ),
                name="ck_experiment_estimated_date_order",
            ),
        ]
        indexes = [
            models.Index(
                fields=["organization", "status", "-updated_at"],
                name="idx_experiment_org_status",
            ),
            models.Index(
                fields=["project", "status", "-updated_at"],
                name="idx_experiment_project_status",
            ),
            models.Index(
                fields=["organization", "owner", "status"],
                name="idx_experiment_owner_status",
            ),
        ]

    def __str__(self) -> str:
        """返回实验编号和名称。"""
        return f"{self.experiment_no} {self.name}"


class ExperimentRecord(TimeStampedModel):
    """电子实验记录正文。"""

    experiment = models.OneToOneField(
        Experiment,
        on_delete=models.CASCADE,
        related_name="record",
        db_comment="实验计划",
    )
    formula_columns = models.JSONField(
        default=list,
        blank=True,
        db_comment="配方表列定义",
    )
    formula_rows = models.JSONField(
        default=list,
        blank=True,
        db_comment="配方表行数据",
    )
    extra_tables = models.JSONField(
        default=list,
        blank=True,
        db_comment="自定义附加表格",
    )
    process_text = models.TextField(blank=True, db_comment="实验过程文字记录")
    extra_processes = models.JSONField(
        default=list,
        blank=True,
        db_comment="自定义实验过程模块",
    )
    process_images = models.JSONField(
        default=list,
        blank=True,
        db_comment="过程图片元数据和内容",
    )
    result_text = models.TextField(blank=True, db_comment="实验结果")
    result_files = models.JSONField(
        default=list,
        blank=True,
        db_comment="结果附件元数据",
    )

    class Meta:
        """电子实验记录表配置。"""

        db_table = "experiment_record"
        db_table_comment = "电子实验记录"

    def __str__(self) -> str:
        """返回关联实验编号。"""
        return f"{self.experiment.experiment_no} 实验记录"


class ExperimentAttachmentKind(models.TextChoices):
    """实验附件用途。"""

    PROCESS_IMAGE = "process_image", "过程图片"
    RESULT_FILE = "result_file", "结果附件"


class ExperimentAttachment(TimeStampedModel):
    """电子实验记录的真实文件附件。"""

    organization = models.ForeignKey(
        Organization,
        on_delete=models.CASCADE,
        related_name="experiment_attachments",
        db_comment="所属组织",
    )
    experiment = models.ForeignKey(
        Experiment,
        on_delete=models.CASCADE,
        related_name="attachments",
        db_comment="关联实验",
    )
    kind = models.CharField(
        max_length=24,
        choices=ExperimentAttachmentKind.choices,
        db_comment="附件用途",
    )
    name = models.CharField(max_length=255, db_comment="原始文件名")
    file = models.FileField(
        upload_to=experiment_attachment_upload_to,
        max_length=500,
        db_comment="附件存储路径",
    )
    mime_type = models.CharField(max_length=120, blank=True, db_comment="MIME 类型")
    file_size = models.PositiveBigIntegerField(db_comment="文件字节数")
    uploaded_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="uploaded_experiment_attachments",
        db_comment="上传用户",
    )

    class Meta:
        """实验附件表配置。"""

        db_table = "experiment_attachment"
        db_table_comment = "实验记录附件"
        indexes = [
            models.Index(
                fields=["organization", "experiment", "kind", "-created_at"],
                name="idx_exp_attachment_scope",
            )
        ]

    def __str__(self) -> str:
        """返回实验附件名称。"""
        return f"{self.experiment.experiment_no} {self.name}"


class ExperimentParticipant(models.Model):
    """实验参与人员关系。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    organization = models.ForeignKey(
        Organization,
        on_delete=models.CASCADE,
        related_name="experiment_participants",
        db_comment="所属组织",
    )
    experiment = models.ForeignKey(
        Experiment,
        on_delete=models.CASCADE,
        related_name="participants",
        db_comment="实验计划",
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="experiment_participations",
        db_comment="参与用户",
    )
    participant_role = models.CharField(
        max_length=24,
        choices=ExperimentParticipantRole.choices,
        default=ExperimentParticipantRole.PARTICIPANT,
        db_comment="实验参与角色",
    )
    joined_at = models.DateTimeField(auto_now_add=True, db_comment="加入时间")
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="created_experiment_participants",
        db_comment="操作用户",
    )

    class Meta:
        """实验参与人员表配置。"""

        db_table = "experiment_participant"
        db_table_comment = "实验参与人员"
        constraints = [
            models.UniqueConstraint(
                fields=["experiment", "user"],
                name="uk_experiment_participant_user",
            )
        ]
        indexes = [
            models.Index(
                fields=["organization", "user", "experiment"],
                name="idx_exp_participant_user",
            )
        ]

    def __str__(self) -> str:
        """返回实验与参与用户关系。"""
        return f"{self.experiment} - {self.user}"
