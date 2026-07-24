"""项目接口路由。"""

from django.urls import path

from .views import ProjectDetailView, ProjectListCreateView

urlpatterns = [
    path("projects", ProjectListCreateView.as_view(), name="project-list-create"),
    path(
        "projects/<str:project_key>",
        ProjectDetailView.as_view(),
        name="project-detail",
    ),
]
