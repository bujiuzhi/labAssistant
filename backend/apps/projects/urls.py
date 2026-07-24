"""项目接口路由。"""

from django.urls import path

from .views import (
    DashboardSummaryView,
    ProjectDetailView,
    ProjectDocumentContentView,
    ProjectDocumentListCreateView,
    ProjectFollowView,
    ProjectListCreateView,
)

urlpatterns = [
    path("dashboard", DashboardSummaryView.as_view(), name="dashboard-summary"),
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
    path(
        "projects/<str:project_key>/follow",
        ProjectFollowView.as_view(),
        name="project-follow",
    ),
]
