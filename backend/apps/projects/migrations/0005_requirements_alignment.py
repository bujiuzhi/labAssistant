"""统一项目类型、默认状态和计划时间精度。"""

from django.db import migrations, models


def normalize_project_data(apps, schema_editor) -> None:
    """将历史项目归一到需求文档规定的两类项目。"""
    Project = apps.get_model("projects", "Project")
    Project.objects.exclude(project_type_code="环氧树脂").update(
        project_type_code="聚酰亚胺",
    )
    Project.objects.filter(status="draft").update(status="not_started")


class Migration(migrations.Migration):
    """执行项目字段和存量数据迁移。"""

    dependencies = [
        ("projects", "0004_project_follow"),
    ]

    operations = [
        migrations.AlterField(
            model_name="project",
            name="planned_start_date",
            field=models.DateTimeField(
                blank=True,
                db_comment="计划开始时间",
                null=True,
            ),
        ),
        migrations.AlterField(
            model_name="project",
            name="planned_end_date",
            field=models.DateTimeField(
                blank=True,
                db_comment="计划结束时间",
                null=True,
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
                default="not_started",
                max_length=24,
            ),
        ),
        migrations.RunPython(normalize_project_data, migrations.RunPython.noop),
    ]
