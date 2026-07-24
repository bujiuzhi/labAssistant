"""身份与组织管理接口权限。"""

from rest_framework.permissions import BasePermission


class IsOrganizationSuperAdmin(BasePermission):
    """仅允许当前组织超级管理员访问。"""

    message = "仅超级管理员可执行此操作"

    def has_permission(self, request, view) -> bool:
        """判断当前用户是否为有效超级管理员。

        Args:
            request: 当前 DRF 请求。
            view: 当前接口视图。

        Returns:
            用户是否具备超级管理员身份。
        """
        user = request.user
        return bool(
            user
            and user.is_authenticated
            and user.is_active
            and user.is_super_admin
        )
