"""CSRF、会话与超级管理员用户管理接口。"""

import logging

from django.contrib.auth import authenticate, login, logout
from django.db.models import Q
from django.middleware.csrf import get_token
from django.shortcuts import get_object_or_404
from rest_framework import status
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.common.pagination import EnvelopePageNumberPagination

from .authentication import CsrfEnforcedSessionAuthentication
from .models import Role, RoleStatus, User, UserStatus
from .permissions import IsOrganizationSuperAdmin
from .serializers import (
    LoginSerializer,
    ManagedUserCreateSerializer,
    ManagedUserSerializer,
    ManagedUserUpdateSerializer,
    OrganizationUserOptionSerializer,
    PasswordResetSerializer,
    RoleOptionSerializer,
    SessionUserSerializer,
)
from .services import (
    ASSIGNABLE_ROLE_CODES,
    create_organization_user,
    reset_organization_user_password,
    update_organization_user,
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


class ManagedUserListCreateView(APIView):
    """超级管理员查询和创建组织用户。"""

    permission_classes = [IsOrganizationSuperAdmin]

    def get(self, request) -> Response:
        """分页查询当前组织用户。

        Args:
            request: 当前请求。

        Returns:
            用户分页列表。
        """
        queryset = User.objects.filter(
            organization_id=request.user.organization_id,
        ).prefetch_related("user_roles__role")
        search = request.query_params.get("search", "").strip()
        if search:
            queryset = queryset.filter(
                Q(username__icontains=search)
                | Q(display_name__icontains=search)
                | Q(email__icontains=search)
            )
        user_status = request.query_params.get("status", "").strip()
        if user_status:
            if user_status not in UserStatus.values:
                raise ValidationError({"status": ["未知用户状态"]})
            queryset = queryset.filter(status=user_status)
        role_code = request.query_params.get("role_code", "").strip()
        if role_code:
            queryset = queryset.filter(user_roles__role__role_code=role_code)
        queryset = queryset.distinct().order_by("-is_super_admin", "display_name", "username")

        paginator = EnvelopePageNumberPagination()
        page = paginator.paginate_queryset(queryset, request, view=self)
        return paginator.get_paginated_response(
            ManagedUserSerializer(page, many=True).data
        )

    def post(self, request) -> Response:
        """创建组织用户并分配角色。

        Args:
            request: 当前请求。

        Returns:
            新建用户。
        """
        serializer = ManagedUserCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = create_organization_user(
            actor=request.user,
            validated_data=dict(serializer.validated_data),
        )
        return Response(
            {
                "data": ManagedUserSerializer(user).data,
                "request_id": getattr(request, "request_id", None),
            },
            status=status.HTTP_201_CREATED,
        )


class ManagedUserDetailView(APIView):
    """超级管理员更新组织用户。"""

    permission_classes = [IsOrganizationSuperAdmin]

    def get_object(self, request, user_id) -> User:
        """获取当前组织目标用户。

        Args:
            request: 当前请求。
            user_id: 用户主键。

        Returns:
            当前组织用户。
        """
        return get_object_or_404(
            User.objects.prefetch_related("user_roles__role"),
            id=user_id,
            organization_id=request.user.organization_id,
        )

    def get(self, request, user_id) -> Response:
        """查询组织用户详情。

        Args:
            request: 当前请求。
            user_id: 用户主键。

        Returns:
            用户详情。
        """
        return Response(
            {
                "data": ManagedUserSerializer(self.get_object(request, user_id)).data,
                "request_id": getattr(request, "request_id", None),
            }
        )

    def patch(self, request, user_id) -> Response:
        """更新组织用户基本信息、状态与角色。

        Args:
            request: 当前请求。
            user_id: 用户主键。

        Returns:
            更新后的用户。
        """
        self.get_object(request, user_id)
        serializer = ManagedUserUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = update_organization_user(
            actor=request.user,
            user_id=user_id,
            validated_data=dict(serializer.validated_data),
        )
        return Response(
            {
                "data": ManagedUserSerializer(user).data,
                "request_id": getattr(request, "request_id", None),
            }
        )


class ManagedUserPasswordResetView(APIView):
    """超级管理员重置组织用户密码。"""

    permission_classes = [IsOrganizationSuperAdmin]

    def post(self, request, user_id) -> Response:
        """重置指定组织用户密码。

        Args:
            request: 当前请求。
            user_id: 用户主键。

        Returns:
            204 空响应。
        """
        get_object_or_404(
            User,
            id=user_id,
            organization_id=request.user.organization_id,
        )
        serializer = PasswordResetSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        reset_organization_user_password(
            actor=request.user,
            user_id=user_id,
            password=serializer.validated_data["password"],
        )
        return Response(status=status.HTTP_204_NO_CONTENT)


class ManagedRoleOptionsView(APIView):
    """超级管理员查询可分配系统角色。"""

    permission_classes = [IsOrganizationSuperAdmin]

    def get(self, request) -> Response:
        """返回当前组织可分配角色。

        Args:
            request: 当前请求。

        Returns:
            角色选择项列表。
        """
        roles = Role.objects.filter(
            organization_id=request.user.organization_id,
            status=RoleStatus.ACTIVE,
            role_code__in=ASSIGNABLE_ROLE_CODES,
        ).order_by("name", "role_code")
        return Response(
            {
                "data": RoleOptionSerializer(roles, many=True).data,
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
