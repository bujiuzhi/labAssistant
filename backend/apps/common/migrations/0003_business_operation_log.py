"""新增跨业务域操作审计日志。"""

import uuid

from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    """创建业务操作日志表。"""

    dependencies = [
        ("common", "0002_initial"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="BusinessOperationLog",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        db_comment="主键",
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                    ),
                ),
                ("organization_id", models.UUIDField(db_comment="所属组织")),
                ("domain", models.CharField(db_comment="业务域", max_length=32)),
                (
                    "object_id",
                    models.UUIDField(db_comment="业务对象主键"),
                ),
                (
                    "object_no",
                    models.CharField(blank=True, db_comment="业务对象编号", max_length=64),
                ),
                (
                    "action_type",
                    models.CharField(db_comment="操作类型", max_length=64),
                ),
                (
                    "description",
                    models.CharField(db_comment="操作内容说明", max_length=1000),
                ),
                (
                    "changes",
                    models.JSONField(blank=True, db_comment="结构化变更摘要", default=dict),
                ),
                (
                    "created_at",
                    models.DateTimeField(auto_now_add=True, db_comment="操作时间"),
                ),
                (
                    "actor",
                    models.ForeignKey(
                        blank=True,
                        db_comment="操作用户",
                        null=True,
                        on_delete=django.db.models.deletion.SET_NULL,
                        related_name="business_operation_logs",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "db_table": "business_operation_log",
                "db_table_comment": "业务操作日志",
                "indexes": [
                    models.Index(
                        fields=[
                            "organization_id",
                            "domain",
                            "object_id",
                            "-created_at",
                        ],
                        name="idx_operation_log_object",
                    ),
                    models.Index(
                        fields=["organization_id", "actor", "-created_at"],
                        name="idx_operation_log_actor",
                    ),
                ],
            },
        ),
    ]
