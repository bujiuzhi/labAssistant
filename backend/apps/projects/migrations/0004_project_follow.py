"""新增项目关注表。"""

import django.db.models.deletion
import uuid

from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):
    """新增项目关注表。"""

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("identity", "0001_initial"),
        ("projects", "0003_project_document"),
    ]

    operations = [
        migrations.CreateModel(
            name="ProjectFollow",
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
                ("created_at", models.DateTimeField(auto_now_add=True, db_comment="关注时间")),
                (
                    "organization",
                    models.ForeignKey(
                        db_comment="所属组织",
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="project_follows",
                        to="identity.organization",
                    ),
                ),
                (
                    "project",
                    models.ForeignKey(
                        db_comment="关注项目",
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="follows",
                        to="projects.project",
                    ),
                ),
                (
                    "user",
                    models.ForeignKey(
                        db_comment="关注用户",
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="followed_projects",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={"db_table": "project_follow", "db_table_comment": "项目关注"},
        ),
        migrations.AddConstraint(
            model_name="projectfollow",
            constraint=models.UniqueConstraint(fields=("project", "user"), name="uk_project_follow_project_user"),
        ),
        migrations.AddIndex(
            model_name="projectfollow",
            index=models.Index(fields=["organization", "user", "-created_at"], name="idx_project_follow_org_user"),
        ),
    ]
