"""项目只读查询。"""

from django.db.models import Q, QuerySet

from apps.identity.models import User

from .models import Project


def projects_for_user(user: User) -> QuerySet[Project]:
    """查询用户可见项目。

    Args:
        user: 当前登录用户。

    Returns:
        已限定组织和对象范围的项目查询集。
    """
    queryset = (
        Project.objects.select_related("owner")
        .prefetch_related("members__user")
        .filter(organization_id=user.organization_id)
    )
    if user.has_permission_code("project.view_all"):
        return queryset
    return queryset.filter(Q(owner=user) | Q(members__user=user)).distinct()
