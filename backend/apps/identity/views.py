"""CSRF、登录、会话和注销接口。"""

import logging

from django.contrib.auth import authenticate, login, logout
from django.middleware.csrf import get_token
from rest_framework import status
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .authentication import CsrfEnforcedSessionAuthentication
from .models import User, UserStatus
from .serializers import (
    LoginSerializer,
    OrganizationUserOptionSerializer,
    SessionUserSerializer,
)

logger = logging.getLogger(__name__)


class CsrfView(APIView):
    """获取 CSRF 令牌。"""

    authentication_classes: list = []
    permission_classes = [AllowAny]

    def get(self, request) -> Response:
        """返回并设置 CSRF 令牌。

        Args:
            request: 当前请求。

        Returns:
            CSRF 令牌响应。
        """
        return Response(
            {
                "data": {"csrf_token": get_token(request)},
                "request_id": getattr(request, "request_id", None),
            }
        )


class LoginView(APIView):
    """用户登录。"""

    authentication_classes = [CsrfEnforcedSessionAuthentication]
    permission_classes = [AllowAny]

    def post(self, request) -> Response:
        """验证凭据并建立 Session。

        Args:
            request: 登录请求。

        Returns:
            当前会话用户。
        """
        serializer = LoginSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = authenticate(
            request=request,
            username=serializer.validated_data["username"],
            password=serializer.validated_data["password"],
        )
        if user is None:
            logger.warning(
                "用户登录失败",
                extra={"request_id": getattr(request, "request_id", None)},
            )
            return Response(
                {
                    "type": "https://errors.materials-lab.local/invalid-credentials",
                    "title": "用户名或密码错误",
                    "status": status.HTTP_401_UNAUTHORIZED,
                    "code": "INVALID_CREDENTIALS",
                    "detail": "用户名或密码错误",
                    "instance": request.path,
                    "request_id": getattr(request, "request_id", None),
                },
                status=status.HTTP_401_UNAUTHORIZED,
                content_type="application/problem+json",
            )
        login(request, user)
        request.session.cycle_key()
        logger.info(
            "用户登录成功",
            extra={
                "request_id": getattr(request, "request_id", None),
                "user_id": str(user.id),
                "organization_id": str(user.organization_id),
            },
        )
        return Response(
            {
                "data": SessionUserSerializer(user).data,
                "request_id": getattr(request, "request_id", None),
            }
        )


class SessionView(APIView):
    """当前登录会话。"""

    permission_classes = [IsAuthenticated]

    def get(self, request) -> Response:
        """返回当前用户和权限摘要。

        Args:
            request: 当前请求。

        Returns:
            当前会话响应。
        """
        return Response(
            {
                "data": SessionUserSerializer(request.user).data,
                "request_id": getattr(request, "request_id", None),
            }
        )


class OrganizationUserOptionsView(APIView):
    """查询当前组织可选项目人员。"""

    permission_classes = [IsAuthenticated]

    def get(self, request) -> Response:
        """返回当前组织有效用户。

        Args:
            request: 当前请求。

        Returns:
            用户选择项列表。
        """
        users = User.objects.filter(
            organization_id=request.user.organization_id,
            status=UserStatus.ACTIVE,
            is_active=True,
        ).order_by("display_name", "username")
        return Response(
            {
                "data": OrganizationUserOptionSerializer(users, many=True).data,
                "request_id": getattr(request, "request_id", None),
            }
        )


class LogoutView(APIView):
    """注销当前会话。"""

    permission_classes = [IsAuthenticated]

    def post(self, request) -> Response:
        """清除当前 Session。

        Args:
            request: 当前请求。

        Returns:
            204 空响应。
        """
        user_id = request.user.id
        logout(request)
        logger.info(
            "用户注销成功",
            extra={
                "request_id": getattr(request, "request_id", None),
                "user_id": str(user_id),
            },
        )
        return Response(status=status.HTTP_204_NO_CONTENT)
