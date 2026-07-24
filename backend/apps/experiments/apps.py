"""电子实验记录本应用配置。"""

from django.apps import AppConfig


class ExperimentsConfig(AppConfig):
    """电子实验记录本应用配置。"""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.experiments"
    verbose_name = "电子实验记录本"
