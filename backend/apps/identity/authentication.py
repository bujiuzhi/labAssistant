"""需要显式 CSRF 校验的 Session 认证。"""

from rest_framework.authentication import SessionAuthentication


class CsrfEnforcedSessionAuthentication(SessionAuthentication):
    """即使用户尚未登录也执行 CSRF 校验。"""

    def authenticate(self, request):
        """校验 CSRF 后执行 Session 认证。

        Args:
            request: DRF 请求。

        Returns:
            Session 认证结果。
        """
        self.enforce_csrf(request)
        return super().authenticate(request)
