"""会话认证接口测试。"""

import json

import pytest
from django.test import Client


@pytest.mark.django_db
def test_login_requires_valid_csrf_and_returns_session(manager_user) -> None:
    """登录应使用 CSRF 并建立可恢复 Session。"""
    client = Client(enforce_csrf_checks=True)
    csrf_response = client.get("/api/v1/auth/csrf")
    csrf_token = csrf_response.json()["data"]["csrf_token"]

    login_response = client.post(
        "/api/v1/auth/login",
        data=json.dumps(
            {"username": manager_user.username, "password": "test-password-123"}
        ),
        content_type="application/json",
        HTTP_X_CSRFTOKEN=csrf_token,
    )

    assert login_response.status_code == 200
    assert login_response.json()["data"]["id"] == str(manager_user.id)
    assert "project.create" in login_response.json()["data"]["permissions"]
    assert login_response.json()["data"]["role_codes"] == ["project_manager"]
    assert login_response.json()["data"]["role_names"] == ["项目负责人"]

    session_response = client.get("/api/v1/auth/session")
    assert session_response.status_code == 200
    assert session_response.json()["data"]["username"] == manager_user.username


@pytest.mark.django_db
def test_login_rejects_missing_csrf(manager_user) -> None:
    """登录缺少 CSRF 时应被拒绝。"""
    client = Client(enforce_csrf_checks=True)

    response = client.post(
        "/api/v1/auth/login",
        data=json.dumps(
            {"username": manager_user.username, "password": "test-password-123"}
        ),
        content_type="application/json",
    )

    assert response.status_code == 403


@pytest.mark.django_db
def test_user_options_are_limited_to_current_organization(
    api_client,
    manager_user,
    researcher_user,
) -> None:
    """项目人员选项只返回当前组织有效用户。"""
    api_client.force_authenticate(manager_user)

    response = api_client.get("/api/v1/auth/users/options")

    assert response.status_code == 200
    assert {item["id"] for item in response.json()["data"]} == {
        str(manager_user.id),
        str(researcher_user.id),
    }
