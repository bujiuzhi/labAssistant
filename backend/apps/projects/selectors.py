"""项目只读查询。"""

from django.db.models import Q, QuerySet

from apps.identity.models import User

from .models import Project, ProjectDocument


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


def documents_for_project(
    *,
    user: User,
    project: Project,
) -> QuerySet[ProjectDocument]:
    """查询用户可见项目下的文档。

    Args:
        user: 当前登录用户。
        project: 已通过项目对象权限校验的项目。

    Returns:
        按更新时间倒序排列的项目文档查询集。
    """
    return ProjectDocument.objects.select_related(
        "project",
        "uploaded_by",
    ).filter(
        organization_id=user.organization_id,
        project=project,
    )
