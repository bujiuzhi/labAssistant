"""健康检查接口。"""

import logging

from django.conf import settings
from django.db import connection
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .object_storage import check_object_storage_bucket

logger = logging.getLogger(__name__)


class LivenessView(APIView):
    """进程存活检查。"""

    authentication_classes: list = []
    permission_classes = [AllowAny]

    def get(self, request) -> Response:
        """返回进程存活状态。

        Args:
            request: 当前 HTTP 请求。

        Returns:
            存活状态。
        """
        return Response({"status": "ok"})


class ReadinessView(APIView):
    """服务就绪检查。"""

    authentication_classes: list = []
    permission_classes = [AllowAny]

    def get(self, request) -> Response:
        """检查数据库并返回就绪状态。

        Args:
            request: 当前 HTTP 请求。

        Returns:
            数据库依赖检查结果。
        """
        checks = {}
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
        checks["database"] = "ok"

        if settings.OBJECT_STORAGE_ENABLED:
            try:
                check_object_storage_bucket()
            except Exception:
                logger.exception("对象存储就绪检查失败")
                checks["object_storage"] = "error"
                return Response(
                    {"status": "error", "checks": checks},
                    status=503,
                )
            checks["object_storage"] = "ok"
        return Response({"status": "ok", "checks": checks})
