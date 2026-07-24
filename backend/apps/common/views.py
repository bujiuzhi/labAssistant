"""健康检查接口。"""

from django.db import connection
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView


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
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
        return Response({"status": "ok", "checks": {"database": "ok"}})
