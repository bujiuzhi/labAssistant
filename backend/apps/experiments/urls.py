"""电子实验记录本接口路由。"""

from django.urls import path

from .views import (
    ExperimentAttachmentContentView,
    ExperimentAttachmentListCreateView,
    ExperimentCopyView,
    ExperimentDetailView,
    ExperimentListCreateView,
    ExperimentTransitionView,
)

urlpatterns = [
    path(
        "experiments",
        ExperimentListCreateView.as_view(),
        name="experiment-list-create",
    ),
    path(
        "experiments/<str:experiment_key>",
        ExperimentDetailView.as_view(),
        name="experiment-detail",
    ),
    path(
        "experiments/<str:experiment_key>/copy",
        ExperimentCopyView.as_view(),
        name="experiment-copy",
    ),
    path(
        "experiments/<str:experiment_key>/transition",
        ExperimentTransitionView.as_view(),
        name="experiment-transition",
    ),
    path(
        "experiments/<str:experiment_key>/attachments",
        ExperimentAttachmentListCreateView.as_view(),
        name="experiment-attachment-list-create",
    ),
    path(
        "experiments/<str:experiment_key>/attachments/<uuid:attachment_id>/content",
        ExperimentAttachmentContentView.as_view(),
        name="experiment-attachment-content",
    ),
]
