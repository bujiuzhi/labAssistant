"""新增实验记录真实附件表。"""

import django.db.models.deletion
import uuid

from django.conf import settings
from django.db import migrations, models

import apps.experiments.models


class Migration(migrations.Migration):
    """新增实验记录真实附件表。"""

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("identity", "0001_initial"),
        ("experiments", "0001_initial"),
    ]

    operations = [
        migrations.CreateModel(
            name="ExperimentAttachment",
            fields=[
                ("id", models.UUIDField(db_comment="主键", default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("created_at", models.DateTimeField(auto_now_add=True, db_comment="创建时间")),
                ("updated_at", models.DateTimeField(auto_now=True, db_comment="最后更新时间")),
                ("kind", models.CharField(choices=[("process_image", "过程图片"), ("result_file", "结果附件")], db_comment="附件用途", max_length=24)),
                ("name", models.CharField(db_comment="原始文件名", max_length=255)),
                ("file", models.FileField(db_comment="附件存储路径", max_length=500, upload_to=apps.experiments.models.experiment_attachment_upload_to)),
                ("mime_type", models.CharField(blank=True, db_comment="MIME 类型", max_length=120)),
                ("file_size", models.PositiveBigIntegerField(db_comment="文件字节数")),
                ("experiment", models.ForeignKey(db_comment="关联实验", on_delete=django.db.models.deletion.CASCADE, related_name="attachments", to="experiments.experiment")),
                ("organization", models.ForeignKey(db_comment="所属组织", on_delete=django.db.models.deletion.CASCADE, related_name="experiment_attachments", to="identity.organization")),
                ("uploaded_by", models.ForeignKey(db_comment="上传用户", on_delete=django.db.models.deletion.PROTECT, related_name="uploaded_experiment_attachments", to=settings.AUTH_USER_MODEL)),
            ],
            options={"db_table": "experiment_attachment", "db_table_comment": "实验记录附件"},
        ),
        migrations.AddIndex(
            model_name="experimentattachment",
            index=models.Index(fields=["organization", "experiment", "kind", "-created_at"], name="idx_exp_attachment_scope"),
        ),
    ]
