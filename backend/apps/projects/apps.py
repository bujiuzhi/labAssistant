"""项目应用配置。"""

from django.apps import AppConfig


class ProjectsConfig(AppConfig):
    """项目管理应用。"""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.projects"
    verbose_name = "项目管理"
