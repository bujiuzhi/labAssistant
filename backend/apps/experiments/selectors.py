"""电子实验记录本只读查询。"""

from django.db.models import Q, QuerySet

from apps.identity.models import User
from apps.identity.selectors import visible_organization_ids

from .models import Experiment


def experiments_for_user(user: User) -> QuerySet[Experiment]:
    """查询用户可见实验。

    Args:
        user: 当前登录用户。

    Returns:
        已限定组织和对象范围的实验查询集。
    """
    queryset = (
        Experiment.objects.select_related("project", "owner", "record")
        .prefetch_related("participants__user", "attachments")
    )
    return queryset.filter(
        Q(organization_id__in=visible_organization_ids(user))
        | Q(project__members__user=user)
        | Q(owner=user)
        | Q(participants__user=user)
    ).distinct()
