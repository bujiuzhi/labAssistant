"""项目接口路由。"""

from django.urls import path

from .views import (
    ProjectDetailView,
    ProjectDocumentContentView,
    ProjectDocumentListCreateView,
    ProjectListCreateView,
)

urlpatterns = [
    path("projects", ProjectListCreateView.as_view(), name="project-list-create"),
    path(
        "projects/<str:project_key>/documents",
        ProjectDocumentListCreateView.as_view(),
        name="project-document-list-create",
    ),
    path(
        "projects/<str:project_key>/documents/<uuid:document_id>/content",
        ProjectDocumentContentView.as_view(),
        name="project-document-content",
    ),
    path(
        "projects/<str:project_key>",
        ProjectDetailView.as_view(),
        name="project-detail",
    ),
]
