"""补齐原型项目阶段、进度、计数、目标和里程碑字段。"""

from django.db import migrations, models


class Migration(migrations.Migration):
    """项目原型字段迁移。"""

    dependencies = [
        ("projects", "0001_initial"),
    ]

    operations = [
        migrations.AddField(
            model_name="project",
            name="current_stage",
            field=models.CharField(
                db_comment="当前研发阶段",
                default="方案设计",
                max_length=64,
            ),
        ),
        migrations.AddField(
            model_name="project",
            name="progress_percent",
            field=models.PositiveSmallIntegerField(
                db_comment="项目进度百分比",
                default=0,
            ),
        ),
        migrations.AddField(
            model_name="project",
            name="document_count",
            field=models.PositiveIntegerField(db_comment="关联文档数量", default=0),
        ),
        migrations.AddField(
            model_name="project",
            name="experiment_count",
            field=models.PositiveIntegerField(db_comment="关联实验数量", default=0),
        ),
        migrations.AddField(
            model_name="project",
            name="data_resource_count",
            field=models.PositiveIntegerField(
                db_comment="关联数据资源数量",
                default=0,
            ),
        ),
        migrations.AddField(
            model_name="project",
            name="objectives",
            field=models.JSONField(
                blank=True,
                db_comment="研发总体目标列表",
                default=list,
            ),
        ),
        migrations.AddField(
            model_name="project",
            name="milestones",
            field=models.JSONField(
                blank=True,
                db_comment="重点里程碑列表",
                default=list,
            ),
        ),
        migrations.AlterField(
            model_name="project",
            name="status",
            field=models.CharField(
                choices=[
                    ("draft", "草稿"),
                    ("not_started", "待开始"),
                    ("active", "进行中"),
                    ("at_risk", "有风险"),
                    ("suspended", "已暂停"),
                    ("completed", "已完成"),
                    ("archived", "已归档"),
                ],
                db_comment="项目状态",
                default="draft",
                max_length=24,
            ),
        ),
        migrations.AddConstraint(
            model_name="project",
            constraint=models.CheckConstraint(
                condition=models.Q(
                    ("progress_percent__gte", 0),
                    ("progress_percent__lte", 100),
                ),
                name="ck_project_progress_percent_range",
            ),
        ),
    ]
