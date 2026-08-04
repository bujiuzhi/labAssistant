"""项目列表、详情、更新与项目文档接口。"""

import logging
import shutil
import subprocess
import uuid
from datetime import date, timedelta
from pathlib import Path
from tempfile import TemporaryDirectory

from django.core.files import File
from django.db.models import Count, Q
from django.db.models.functions import TruncDate
from django.http import FileResponse
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import status
from rest_framework.exceptions import NotAcceptable, PermissionDenied, ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.common.models import BusinessOperationLog
from apps.common.pagination import EnvelopePageNumberPagination

from .document_formats import (
    DIRECT_PREVIEW_EXTENSIONS,
    DOCUMENT_EXTENSION_GROUPS,
    OFFICE_PREVIEW_EXTENSIONS,
)
from .models import (
    Project,
    ProjectDocument,
    ProjectDocumentCategory,
    ProjectFollow,
    ProjectStatus,
)
from .selectors import documents_for_project, projects_for_user
from .serializers import (
    ProjectCreateSerializer,
    ProjectDocumentCreateSerializer,
    ProjectDocumentSerializer,
    ProjectOperationLogSerializer,
    ProjectSerializer,
    ProjectUpdateSerializer,
)
from .services import (
    archive_project,
    create_project,
    create_project_document,
    update_project,
)

logger = logging.getLogger(__name__)

PROJECT_TYPE_CHOICES = ["聚酰亚胺", "环氧树脂"]


def _office_preview_pdf(document: ProjectDocument) -> File:
    """将对象存储中的办公文档转换为缓存 PDF。

    Args:
        document: 已通过权限范围校验的项目文档。

    Returns:
        从当前文件存储打开的 PDF 文件流。

    Raises:
        NotAcceptable: 原文件不可读、未安装转换程序或转换失败。
    """
    version_key = str(int(document.updated_at.timestamp()))
    preview_storage_name = f"project-document-previews/{document.id}/{version_key}/preview.pdf"
    storage = document.file.storage
    if storage.exists(preview_storage_name):
        return storage.open(preview_storage_name, "rb")

    converter = shutil.which("libreoffice") or shutil.which("soffice")
    if not converter:
        logger.error("项目文档预览失败：未找到 LibreOffice，document_id=%s", document.id)
        raise NotAcceptable("服务器未配置办公文档预览服务，请下载原文件查看")

    with TemporaryDirectory(prefix="materials-lab-preview-") as temporary_directory:
        temporary_root = Path(temporary_directory)
        source_path = temporary_root / f"source.{document.extension.lower()}"
        output_directory = temporary_root / "output"
        profile_directory = temporary_root / "libreoffice-profile"
        output_directory.mkdir()
        try:
            with (
                document.file.open("rb") as source_stream,
                source_path.open("wb") as destination_stream,
            ):
                shutil.copyfileobj(source_stream, destination_stream)
        except Exception as error:
            logger.exception("项目文档原文件读取失败：document_id=%s", document.id)
            raise NotAcceptable("原始文档不存在或暂时无法读取") from error

        try:
            result = subprocess.run(
                [
                    converter,
                    f"-env:UserInstallation={profile_directory.as_uri()}",
                    "--headless",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    str(output_directory),
                    str(source_path),
                ],
                capture_output=True,
                text=True,
                timeout=45,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            logger.exception("项目文档转换异常：document_id=%s", document.id)
            raise NotAcceptable("文档转换超时或失败，请下载原文件查看") from error

        converted_files = list(output_directory.glob("*.pdf"))
        if result.returncode != 0 or not converted_files:
            logger.warning(
                "项目文档转换失败：document_id=%s returncode=%s stderr=%s",
                document.id,
                result.returncode,
                result.stderr[-500:],
            )
            raise NotAcceptable("该文档无法在线转换，请下载原文件查看")

        try:
            with converted_files[0].open("rb") as converted_stream:
                saved_name = storage.save(
                    preview_storage_name,
                    File(converted_stream, name="preview.pdf"),
                )
        except Exception as error:
            logger.exception("项目文档预览缓存写入失败：document_id=%s", document.id)
            raise NotAcceptable("预览文件暂时无法保存，请下载原文件查看") from error
    return storage.open(saved_name, "rb")


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


def _milestone_risk(project: Project, today: date) -> dict:
    """计算项目最近未完成里程碑的风险状态。

    Args:
        project: 项目对象。
        today: 当前业务日期。

    Returns:
        风险级别、相差天数和当前里程碑。
    """
    outstanding = []
    for item in project.milestones:
        if item.get("state") == "done" or not item.get("date"):
            continue
        try:
            milestone_date = date.fromisoformat(str(item["date"])[:10])
        except ValueError:
            continue
        outstanding.append((milestone_date, item))
    if not outstanding:
        return {"level": "normal", "days": None, "milestone": None}
    milestone_date, milestone = min(outstanding, key=lambda value: value[0])
    remaining_days = (milestone_date - today).days
    if remaining_days < 0:
        return {
            "level": "overdue",
            "days": abs(remaining_days),
            "milestone": milestone,
        }
    if remaining_days <= 3:
        return {
            "level": "countdown",
            "days": remaining_days,
            "milestone": milestone,
        }
    return {
        "level": "normal",
        "days": remaining_days,
        "milestone": milestone,
    }


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
            aggregate_statuses = {
                "running": [ProjectStatus.ACTIVE, ProjectStatus.AT_RISK],
                "not_started": [ProjectStatus.DRAFT, ProjectStatus.NOT_STARTED],
                "ended": [ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED],
            }
            if project_status in aggregate_statuses:
                queryset = queryset.filter(status__in=aggregate_statuses[project_status])
            elif project_status in ProjectStatus.values:
                queryset = queryset.filter(status=project_status)
            else:
                raise ValidationError({"status": ["未知项目状态"]})
        project_type = request.query_params.get("project_type", "").strip()
        if project_type:
            if project_type not in PROJECT_TYPE_CHOICES:
                raise ValidationError({"project_type": ["未知项目类型"]})
            queryset = queryset.filter(project_type_code=project_type)
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


class DashboardSummaryView(APIView):
    """返回当前用户范围内的实时总览统计。"""

    def get(self, request) -> Response:
        """查询实时项目、实验、类型分布与近 30 天趋势。"""
        if not request.user.has_permission_code("project.view"):
            raise PermissionDenied("无项目查看权限")

        from apps.experiments.models import ExperimentStatus
        from apps.experiments.selectors import experiments_for_user

        projects = projects_for_user(request.user)
        experiments = experiments_for_user(request.user)
        followed_at = {
            item["project_id"]: item["created_at"]
            for item in ProjectFollow.objects.filter(user=request.user).values(
                "project_id",
                "created_at",
            )
        }
        project_status_counts = {
            row["status"]: row["total"]
            for row in projects.values("status").annotate(
                total=Count("id", distinct=True)
            )
        }
        experiment_status_counts = {
            row["status"]: row["total"]
            for row in experiments.values("status").annotate(
                total=Count("id", distinct=True)
            )
        }
        project_type_counts = {
            row["project_type_code"]: row["total"]
            for row in projects.filter(project_type_code__in=PROJECT_TYPE_CHOICES)
            .values("project_type_code")
            .annotate(total=Count("id", distinct=True))
        }
        experiment_type_counts = {
            row["project__project_type_code"]: row["total"]
            for row in experiments.filter(
                project__project_type_code__in=PROJECT_TYPE_CHOICES
            )
            .values("project__project_type_code")
            .annotate(total=Count("id", distinct=True))
        }
        today = timezone.localdate()
        start_date = today - timedelta(days=29)
        labels = [start_date + timedelta(days=offset) for offset in range(30)]
        selected_project_id = request.query_params.get("project_id", "").strip()
        selected_project = None
        trend_experiments = experiments
        if selected_project_id:
            selected_project = get_object_or_404(projects, id=selected_project_id)
            trend_experiments = trend_experiments.filter(project=selected_project)
        trend_rows = (
            trend_experiments.filter(created_at__date__gte=start_date)
            .annotate(day=TruncDate("created_at"))
            .values("day")
            .annotate(total=Count("id", distinct=True))
        )
        trend_map = {row["day"]: row["total"] for row in trend_rows}
        project_options = list(
            projects.order_by("name", "project_no").values("id", "project_no", "name")
        )
        active_project_rows = []
        for project in projects.filter(
            status__in=[ProjectStatus.ACTIVE, ProjectStatus.AT_RISK],
        ):
            risk = _milestone_risk(project, today)
            active_project_rows.append(
                {
                    "id": str(project.id),
                    "project_no": project.project_no,
                    "name": project.name,
                    "project_type": project.project_type_code,
                    "owner_name": project.owner.display_name,
                    "objectives": project.objectives,
                    "planned_start_date": project.planned_start_date,
                    "planned_end_date": project.planned_end_date,
                    "milestone": risk["milestone"],
                    "progress_percent": project.progress_percent,
                    "is_followed": project.id in followed_at,
                    "followed_at": followed_at.get(project.id),
                    "risk_level": risk["level"],
                    "risk_days": risk["days"],
                    "created_at": project.created_at,
                }
            )

        def active_project_sort_key(item: dict) -> tuple:
            """按关注、延期、倒计时和新建时间排序项目卡片。"""
            if item["is_followed"]:
                return (0, -item["followed_at"].timestamp())
            if item["risk_level"] == "overdue":
                return (1, -(item["risk_days"] or 0))
            if item["risk_level"] == "countdown":
                return (2, -(item["risk_days"] or 0))
            return (3, -item["created_at"].timestamp())

        active_project_rows.sort(key=active_project_sort_key)
        recent_projects = [
            {
                key: value
                for key, value in item.items()
                if key not in {"followed_at", "created_at"}
            }
            for item in active_project_rows[:6]
        ]
        risk_project_count = sum(
            _milestone_risk(project, today)["level"] in {"overdue", "countdown"}
            for project in projects.exclude(
                status__in=[ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED]
            )
        )
        return Response(
            {
                "data": {
                    "project_metrics": {
                        "total": projects.count(),
                        "active": project_status_counts.get(ProjectStatus.ACTIVE, 0)
                        + project_status_counts.get(ProjectStatus.AT_RISK, 0),
                        "archived": project_status_counts.get(ProjectStatus.ARCHIVED, 0),
                        "at_risk": risk_project_count,
                    },
                    "experiment_metrics": {
                        "total": experiments.count(),
                        "in_progress": experiment_status_counts.get(
                            ExperimentStatus.IN_PROGRESS,
                            0,
                        ),
                        "completed": experiment_status_counts.get(
                            ExperimentStatus.COMPLETED,
                            0,
                        ),
                    },
                    "type_distribution": [
                        {
                            "name": type_name,
                            "project_count": project_type_counts.get(type_name, 0),
                            "experiment_count": experiment_type_counts.get(type_name, 0),
                        }
                        for type_name in PROJECT_TYPE_CHOICES
                    ],
                    "trend": {
                        "dates": [item.isoformat() for item in labels],
                        "series": [
                            {
                                "name": (
                                    selected_project.name
                                    if selected_project
                                    else "全部项目"
                                ),
                                "values": [
                                    trend_map.get(item, 0) for item in labels
                                ],
                            }
                        ],
                        "selected_project_id": selected_project_id,
                        "project_options": [
                            {
                                **item,
                                "id": str(item["id"]),
                            }
                            for item in project_options
                        ],
                    },
                    "active_projects": recent_projects,
                },
                "request_id": getattr(request, "request_id", None),
            }
        )


class ProjectFollowView(APIView):
    """新增或取消当前用户对项目的关注。"""

    def post(self, request, project_key: str) -> Response:
        """关注项目。"""
        project = _visible_project(request, project_key)
        ProjectFollow.objects.get_or_create(
            organization_id=request.user.organization_id,
            project=project,
            user=request.user,
        )
        return Response({"data": {"is_followed": True}})

    def delete(self, request, project_key: str) -> Response:
        """取消关注项目。"""
        project = _visible_project(request, project_key)
        ProjectFollow.objects.filter(project=project, user=request.user).delete()
        return Response({"data": {"is_followed": False}})


class ProjectArchiveView(APIView):
    """归档项目。"""

    def post(self, request, project_key: str) -> Response:
        """归档当前用户可维护项目。"""
        current = _visible_project(request, project_key)
        project = archive_project(
            project_id=current.id,
            actor=request.user,
            expected_version=_parse_if_match(request),
        )
        return _project_response(project, request)


class ProjectOperationLogListView(APIView):
    """查询项目操作日志。"""

    def get(self, request, project_key: str) -> Response:
        """返回当前项目最近操作记录。"""
        project = _visible_project(request, project_key)
        logs = BusinessOperationLog.objects.select_related("actor").filter(
            organization_id=project.organization_id,
            domain="project",
            object_id=project.id,
        ).order_by("-created_at")[:100]
        return Response(
            {
                "data": ProjectOperationLogSerializer(logs, many=True).data,
                "request_id": getattr(request, "request_id", None),
            }
        )


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
                | Q(uploaded_by__display_name__icontains=search)
            )
        category = request.query_params.get("category", "").strip()
        if category:
            if category not in ProjectDocumentCategory.values:
                raise ValidationError({"category": ["未知文档分类"]})
            queryset = queryset.filter(category=category)
        file_type = request.query_params.get("file_type", "").strip()
        if file_type:
            if file_type not in DOCUMENT_EXTENSION_GROUPS:
                raise ValidationError({"file_type": ["未知文件类型"]})
            queryset = queryset.filter(extension__in=DOCUMENT_EXTENSION_GROUPS[file_type])
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
            organization_id=project.organization_id,
        )
        as_attachment = request.query_params.get("download") == "1"
        return FileResponse(
            document.file.open("rb"),
            as_attachment=as_attachment,
            filename=document.name,
            content_type=document.mime_type or "application/octet-stream",
        )


class ProjectDocumentPreviewView(APIView):
    """返回项目文档的实际在线预览内容。"""

    def get(self, request, project_key: str, document_id) -> FileResponse:
        """读取原始 PDF、图片或转换后的办公文档 PDF。

        Args:
            request: 当前请求。
            project_key: 项目 UUID 或业务编号。
            document_id: 项目文档 UUID。

        Returns:
            可在浏览器内嵌显示的文件流。
        """
        if not request.user.has_permission_code("document.view"):
            raise PermissionDenied("无项目文档查看权限")
        project = _visible_project(request, project_key)
        document = get_object_or_404(
            ProjectDocument,
            id=document_id,
            project=project,
            organization_id=project.organization_id,
        )
        extension = document.extension.lower()
        if extension in DIRECT_PREVIEW_EXTENSIONS:
            response = FileResponse(
                document.file.open("rb"),
                as_attachment=False,
                filename=document.name,
                content_type=document.mime_type or "application/octet-stream",
            )
            response["X-Frame-Options"] = "SAMEORIGIN"
            response["Content-Security-Policy"] = "frame-ancestors 'self'"
            return response
        if extension in OFFICE_PREVIEW_EXTENSIONS:
            preview_file = _office_preview_pdf(document)
            response = FileResponse(
                preview_file,
                as_attachment=False,
                filename=f"{Path(document.name).stem}.pdf",
                content_type="application/pdf",
            )
            response["X-Frame-Options"] = "SAMEORIGIN"
            response["Content-Security-Policy"] = "frame-ancestors 'self'"
            return response
        raise NotAcceptable("该文件格式暂不支持在线预览，请下载原文件查看")
