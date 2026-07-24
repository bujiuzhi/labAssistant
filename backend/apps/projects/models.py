"""项目、成员与业务编号模型。"""

import uuid

from django.conf import settings
from django.db import models

from apps.common.models import TimeStampedModel
from apps.identity.models import Organization


class ProjectStatus(models.TextChoices):
    """项目状态。"""

    DRAFT = "draft", "草稿"
    ACTIVE = "active", "进行中"
    SUSPENDED = "suspended", "已暂停"
    COMPLETED = "completed", "已完成"
    ARCHIVED = "archived", "已归档"


class ProjectMemberRole(models.TextChoices):
    """项目成员角色。"""

    OWNER = "owner", "负责人"
    RESEARCHER = "researcher", "研究人员"
    INSPECTOR = "inspector", "检测人员"
    VIEWER = "viewer", "只读成员"


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
