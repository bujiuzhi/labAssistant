"""项目 URL 配置。"""

from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/v1/health/", include("apps.common.urls")),
    path("api/v1/auth/", include("apps.identity.urls")),
    path("api/v1/", include("apps.projects.urls")),
    path("api/v1/", include("apps.experiments.urls")),
]
