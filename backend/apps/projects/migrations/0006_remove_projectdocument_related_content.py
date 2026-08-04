"""移除项目文档关联内容字段。"""

from django.db import migrations


class Migration(migrations.Migration):
    """移除不再使用的文档关联内容。"""

    dependencies = [
        ("projects", "0005_requirements_alignment"),
    ]

    operations = [
        migrations.RemoveField(
            model_name="projectdocument",
            name="related_content",
        ),
    ]
