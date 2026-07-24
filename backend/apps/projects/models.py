"""项目、成员、文档与业务编号模型。"""

from __future__ import annotations

import uuid
from pathlib import Path

from django.conf import settings
from django.db import models

from apps.common.models import TimeStampedModel
from apps.identity.models import Organization


class ProjectStatus(models.TextChoices):
    """项目状态。"""

    DRAFT = "draft", "草稿"
    NOT_STARTED = "not_started", "待开始"
    ACTIVE = "active", "进行中"
    AT_RISK = "at_risk", "有风险"
    SUSPENDED = "suspended", "已暂停"
    COMPLETED = "completed", "已完成"
    ARCHIVED = "archived", "已归档"


class ProjectMemberRole(models.TextChoices):
    """项目成员角色。"""

    OWNER = "owner", "负责人"
    RESEARCHER = "researcher", "研究人员"
    INSPECTOR = "inspector", "检测人员"
    VIEWER = "viewer", "只读成员"


class ProjectDocumentCategory(models.TextChoices):
    """项目文档分类。"""

    PROJECT_PLAN = "project_plan", "项目方案"
    LITERATURE = "literature", "文献资料"
    EXPERIMENT_PLAN = "experiment_plan", "实验方案"
    STAGE_REPORT = "stage_report", "阶段报告"
    MEETING_MINUTES = "meeting_minutes", "会议纪要"
    OTHER = "other", "其他"


def project_document_upload_to(instance: ProjectDocument, filename: str) -> str:
    """生成组织和项目隔离的文档存储路径。

    Args:
        instance: 项目文档。
        filename: 客户端原始文件名。

    Returns:
        带业务域前缀的相对存储路径。
    """
    suffix = Path(filename).suffix.lower()
    return (
        f"project-documents/{instance.organization_id}/{instance.project_id}/"
        f"{uuid.uuid4().hex}{suffix}"
    )


class BusinessNumberSequence(models.Model):
    """按组织、业务类型和周期生成业务编号。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    organization = models.ForeignKey(
        Organization,
        on_delete=models.CASCADE,
        related_name="business_number_sequences",
        db_comment="所属组织",
    )
    business_type = models.CharField(max_length=32, db_comment="业务类型")
    period_key = models.CharField(max_length=16, db_comment="周期键")
    current_value = models.BigIntegerField(default=0, db_comment="当前已分配序号")
    updated_at = models.DateTimeField(auto_now=True, db_comment="更新时间")

    class Meta:
        """业务编号序列表配置。"""

        db_table = "business_number_sequence"
        db_table_comment = "业务编号序列"
        constraints = [
            models.UniqueConstraint(
                fields=["organization", "business_type", "period_key"],
                name="uk_business_number_sequence_scope",
            )
        ]

    def __str__(self) -> str:
        """返回序列业务范围。"""
        return f"{self.organization_id} {self.business_type} {self.period_key}"


class Project(TimeStampedModel):
    """材料研发项目。"""

    organization = models.ForeignKey(
        Organization,
        on_delete=models.PROTECT,
        related_name="projects",
        db_comment="所属组织",
    )
    project_no = models.CharField(max_length=32, db_comment="项目编号")
    name = models.CharField(max_length=200, db_comment="项目名称")
    project_type_code = models.CharField(max_length=64, db_comment="项目类型字典代码")
    description = models.TextField(blank=True, db_comment="项目目标和范围说明")
    current_stage = models.CharField(
        max_length=64,
        default="方案设计",
        db_comment="当前研发阶段",
    )
    progress_percent = models.PositiveSmallIntegerField(
        default=0,
        db_comment="项目进度百分比",
    )
    document_count = models.PositiveIntegerField(default=0, db_comment="关联文档数量")
    experiment_count = models.PositiveIntegerField(default=0, db_comment="关联实验数量")
    data_resource_count = models.PositiveIntegerField(
        default=0,
        db_comment="关联数据资源数量",
    )
    objectives = models.JSONField(default=list, blank=True, db_comment="研发总体目标列表")
    milestones = models.JSONField(default=list, blank=True, db_comment="重点里程碑列表")
    status = models.CharField(
        max_length=24,
        choices=ProjectStatus.choices,
        default=ProjectStatus.DRAFT,
        db_comment="项目状态",
    )
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="owned_projects",
        db_comment="项目负责人",
    )
    planned_start_date = models.DateField(null=True, blank=True, db_comment="计划开始日期")
    planned_end_date = models.DateField(null=True, blank=True, db_comment="计划结束日期")
    actual_end_at = models.DateTimeField(null=True, blank=True, db_comment="实际完成时间")
    archived_at = models.DateTimeField(null=True, blank=True, db_comment="归档时间")
    version = models.PositiveIntegerField(default=1, db_comment="乐观锁版本")
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="created_projects",
        db_comment="创建用户",
    )
    updated_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="updated_projects",
        db_comment="最后更新用户",
    )

    class Meta:
        """项目表配置。"""

        db_table = "project"
        db_table_comment = "项目"
        constraints = [
            models.UniqueConstraint(
                fields=["organization", "project_no"],
                name="uk_project_org_project_no",
            ),
            models.CheckConstraint(
                condition=(
                    models.Q(planned_end_date__isnull=True)
                    | models.Q(planned_start_date__isnull=True)
                    | models.Q(planned_end_date__gte=models.F("planned_start_date"))
                ),
                name="ck_project_planned_date_order",
            ),
            models.CheckConstraint(
                condition=models.Q(progress_percent__gte=0)
                & models.Q(progress_percent__lte=100),
                name="ck_project_progress_percent_range",
            ),
        ]
        indexes = [
            models.Index(
                fields=["organization", "status", "-updated_at"],
                name="idx_project_org_status_updated",
            ),
            models.Index(
                fields=["organization", "owner", "status"],
                name="idx_project_owner_status",
            ),
        ]

    def __str__(self) -> str:
        """返回项目编号和名称。"""
        return f"{self.project_no} {self.name}"


class ProjectMember(models.Model):
    """项目成员关系。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    organization = models.ForeignKey(
        Organization,
        on_delete=models.CASCADE,
        related_name="project_members",
        db_comment="所属组织",
    )
    project = models.ForeignKey(
        Project,
        on_delete=models.CASCADE,
        related_name="members",
        db_comment="项目",
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="project_memberships",
        db_comment="成员",
    )
    member_role = models.CharField(
        max_length=24,
        choices=ProjectMemberRole.choices,
        db_comment="项目成员角色",
    )
    joined_at = models.DateTimeField(auto_now_add=True, db_comment="加入时间")
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="created_project_memberships",
        db_comment="操作用户",
    )

    class Meta:
        """项目成员表配置。"""

        db_table = "project_member"
        db_table_comment = "项目成员"
        constraints = [
            models.UniqueConstraint(
                fields=["project", "user"],
                name="uk_project_member_project_user",
            )
        ]
        indexes = [
            models.Index(
                fields=["organization", "user", "project"],
                name="idx_project_member_user",
            )
        ]

    def __str__(self) -> str:
        """返回项目与成员关系。"""
        return f"{self.project} - {self.user}"


class ProjectDocument(TimeStampedModel):
    """项目文档及其可追溯元数据。"""

    organization = models.ForeignKey(
        Organization,
        on_delete=models.PROTECT,
        related_name="project_documents",
        db_comment="所属组织",
    )
    project = models.ForeignKey(
        Project,
        on_delete=models.CASCADE,
        related_name="documents",
        db_comment="关联项目",
    )
    name = models.CharField(max_length=255, db_comment="原始文档名称")
    file = models.FileField(
        upload_to=project_document_upload_to,
        max_length=500,
        db_comment="文档存储路径",
    )
    extension = models.CharField(max_length=20, db_comment="小写文件扩展名")
    mime_type = models.CharField(max_length=150, blank=True, db_comment="MIME 类型")
    file_size = models.PositiveBigIntegerField(default=0, db_comment="文件大小字节数")
    category = models.CharField(
        max_length=32,
        choices=ProjectDocumentCategory.choices,
        db_comment="文档分类",
    )
    related_content = models.CharField(
        max_length=200,
        default="项目整体",
        db_comment="关联项目内容或实验编号",
    )
    version_label = models.CharField(
        max_length=32,
        default="V1.0",
        db_comment="业务版本标签",
    )
    uploaded_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="uploaded_project_documents",
        db_comment="上传用户",
    )
    updated_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="updated_project_documents",
        db_comment="最后更新用户",
    )

    class Meta:
        """项目文档表配置。"""

        db_table = "project_document"
        db_table_comment = "项目文档"
        indexes = [
            models.Index(
                fields=["project", "category", "-updated_at"],
                name="idx_project_doc_category",
            ),
            models.Index(
                fields=["organization", "extension", "-updated_at"],
                name="idx_project_doc_extension",
            ),
        ]

    def __str__(self) -> str:
        """返回项目编号和文档名称。"""
        return f"{self.project.project_no} {self.name}"
