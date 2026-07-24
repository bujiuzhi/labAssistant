"""统一异常与问题详情响应。"""

from typing import Any

from rest_framework import status
from rest_framework.exceptions import APIException, ValidationError
from rest_framework.response import Response
from rest_framework.views import exception_handler


class ResourceVersionConflict(APIException):
    """资源乐观锁版本不一致。"""

    status_code = status.HTTP_412_PRECONDITION_FAILED
    default_detail = "记录已被其他用户修改，请重新加载后再提交"
    default_code = "RESOURCE_VERSION_CONFLICT"


class BusinessRuleConflict(APIException):
    """当前资源状态不允许执行目标操作。"""

    status_code = status.HTTP_409_CONFLICT
    default_detail = "当前资源状态不允许执行此操作"
    default_code = "BUSINESS_RULE_CONFLICT"


def _field_errors(detail: Any) -> dict[str, list[str]] | None:
    """将 DRF 校验错误转换为字段错误。

    Args:
        detail: DRF 异常详情。

    Returns:
        字段到错误消息列表的映射；无法转换时返回空值。
    """
    if not isinstance(detail, dict):
        return None
    result: dict[str, list[str]] = {}
    for field_name, messages in detail.items():
        if isinstance(messages, list):
            result[field_name] = [str(message) for message in messages]
        else:
            result[field_name] = [str(messages)]
    return result


def problem_exception_handler(exc: Exception, context: dict[str, Any]) -> Response | None:
    """将 DRF 异常转换为统一问题详情。

    Args:
        exc: 捕获的异常。
        context: DRF 异常上下文。

    Returns:
        统一问题详情响应；未知异常返回空值交由 Django 处理。
    """
    response = exception_handler(exc, context)
    if response is None:
        return None

    request = context.get("request")
    request_id = getattr(request, "request_id", None)
    code = getattr(exc, "default_code", "REQUEST_FAILED")
    detail = getattr(exc, "detail", response.data)
    if isinstance(exc, ValidationError):
        title = "请求数据校验失败"
        code = "VALIDATION_ERROR"
    else:
        title = str(detail) if isinstance(detail, str) else "请求处理失败"

    response.data = {
        "type": f"https://errors.materials-lab.local/{str(code).lower()}",
        "title": title,
        "status": response.status_code,
        "code": str(code).upper(),
        "detail": title,
        "instance": request.path if request else None,
        "request_id": request_id,
    }
    field_errors = _field_errors(detail)
    if field_errors:
        response.data["field_errors"] = field_errors
    response.content_type = "application/problem+json"
    return response
