"""超级管理员用户管理接口测试。"""

import pytest

from apps.identity.models import Role, User, UserRole


@pytest.fixture
def super_admin_user(
    organization,
    manager_user,
    researcher_user,
) -> User:
    """创建受保护的组织超级管理员。"""
    role = Role.objects.create(
        organization=organization,
        role_code="system_admin",
        name="超级管理员",
        is_system=True,
    )
    user = User.objects.create_user(
        organization=organization,
        username="admin",
        display_name="系统管理员",
        password="test-password-123",
        is_super_admin=True,
        is_staff=True,
        is_superuser=True,
    )
    UserRole.objects.create(organization=organization, user=user, role=role)
    return user


@pytest.mark.django_db
def test_super_admin_can_list_and_create_users(
    api_client,
    super_admin_user,
) -> None:
    """超级管理员可以查询并创建带角色的组织用户。"""
    api_client.force_authenticate(super_admin_user)

    list_response = api_client.get("/api/v1/auth/users")
    create_response = api_client.post(
        "/api/v1/auth/users",
        {
            "username": "new.researcher",
            "display_name": "新研究员",
            "email": "new@example.com",
            "password": "00000000",
            "status": "active",
            "role_codes": ["researcher"],
        },
        format="json",
    )
    options_response = api_client.get("/api/v1/auth/users/options")

    assert list_response.status_code == 200
    assert list_response.json()["meta"]["total"] == 3
    assert create_response.status_code == 201
    assert options_response.status_code == 200
    assert "new.researcher" in {
        item["username"] for item in options_response.json()["data"]
    }
    created_user = User.objects.get(username="new.researcher")
    assert created_user.organization_id == super_admin_user.organization_id
    assert created_user.check_password("00000000")
    assert list(
        created_user.user_roles.values_list("role__role_code", flat=True)
    ) == ["researcher"]


@pytest.mark.django_db
def test_non_super_admin_cannot_manage_users(
    api_client,
    manager_user,
) -> None:
    """项目负责人不能访问用户管理接口。"""
    api_client.force_authenticate(manager_user)

    response = api_client.get("/api/v1/auth/users")

    assert response.status_code == 403


@pytest.mark.django_db
def test_super_admin_can_update_status_roles_and_reset_password(
    api_client,
    super_admin_user,
    researcher_user,
) -> None:
    """超级管理员可以更新普通用户状态、角色并重置密码。"""
    api_client.force_authenticate(super_admin_user)

    update_response = api_client.patch(
        f"/api/v1/auth/users/{researcher_user.id}",
        {
            "display_name": "暂停研究员",
            "status": "locked",
            "role_codes": ["project_manager"],
        },
        format="json",
    )
    reset_response = api_client.post(
        f"/api/v1/auth/users/{researcher_user.id}/reset-password",
        {"password": "87654321"},
        format="json",
    )

    researcher_user.refresh_from_db()
    assert update_response.status_code == 200
    assert researcher_user.display_name == "暂停研究员"
    assert researcher_user.status == "locked"
    assert not researcher_user.is_active
    assert list(
        researcher_user.user_roles.values_list("role__role_code", flat=True)
    ) == ["project_manager"]
    assert reset_response.status_code == 204
    assert researcher_user.check_password("87654321")


@pytest.mark.django_db
def test_super_admin_account_is_protected(
    api_client,
    super_admin_user,
) -> None:
    """超级管理员不能通过业务用户管理接口被停用。"""
    api_client.force_authenticate(super_admin_user)

    response = api_client.patch(
        f"/api/v1/auth/users/{super_admin_user.id}",
        {"status": "disabled"},
        format="json",
    )

    assert response.status_code == 409
