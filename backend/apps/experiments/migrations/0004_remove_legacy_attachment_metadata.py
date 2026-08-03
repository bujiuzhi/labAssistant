"""删除已由真实附件表替代的 JSON 附件元数据字段。"""

from django.db import migrations


class Migration(migrations.Migration):
    """确保附件仅以实验附件表和文件存储为正式数据源。"""

    dependencies = [
        ("experiments", "0003_experiment_attachment_file_path"),
    ]

    operations = [
        migrations.RemoveField(
            model_name="experimentrecord",
            name="process_images",
        ),
        migrations.RemoveField(
            model_name="experimentrecord",
            name="result_files",
        ),
    ]
