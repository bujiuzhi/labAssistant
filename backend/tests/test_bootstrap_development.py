"""开发数据初始化命令测试。"""

import pytest
from apps.identity.models import Organization, User, UserRole
from django.core.management import call_command


@pytest.mark.django_db
def test_bootstrap_creates_all_role_accounts_with_default_password(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """初始化命令应可重复创建三类角色账号并使用默认开发密码。"""
    monkeypatch.delenv("MATERIALS_LAB_DEVELOPMENT_PASSWORD", raising=False)

    call_command("bootstrap_development")
    call_command("bootstrap_development")

    organization = Organization.objects.get(organization_code="LAB")
    expected_roles = {
        "admin": "system_admin",
        "manager": "project_manager",
        "researcher": "researcher",
        "inspector": "researcher",
    }

    assert User.objects.filter(organization=organization).count() == 4
    assert UserRole.objects.filter(organization=organization).count() == 4
    for username, role_code in expected_roles.items():
        user = User.objects.get(organization=organization, username=username)
        assert user.check_password("00000000")
        assert user.user_roles.filter(role__role_code=role_code).exists()

    admin = User.objects.get(organization=organization, username="admin")
    assert admin.is_staff
    assert admin.is_superuser
    assert admin.is_super_admin
