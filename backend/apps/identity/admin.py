"""身份模型后台管理。"""

from django.contrib import admin
from django.contrib.auth.admin import UserAdmin

from .models import Organization, Permission, Role, RolePermission, User, UserRole


@admin.register(User)
class OrganizationUserAdmin(UserAdmin):
    """组织用户后台配置。"""

    fieldsets = UserAdmin.fieldsets + (
        ("组织信息", {"fields": ("organization", "display_name", "status", "is_super_admin")}),
    )
    add_fieldsets = UserAdmin.add_fieldsets + (
        ("组织信息", {"fields": ("organization", "display_name", "status", "is_super_admin")}),
    )
    list_display = ("username", "display_name", "organization", "status", "is_staff")
    list_filter = ("organization", "status", "is_staff")


admin.site.register(Organization)
admin.site.register(Role)
admin.site.register(Permission)
admin.site.register(UserRole)
admin.site.register(RolePermission)
