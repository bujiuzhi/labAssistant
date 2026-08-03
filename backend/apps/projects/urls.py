"""项目接口路由。"""

from django.urls import path

from .views import (
    DashboardSummaryView,
    ProjectArchiveView,
    ProjectDetailView,
    ProjectDocumentContentView,
    ProjectDocumentListCreateView,
    ProjectDocumentPreviewView,
    ProjectFollowView,
    ProjectListCreateView,
    ProjectOperationLogListView,
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
        "projects/<str:project_key>/documents/<uuid:document_id>/preview",
        ProjectDocumentPreviewView.as_view(),
        name="project-document-preview",
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
    path(
        "projects/<str:project_key>/archive",
        ProjectArchiveView.as_view(),
        name="project-archive",
    ),
    path(
        "projects/<str:project_key>/operation-logs",
        ProjectOperationLogListView.as_view(),
        name="project-operation-log-list",
    ),
]
