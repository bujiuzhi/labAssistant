"""健康检查测试。"""

import pytest


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
