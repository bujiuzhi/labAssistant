"""健康检查测试。"""

import pytest
from django.test import override_settings


@pytest.mark.django_db
def test_liveness_does_not_require_login(client) -> None:
    """存活检查应允许匿名访问。"""
    response = client.get("/api/v1/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert response.headers["X-Request-ID"]


@pytest.mark.django_db
def test_readiness_checks_database(client) -> None:
    """就绪检查应验证数据库连接。"""
    response = client.get("/api/v1/health/ready")

    assert response.status_code == 200
    assert response.json()["checks"]["database"] == "ok"


@pytest.mark.django_db
@override_settings(OBJECT_STORAGE_ENABLED=True)
def test_readiness_checks_object_storage(client, monkeypatch) -> None:
    """启用对象存储后就绪检查应验证私有桶。"""
    monkeypatch.setattr(
        "apps.common.views.check_object_storage_bucket",
        lambda: None,
    )

    response = client.get("/api/v1/health/ready")

    assert response.status_code == 200
    assert response.json()["checks"]["object_storage"] == "ok"


@pytest.mark.django_db
@override_settings(OBJECT_STORAGE_ENABLED=True)
def test_readiness_rejects_unavailable_object_storage(client, monkeypatch) -> None:
    """对象存储不可用时服务不应报告就绪。"""

    def unavailable_storage() -> None:
        raise OSError("connection refused")

    monkeypatch.setattr(
        "apps.common.views.check_object_storage_bucket",
        unavailable_storage,
    )

    response = client.get("/api/v1/health/ready")

    assert response.status_code == 503
    assert response.json()["checks"]["database"] == "ok"
    assert response.json()["checks"]["object_storage"] == "error"
