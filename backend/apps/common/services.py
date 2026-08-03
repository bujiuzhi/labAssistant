"""跨业务域通用写入服务。"""

from typing import Any

from apps.identity.models import User

from .models import BusinessOperationLog


def record_business_operation(
    *,
    actor: User,
    domain: str,
    object_id,
    object_no: str,
    action_type: str,
    description: str,
    changes: dict[str, Any] | None = None,
    organization_id=None,
) -> BusinessOperationLog:
    """记录一条可追溯业务操作。

    Args:
        actor: 当前操作用户。
        domain: 稳定业务域代码。
        object_id: 业务对象主键。
        object_no: 业务对象编号。
        action_type: 稳定操作类型。
        description: 面向用户的操作说明。
        changes: 不含敏感值的结构化变更摘要。
        organization_id: 业务对象所属组织，默认使用操作用户组织。

    Returns:
        已持久化的业务操作日志。
    """
    return BusinessOperationLog.objects.create(
        organization_id=organization_id or actor.organization_id,
        actor=actor,
        domain=domain,
        object_id=object_id,
        object_no=object_no,
        action_type=action_type,
        description=description,
        changes=changes or {},
    )
