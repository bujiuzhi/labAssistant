"""请求追踪中间件。"""

from collections.abc import Callable
from uuid import UUID, uuid4

from django.http import HttpRequest, HttpResponse


class RequestIdMiddleware:
    """为每个请求分配稳定请求编号。"""

    header_name = "HTTP_X_REQUEST_ID"

    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]) -> None:
        """初始化中间件。

        Args:
            get_response: Django 后续请求处理函数。
        """
        self.get_response = get_response

    def __call__(self, request: HttpRequest) -> HttpResponse:
        """处理请求并写入响应请求编号。

        Args:
            request: 当前 HTTP 请求。

        Returns:
            带 `X-Request-ID` 的响应。
        """
        request_id = self._normalize_request_id(request.META.get(self.header_name))
        request.request_id = request_id
        response = self.get_response(request)
        response["X-Request-ID"] = request_id
        return response

    @staticmethod
    def _normalize_request_id(raw_value: str | None) -> str:
        """校验客户端请求编号，不合法时生成新编号。

        Args:
            raw_value: 客户端提供的请求编号。

        Returns:
            标准 UUID 字符串。
        """
        if raw_value:
            try:
                return str(UUID(raw_value))
            except ValueError:
                pass
        return str(uuid4())
