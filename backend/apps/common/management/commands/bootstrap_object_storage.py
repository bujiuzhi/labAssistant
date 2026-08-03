"""初始化并检查应用对象存储桶。"""

from botocore.exceptions import BotoCoreError, ClientError
from django.core.exceptions import ImproperlyConfigured
from django.core.management.base import BaseCommand, CommandError

from apps.common.object_storage import ensure_object_storage_bucket


class Command(BaseCommand):
    """创建缺失的对象存储桶并验证访问权限。"""

    help = "初始化并检查材料实验助手的私有对象存储桶"

    def handle(self, *args, **options) -> None:
        """执行对象存储桶初始化。

        Args:
            args: Django 命令位置参数。
            options: Django 命令选项。

        Raises:
            CommandError: 对象存储不可连接、无权限或配置错误。
        """
        del args, options
        try:
            created = ensure_object_storage_bucket()
        except (
            BotoCoreError,
            ClientError,
            ImproperlyConfigured,
            OSError,
        ) as error:
            raise CommandError(f"对象存储桶初始化失败：{error}") from error
        if created:
            self.stdout.write(self.style.SUCCESS("对象存储桶已创建并可访问"))
        else:
            self.stdout.write(self.style.SUCCESS("对象存储桶已存在并可访问"))
