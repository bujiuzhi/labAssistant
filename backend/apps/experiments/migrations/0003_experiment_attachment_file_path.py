"""扩展实验附件存储路径长度。"""

import apps.experiments.models

from django.db import migrations, models


class Migration(migrations.Migration):
    """扩展实验附件存储路径长度。"""

    dependencies = [("experiments", "0002_experiment_attachment")]

    operations = [
        migrations.AlterField(
            model_name="experimentattachment",
            name="file",
            field=models.FileField(
                db_comment="附件存储路径",
                max_length=500,
                upload_to=apps.experiments.models.experiment_attachment_upload_to,
            ),
        )
    ]
