"""组织用户认证后端。"""

from typing import Any

from django.contrib.auth.backends import ModelBackend
from django.contrib.auth.base_user import AbstractBaseUser
from django.http import HttpRequest

from .models import OrganizationStatus, User, UserStatus


class OrganizationBackend(ModelBackend):
    """按组织和用户名认证；单组织登录可省略组织代码。"""

    def authenticate(
        self,
        request: HttpRequest | None,
        username: str | None = None,
        password: str | None = None,
        **kwargs: Any,
    ) -> AbstractBaseUser | None:
        """验证用户名和密码。

        Args:
            request: 当前请求。
            username: 登录名。
            password: 明文密码。
            **kwargs: 可选 `organization_code`。

        Returns:
            验证通过的用户，否则返回空值。
        """
        if not username or not password:
            return None
        queryset = User.objects.select_related("organization").filter(
            username__iexact=username,
            status=UserStatus.ACTIVE,
            is_active=True,
            organization__status=OrganizationStatus.ACTIVE,
        )
        organization_code = kwargs.get("organization_code")
        if organization_code:
            queryset = queryset.filter(organization__organization_code=organization_code)
        users = list(queryset[:2])
        if len(users) != 1:
            return None
        user = users[0]
        if user.check_password(password) and self.user_can_authenticate(user):
            return user
        return None
