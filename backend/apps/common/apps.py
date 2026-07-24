"""通用应用配置。"""

from django.apps import AppConfig


class CommonConfig(AppConfig):
    """通用基础能力应用。"""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.common"
    verbose_name = "通用基础能力"
