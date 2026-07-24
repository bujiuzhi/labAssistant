"""身份应用配置。"""

from django.apps import AppConfig


class IdentityConfig(AppConfig):
    """身份、组织与权限应用。"""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.identity"
    verbose_name = "身份与权限"
