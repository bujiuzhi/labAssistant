"""业务模型通用基类。"""

import uuid

from django.conf import settings
from django.db import models


class TimeStampedModel(models.Model):
    """提供 UUID 主键和创建、更新时间。"""

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    created_at = models.DateTimeField(auto_now_add=True, db_comment="创建时间")
    updated_at = models.DateTimeField(auto_now=True, db_comment="最后更新时间")

    class Meta:
        """声明抽象模型。"""

        abstract = True


class IdempotencyRequest(models.Model):
    """创建和状态迁移请求的幂等记录。"""

    class Status(models.TextChoices):
        """幂等请求状态。"""

        PROCESSING = "processing", "处理中"
        COMPLETED = "completed", "已完成"
        FAILED = "failed", "失败"

    id = models.UUIDField(
        primary_key=True,
        default=uuid.uuid4,
        editable=False,
        db_comment="主键",
    )
    organization_id = models.UUIDField(db_comment="所属组织")
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="idempotency_requests",
        db_comment="请求用户",
    )
    idempotency_key = models.CharField(max_length=128, db_comment="客户端幂等键")
    route_key = models.CharField(max_length=200, db_comment="稳定路由和动作标识")
    request_hash = models.CharField(max_length=64, db_comment="规范化请求摘要")
    status = models.CharField(
        max_length=16,
        choices=Status.choices,
        default=Status.PROCESSING,
        db_comment="幂等请求状态",
    )
    response_status = models.PositiveSmallIntegerField(null=True, db_comment="首次响应状态码")
    response_body = models.JSONField(null=True, db_comment="可安全重放的脱敏响应")
    created_at = models.DateTimeField(auto_now_add=True, db_comment="创建时间")
    expires_at = models.DateTimeField(db_comment="过期时间")

    class Meta:
        """幂等请求表配置。"""

        db_table = "idempotency_request"
        db_table_comment = "幂等请求"
        constraints = [
            models.UniqueConstraint(
                fields=["organization_id", "user", "route_key", "idempotency_key"],
                name="uk_idempotency_request_scope_key",
            )
        ]
        indexes = [
            models.Index(fields=["expires_at"], name="idx_idempotency_expires")
        ]

    def __str__(self) -> str:
        """返回幂等请求路由与键。"""
        return f"{self.route_key} {self.idempotency_key}"
