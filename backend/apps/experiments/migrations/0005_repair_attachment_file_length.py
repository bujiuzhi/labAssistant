"""修复历史 PostgreSQL 数据库中的实验附件路径列长度。"""

from django.db import migrations


def repair_postgresql_file_column(apps, schema_editor) -> None:
    """将历史数据库遗留的 100 字符路径列扩展到模型规定的 500 字符。"""
    if schema_editor.connection.vendor != "postgresql":
        return
    schema_editor.execute(
        "ALTER TABLE experiment_attachment "
        "ALTER COLUMN file TYPE varchar(500)"
    )


class Migration(migrations.Migration):
    """修复曾被修改过迁移状态导致的物理结构漂移。"""

    dependencies = [
        ("experiments", "0004_remove_legacy_attachment_metadata"),
    ]

    operations = [
        migrations.RunPython(
            repair_postgresql_file_column,
            migrations.RunPython.noop,
        ),
    ]
