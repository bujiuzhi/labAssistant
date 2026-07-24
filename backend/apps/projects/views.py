"""项目列表、详情、更新与项目文档接口。"""

import uuid
from datetime import timedelta

from django.db.models import Count, Q
from django.http import FileResponse
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import status
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.common.pagination import EnvelopePageNumberPagination

from .models import Project, ProjectDocument, ProjectDocumentCategory, ProjectStatus
from .selectors import documents_for_project, projects_for_user
from .serializers import (
    ProjectCreateSerializer,
    ProjectDocumentCreateSerializer,
    ProjectDocumentSerializer,
    ProjectSerializer,
    ProjectUpdateSerializer,
)
from .services import create_project, create_project_document, update_project


def _visible_project(request, project_key: str) -> Project:
    """按 UUID 或业务编号获取当前用户可见项目。

    Args:
        request: 当前 DRF 请求。
        project_key: 项目 UUID 或业务编号。

    Returns:
        当前用户可见项目。
    """
    filters = Q(project_no=project_key)
    try:
        filters |= Q(id=uuid.UUID(project_key))
    except ValueError:
        pass
    return get_object_or_404(projects_for_user(request.user), filters)


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
        return _visible_project(request, project_key)

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


class ProjectDocumentListCreateView(APIView):
    """项目文档列表和上传。"""

    def get(self, request, project_key: str) -> Response:
        """查询项目文档并返回分类统计。

        Args:
            request: 当前请求。
            project_key: 项目 UUID 或业务编号。

        Returns:
            文档列表及分类统计。
        """
        if not request.user.has_permission_code("document.view"):
            raise PermissionDenied("无项目文档查看权限")
        project = _visible_project(request, project_key)
        base_queryset = documents_for_project(user=request.user, project=project)
        category_rows = base_queryset.values("category").annotate(total=Count("id"))
        category_counts = {
            value: 0 for value in ProjectDocumentCategory.values
        }
        category_counts.update(
            {row["category"]: row["total"] for row in category_rows}
        )

        queryset = base_queryset
        search = request.query_params.get("search", "").strip()
        if search:
            queryset = queryset.filter(
                Q(name__icontains=search)
                | Q(related_content__icontains=search)
                | Q(uploaded_by__display_name__icontains=search)
            )
        category = request.query_params.get("category", "").strip()
        if category:
            if category not in ProjectDocumentCategory.values:
                raise ValidationError({"category": ["未知文档分类"]})
            queryset = queryset.filter(category=category)
        file_type = request.query_params.get("file_type", "").strip()
        extension_groups = {
            "word": ["doc", "docx"],
            "pdf": ["pdf"],
            "excel": ["xls", "xlsx", "csv"],
            "powerpoint": ["ppt", "pptx"],
            "image": ["png", "jpg", "jpeg", "webp"],
        }
        if file_type:
            if file_type not in extension_groups:
                raise ValidationError({"file_type": ["未知文件类型"]})
            queryset = queryset.filter(extension__in=extension_groups[file_type])
        updated_range = request.query_params.get("updated_range", "").strip()
        if updated_range:
            days = {"week": 7, "month": 31}.get(updated_range)
            if not days:
                raise ValidationError({"updated_range": ["未知更新时间范围"]})
            queryset = queryset.filter(updated_at__gte=timezone.now() - timedelta(days=days))

        documents = queryset.order_by("-updated_at", "name")
        return Response(
            {
                "data": ProjectDocumentSerializer(documents, many=True).data,
                "meta": {
                    "total": base_queryset.count(),
                    "filtered_total": documents.count(),
                    "category_counts": category_counts,
                },
                "request_id": getattr(request, "request_id", None),
            }
        )

    def post(self, request, project_key: str) -> Response:
        """上传项目文档。

        Args:
            request: 当前 multipart 请求。
            project_key: 项目 UUID 或业务编号。

        Returns:
            新建文档元数据。
        """
        project = _visible_project(request, project_key)
        serializer = ProjectDocumentCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        document = create_project_document(
            project=project,
            actor=request.user,
            validated_data=serializer.validated_data,
        )
        return Response(
            {
                "data": ProjectDocumentSerializer(document).data,
                "request_id": getattr(request, "request_id", None),
            },
            status=status.HTTP_201_CREATED,
        )


class ProjectDocumentContentView(APIView):
    """项目文档内容读取和下载。"""

    def get(self, request, project_key: str, document_id) -> FileResponse:
        """以内联预览或附件方式返回文档内容。

        Args:
            request: 当前请求。
            project_key: 项目 UUID 或业务编号。
            document_id: 项目文档 UUID。

        Returns:
            文档文件流。
        """
        if not request.user.has_permission_code("document.view"):
            raise PermissionDenied("无项目文档查看权限")
        project = _visible_project(request, project_key)
        document = get_object_or_404(
            ProjectDocument,
            id=document_id,
            project=project,
            organization_id=request.user.organization_id,
        )
        as_attachment = request.query_params.get("download") == "1"
        return FileResponse(
            document.file.open("rb"),
            as_attachment=as_attachment,
            filename=document.name,
            content_type=document.mime_type or "application/octet-stream",
        )
