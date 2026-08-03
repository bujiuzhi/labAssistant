"""组织架构和用户数据范围查询。"""

from django.db.models import QuerySet

from .models import Organization, OrganizationStatus, User, UserStatus


def visible_organization_ids(user: User) -> set:
    """返回用户可见的组织主键集合。

    所有角色均按本人组织及全部下级组织确定组织范围；项目成员关系由业务查询
    另行并入，避免系统角色绕过组织边界。

    Args:
        user: 当前登录用户。

    Returns:
        可用于业务查询的组织主键集合。
    """
    active_organizations = Organization.objects.filter(
        status=OrganizationStatus.ACTIVE,
    )
    visible_ids = {user.organization_id}
    frontier = {user.organization_id}
    while frontier:
        children = set(
            active_organizations.filter(parent_id__in=frontier).values_list(
                "id",
                flat=True,
            )
        )
        frontier = children - visible_ids
        visible_ids.update(frontier)
    return visible_ids


def visible_user_options(user: User) -> QuerySet[User]:
    """查询用户可选择为负责人或成员的有效账号。

    Args:
        user: 当前登录用户。

    Returns:
        当前数据范围内的有效用户查询集。
    """
    return User.objects.select_related("organization").filter(
        organization_id__in=visible_organization_ids(user),
        status=UserStatus.ACTIVE,
        is_active=True,
    )
