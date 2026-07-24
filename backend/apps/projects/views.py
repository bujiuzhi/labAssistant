"""项目列表、创建、详情和更新接口。"""

import uuid

from django.db.models import Q
from django.shortcuts import get_object_or_404
from rest_framework import status
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.common.pagination import EnvelopePageNumberPagination

from .models import Project, ProjectStatus
from .selectors import projects_for_user
from .serializers import (
    ProjectCreateSerializer,
    ProjectSerializer,
    ProjectUpdateSerializer,
)
from .services import create_project, update_project


def _parse_if_match(request) -> int:
    """解析 `If-Match` 资源版本。

    Args:
        request: 当前 DRF 请求。

    Returns:
        整数资源版本。

    Raises:
        ValidationError: 请求头缺失或格式错误。
    """
    raw_value = request.headers.get("If-Match")
    if not raw_value:
        raise ValidationError({"if_match": ["更新请求必须提供 If-Match"]})
    normalized = raw_value.strip('"')
    if not normalized.isdigit() or int(normalized) < 1:
        raise ValidationError({"if_match": ["If-Match 必须是带引号的正整数版本"]})
    return int(normalized)


def _project_response(project: Project, request, response_status: int = 200) -> Response:
    """生成带 ETag 的项目响应。

    Args:
        project: 项目对象。
        request: 当前请求。
        response_status: HTTP 状态码。

    Returns:
        项目响应。
    """
    response = Response(
        {
            "data": ProjectSerializer(project).data,
            "request_id": getattr(request, "request_id", None),
        },
        status=response_status,
    )
    response["ETag"] = f'"{project.version}"'
    return response


class ProjectListCreateView(APIView):
    """项目列表和创建。"""

    def get(self, request) -> Response:
        """查询当前用户可见项目。

        Args:
            request: 当前请求。

        Returns:
            项目分页列表。
        """
        queryset = projects_for_user(request.user)
        search = request.query_params.get("search", "").strip()
        if search:
            queryset = queryset.filter(
                Q(project_no__icontains=search) | Q(name__icontains=search)
            )
        project_status = request.query_params.get("status")
        if project_status:
            if project_status not in ProjectStatus.values:
                raise ValidationError({"status": ["未知项目状态"]})
            queryset = queryset.filter(status=project_status)
        owner_id = request.query_params.get("owner_id")
        if owner_id:
            queryset = queryset.filter(owner_id=owner_id)
        ordering = request.query_params.get("ordering", "-updated_at")
        allowed_orderings = {
            "updated_at",
            "-updated_at",
            "project_no",
            "-project_no",
            "planned_end_date",
            "-planned_end_date",
        }
        if ordering not in allowed_orderings:
            raise ValidationError({"ordering": ["不支持的排序字段"]})
        queryset = queryset.order_by(ordering)

        paginator = EnvelopePageNumberPagination()
        page = paginator.paginate_queryset(queryset, request, view=self)
        serializer = ProjectSerializer(page, many=True)
        return paginator.get_paginated_response(serializer.data)

    def post(self, request) -> Response:
        """创建项目。

        Args:
            request: 当前请求。

        Returns:
            新建或幂等重放的项目。
        """
        idempotency_key = request.headers.get("Idempotency-Key", "").strip()
        if len(idempotency_key) < 16 or len(idempotency_key) > 128:
            raise ValidationError(
                {"idempotency_key": ["Idempotency-Key 长度必须为 16–128"]}
            )
        serializer = ProjectCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        project, created = create_project(
            actor=request.user,
            validated_data=serializer.validated_data,
            idempotency_key=idempotency_key,
        )
        response_status = status.HTTP_201_CREATED if created else status.HTTP_200_OK
        return _project_response(project, request, response_status)


class ProjectDetailView(APIView):
    """项目详情和更新。"""

    def get_object(self, request, project_key: str) -> Project:
        """获取当前用户可见项目。

        Args:
            request: 当前请求。
            project_key: 项目 UUID 或业务编号。

        Returns:
            项目对象。
        """
        filters = Q(project_no=project_key)
        try:
            filters |= Q(id=uuid.UUID(project_key))
        except ValueError:
            pass
        return get_object_or_404(projects_for_user(request.user), filters)

    def get(self, request, project_key: str) -> Response:
        """获取项目详情。

        Args:
            request: 当前请求。
            project_key: 项目 UUID 或业务编号。

        Returns:
            项目详情。
        """
        return _project_response(self.get_object(request, project_key), request)

    def patch(self, request, project_key: str) -> Response:
        """部分更新项目。

        Args:
            request: 当前请求。
            project_key: 项目 UUID 或业务编号。

        Returns:
            更新后的项目。
        """
        current_project = self.get_object(request, project_key)
        if not request.user.has_permission_code("project.update"):
            raise PermissionDenied("无项目更新权限")
        serializer = ProjectUpdateSerializer(
            data=request.data,
            context={"project": current_project},
        )
        serializer.is_valid(raise_exception=True)
        project = update_project(
            project_id=current_project.id,
            actor=request.user,
            validated_data=serializer.validated_data,
            expected_version=_parse_if_match(request),
        )
        return _project_response(project, request)
