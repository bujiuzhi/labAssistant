"""电子实验记录本列表、详情和状态接口。"""

import uuid

from django.db.models import Q
from django.shortcuts import get_object_or_404
from rest_framework import status
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.common.pagination import EnvelopePageNumberPagination

from .models import Experiment, ExperimentStatus
from .selectors import experiments_for_user
from .serializers import (
    ExperimentCreateSerializer,
    ExperimentSerializer,
    ExperimentStatusSerializer,
    ExperimentUpdateSerializer,
)
from .services import (
    copy_experiment,
    create_experiment,
    transition_experiment,
    update_experiment,
)


def _parse_if_match(request) -> int:
    """解析 `If-Match` 资源版本。

    Args:
        request: 当前 DRF 请求。

    Returns:
        整数资源版本。
    """
    raw_value = request.headers.get("If-Match")
    if not raw_value:
        raise ValidationError({"if_match": ["更新请求必须提供 If-Match"]})
    normalized = raw_value.strip('"')
    if not normalized.isdigit() or int(normalized) < 1:
        raise ValidationError({"if_match": ["If-Match 必须是带引号的正整数版本"]})
    return int(normalized)


def _experiment_response(
    experiment: Experiment,
    request,
    response_status: int = 200,
) -> Response:
    """生成带 ETag 的实验响应。

    Args:
        experiment: 实验对象。
        request: 当前请求。
        response_status: HTTP 状态码。

    Returns:
        实验响应。
    """
    experiment = experiments_for_user(request.user).get(id=experiment.id)
    response = Response(
        {
            "data": ExperimentSerializer(experiment).data,
            "request_id": getattr(request, "request_id", None),
        },
        status=response_status,
    )
    response["ETag"] = f'"{experiment.version}"'
    return response


class ExperimentListCreateView(APIView):
    """实验列表和创建。"""

    def get(self, request) -> Response:
        """查询当前用户可见实验。

        Args:
            request: 当前请求。

        Returns:
            实验分页列表。
        """
        if not request.user.has_permission_code("experiment.view"):
            raise PermissionDenied("无实验查看权限")
        queryset = experiments_for_user(request.user)
        search = request.query_params.get("search", "").strip()
        if search:
            queryset = queryset.filter(
                Q(experiment_no__icontains=search)
                | Q(name__icontains=search)
                | Q(project__name__icontains=search)
            )
        experiment_status = request.query_params.get("status")
        if experiment_status:
            if experiment_status not in ExperimentStatus.values:
                raise ValidationError({"status": ["未知实验状态"]})
            queryset = queryset.filter(status=experiment_status)
        project_id = request.query_params.get("project_id")
        if project_id:
            queryset = queryset.filter(project_id=project_id)
        queryset = queryset.order_by("-updated_at", "-experiment_no")
        paginator = EnvelopePageNumberPagination()
        page = paginator.paginate_queryset(queryset, request, view=self)
        return paginator.get_paginated_response(
            ExperimentSerializer(page, many=True).data
        )

    def post(self, request) -> Response:
        """创建实验计划。

        Args:
            request: 当前请求。

        Returns:
            新建实验。
        """
        serializer = ExperimentCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        experiment = create_experiment(
            actor=request.user,
            validated_data=serializer.validated_data,
        )
        return _experiment_response(
            experiment,
            request,
            status.HTTP_201_CREATED,
        )


class ExperimentObjectMixin:
    """实验对象范围查询。"""

    def get_object(self, request, experiment_key: str) -> Experiment:
        """获取当前用户可见实验。

        Args:
            request: 当前请求。
            experiment_key: 实验 UUID 或业务编号。

        Returns:
            实验对象。
        """
        filters = Q(experiment_no=experiment_key)
        try:
            filters |= Q(id=uuid.UUID(experiment_key))
        except ValueError:
            pass
        return get_object_or_404(experiments_for_user(request.user), filters)


class ExperimentDetailView(ExperimentObjectMixin, APIView):
    """实验详情和记录更新。"""

    def get(self, request, experiment_key: str) -> Response:
        """获取实验详情。

        Args:
            request: 当前请求。
            experiment_key: 实验 UUID 或业务编号。

        Returns:
            实验详情。
        """
        if not request.user.has_permission_code("experiment.view"):
            raise PermissionDenied("无实验查看权限")
        return _experiment_response(self.get_object(request, experiment_key), request)

    def patch(self, request, experiment_key: str) -> Response:
        """更新实验计划和电子记录。

        Args:
            request: 当前请求。
            experiment_key: 实验 UUID 或业务编号。

        Returns:
            更新后的实验。
        """
        current = self.get_object(request, experiment_key)
        serializer = ExperimentUpdateSerializer(
            data=request.data,
            context={"experiment": current},
        )
        serializer.is_valid(raise_exception=True)
        experiment = update_experiment(
            experiment_id=current.id,
            actor=request.user,
            validated_data=serializer.validated_data,
            expected_version=_parse_if_match(request),
        )
        return _experiment_response(experiment, request)


class ExperimentCopyView(ExperimentObjectMixin, APIView):
    """复制实验计划。"""

    def post(self, request, experiment_key: str) -> Response:
        """复制指定实验计划。

        Args:
            request: 当前请求。
            experiment_key: 源实验 UUID 或业务编号。

        Returns:
            新建副本。
        """
        experiment = copy_experiment(
            source=self.get_object(request, experiment_key),
            actor=request.user,
        )
        return _experiment_response(
            experiment,
            request,
            status.HTTP_201_CREATED,
        )


class ExperimentTransitionView(ExperimentObjectMixin, APIView):
    """实验状态迁移。"""

    def post(self, request, experiment_key: str) -> Response:
        """开始或完成实验。

        Args:
            request: 当前请求。
            experiment_key: 实验 UUID 或业务编号。

        Returns:
            状态迁移后的实验。
        """
        current = self.get_object(request, experiment_key)
        serializer = ExperimentStatusSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        experiment = transition_experiment(
            experiment_id=current.id,
            actor=request.user,
            target_status=serializer.validated_data["target_status"],
            expected_version=_parse_if_match(request),
        )
        return _experiment_response(experiment, request)
