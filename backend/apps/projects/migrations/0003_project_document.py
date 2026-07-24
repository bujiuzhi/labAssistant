"""新增项目文档及可追溯元数据表。"""

import django.db.models.deletion
import uuid
from django.conf import settings
from django.db import migrations, models

import apps.projects.models


class Migration(migrations.Migration):
    """项目文档迁移。"""

    dependencies = [
        ("projects", "0002_project_prototype_fields"),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name="ProjectDocument",
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
                (
                    "created_at",
                    models.DateTimeField(auto_now_add=True, db_comment="创建时间"),
                ),
                (
                    "updated_at",
                    models.DateTimeField(auto_now=True, db_comment="最后更新时间"),
                ),
                ("name", models.CharField(db_comment="原始文档名称", max_length=255)),
                (
                    "file",
                    models.FileField(
                        db_comment="文档存储路径",
                        max_length=500,
                        upload_to=apps.projects.models.project_document_upload_to,
                    ),
                ),
                (
                    "extension",
                    models.CharField(db_comment="小写文件扩展名", max_length=20),
                ),
                (
                    "mime_type",
                    models.CharField(blank=True, db_comment="MIME 类型", max_length=150),
                ),
                (
                    "file_size",
                    models.PositiveBigIntegerField(
                        db_comment="文件大小字节数",
                        default=0,
                    ),
                ),
                (
                    "category",
                    models.CharField(
                        choices=[
                            ("project_plan", "项目方案"),
                            ("literature", "文献资料"),
                            ("experiment_plan", "实验方案"),
                            ("stage_report", "阶段报告"),
                            ("meeting_minutes", "会议纪要"),
                            ("other", "其他"),
                        ],
                        db_comment="文档分类",
                        max_length=32,
                    ),
                ),
                (
                    "related_content",
                    models.CharField(
                        db_comment="关联项目内容或实验编号",
                        default="项目整体",
                        max_length=200,
                    ),
                ),
                (
                    "version_label",
                    models.CharField(
                        db_comment="业务版本标签",
                        default="V1.0",
                        max_length=32,
                    ),
                ),
                (
                    "organization",
                    models.ForeignKey(
                        db_comment="所属组织",
                        on_delete=django.db.models.deletion.PROTECT,
                        related_name="project_documents",
                        to="identity.organization",
                    ),
                ),
                (
                    "project",
                    models.ForeignKey(
                        db_comment="关联项目",
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="documents",
                        to="projects.project",
                    ),
                ),
                (
                    "uploaded_by",
                    models.ForeignKey(
                        db_comment="上传用户",
                        on_delete=django.db.models.deletion.PROTECT,
                        related_name="uploaded_project_documents",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
                (
                    "updated_by",
                    models.ForeignKey(
                        blank=True,
                        db_comment="最后更新用户",
                        null=True,
                        on_delete=django.db.models.deletion.SET_NULL,
                        related_name="updated_project_documents",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "db_table": "project_document",
                "db_table_comment": "项目文档",
            },
        ),
        migrations.AddIndex(
            model_name="projectdocument",
            index=models.Index(
                fields=["project", "category", "-updated_at"],
                name="idx_project_doc_category",
            ),
        ),
        migrations.AddIndex(
            model_name="projectdocument",
            index=models.Index(
                fields=["organization", "extension", "-updated_at"],
                name="idx_project_doc_extension",
            ),
        ),
    ]
