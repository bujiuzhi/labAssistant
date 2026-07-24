"""统一分页响应。"""

from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response


class EnvelopePageNumberPagination(PageNumberPagination):
    """返回统一数据包络的页码分页器。"""

    page_size = 20
    page_size_query_param = "page_size"
    max_page_size = 100

    def get_paginated_response(self, data: list[dict]) -> Response:
        """生成分页响应。

        Args:
            data: 当前页序列化结果。

        Returns:
            带分页元数据和请求编号的响应。
        """
        total = self.page.paginator.count
        return Response(
            {
                "data": data,
                "meta": {
                    "page": self.page.number,
                    "page_size": self.get_page_size(self.request),
                    "total": total,
                    "total_pages": self.page.paginator.num_pages if total else 0,
                },
                "request_id": getattr(self.request, "request_id", None),
            }
        )
