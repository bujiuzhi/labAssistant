"""后端测试夹具。"""

import pytest
from apps.identity.models import (
    Organization,
    Permission,
    Role,
    RolePermission,
    User,
    UserRole,
)
from rest_framework.test import APIClient


@pytest.fixture
def organization(db) -> Organization:
    """创建测试组织。"""
    return Organization.objects.create(
        organization_code="TEST",
        name="测试材料中心",
    )


@pytest.fixture
def manager_user(organization: Organization) -> User:
    """创建具有项目管理权限的用户。"""
    user = User.objects.create_user(
        organization=organization,
        username="manager",
        display_name="项目负责人",
        password="test-password-123",
    )
    role = Role.objects.create(
        organization=organization,
        role_code="project_manager",
        name="项目负责人",
    )
    for permission_code in [
        "project.view",
        "project.view_all",
        "project.create",
        "project.update",
        "project.manage_members",
        "document.view",
        "document.upload",
        "experiment.view",
        "experiment.view_all",
        "experiment.create",
        "experiment.update",
        "experiment.execute",
    ]:
        permission = Permission.objects.create(
            permission_code=permission_code,
            name=permission_code,
            module_code="projects",
        )
        RolePermission.objects.create(role=role, permission=permission)
    UserRole.objects.create(organization=organization, user=user, role=role)
    return user


@pytest.fixture
def researcher_user(organization: Organization, manager_user: User) -> User:
    """创建普通研究人员。"""
    user = User.objects.create_user(
        organization=organization,
        username="researcher",
        display_name="研究人员",
        password="test-password-123",
    )
    role = Role.objects.create(
        organization=organization,
        role_code="researcher",
        name="研究人员",
    )
    for permission_code in [
        "project.view",
        "document.view",
        "document.upload",
        "experiment.view",
        "experiment.create",
        "experiment.update",
        "experiment.execute",
    ]:
        RolePermission.objects.create(
            role=role,
            permission=Permission.objects.get(permission_code=permission_code),
        )
    UserRole.objects.create(organization=organization, user=user, role=role)
    return user


@pytest.fixture
def api_client() -> APIClient:
    """创建 DRF 测试客户端。"""
    return APIClient()
