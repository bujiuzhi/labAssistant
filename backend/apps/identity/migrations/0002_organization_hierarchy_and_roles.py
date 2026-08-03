"""增加组织层级并统一项目管理员与实验员功能权限。"""

from django.db import migrations, models
import django.db.models.deletion


FUNCTIONAL_PERMISSION_CODES = {
    "project.view",
    "project.create",
    "project.update",
    "project.manage_members",
    "document.view",
    "document.upload",
    "experiment.view",
    "experiment.create",
    "experiment.update",
    "experiment.execute",
}


def align_roles(apps, schema_editor) -> None:
    """迁移旧角色并统一三类角色的功能权限。"""
    Permission = apps.get_model("identity", "Permission")
    Role = apps.get_model("identity", "Role")
    RolePermission = apps.get_model("identity", "RolePermission")
    UserRole = apps.get_model("identity", "UserRole")

    functional_permissions = list(
        Permission.objects.filter(permission_code__in=FUNCTIONAL_PERMISSION_CODES)
    )
    for role in Role.objects.filter(role_code="project_manager"):
        role.name = "项目管理员"
        role.save(update_fields=["name", "updated_at"])
        for permission in functional_permissions:
            RolePermission.objects.get_or_create(role=role, permission=permission)

    for role in Role.objects.filter(role_code="researcher"):
        role.name = "实验员"
        role.save(update_fields=["name", "updated_at"])
        for permission in functional_permissions:
            RolePermission.objects.get_or_create(role=role, permission=permission)

    for inspector_role in Role.objects.filter(role_code="inspector"):
        researcher_role = Role.objects.filter(
            organization_id=inspector_role.organization_id,
            role_code="researcher",
        ).first()
        if researcher_role:
            for link in UserRole.objects.filter(role=inspector_role):
                UserRole.objects.get_or_create(
                    organization_id=link.organization_id,
                    user_id=link.user_id,
                    role=researcher_role,
                )
                link.delete()
        inspector_role.name = "检测人员（已停用）"
        inspector_role.status = "disabled"
        inspector_role.save(update_fields=["name", "status", "updated_at"])


class Migration(migrations.Migration):
    """增加组织父子关系并迁移角色。"""

    dependencies = [
        ("identity", "0001_initial"),
    ]

    operations = [
        migrations.AddField(
            model_name="organization",
            name="parent",
            field=models.ForeignKey(
                blank=True,
                db_comment="上级组织",
                null=True,
                on_delete=django.db.models.deletion.PROTECT,
                related_name="children",
                to="identity.organization",
            ),
        ),
        migrations.RunPython(align_roles, migrations.RunPython.noop),
    ]
