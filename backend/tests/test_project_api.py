"""项目接口核心路径测试。"""

import uuid

import pytest
from apps.projects.models import (
    Project,
    ProjectMember,
    ProjectMemberRole,
    ProjectStatus,
)


def project_payload(owner_id) -> dict:
    """生成项目创建请求。

    Args:
        owner_id: 项目负责人 ID。

    Returns:
        项目创建数据。
    """
    return {
        "name": "高分子复合材料验证",
        "project_type_code": "material_research",
        "description": "验证材料配比和关键工艺窗口",
        "owner_id": str(owner_id),
        "planned_start_date": "2026-07-24",
        "planned_end_date": "2026-10-31",
    }


@pytest.mark.django_db
def test_create_project_is_idempotent(api_client, manager_user) -> None:
    """重复幂等请求只创建一个项目。"""
    api_client.force_authenticate(manager_user)
    idempotency_key = str(uuid.uuid4())

    first_response = api_client.post(
        "/api/v1/projects",
        project_payload(manager_user.id),
        format="json",
        HTTP_IDEMPOTENCY_KEY=idempotency_key,
    )
    second_response = api_client.post(
        "/api/v1/projects",
        project_payload(manager_user.id),
        format="json",
        HTTP_IDEMPOTENCY_KEY=idempotency_key,
    )

    assert first_response.status_code == 201
    assert second_response.status_code == 200
    assert first_response.json()["data"]["id"] == second_response.json()["data"]["id"]
    assert Project.objects.count() == 1
    assert ProjectMember.objects.get().member_role == ProjectMemberRole.OWNER


@pytest.mark.django_db
def test_researcher_only_sees_member_projects(
    api_client,
    manager_user,
    researcher_user,
) -> None:
    """研究人员只能看到参与项目。"""
    api_client.force_authenticate(manager_user)
    first = api_client.post(
        "/api/v1/projects",
        project_payload(manager_user.id),
        format="json",
        HTTP_IDEMPOTENCY_KEY=str(uuid.uuid4()),
    ).json()["data"]
    api_client.post(
        "/api/v1/projects",
        {
            **project_payload(manager_user.id),
            "name": "不可见项目",
        },
        format="json",
        HTTP_IDEMPOTENCY_KEY=str(uuid.uuid4()),
    )
    project = Project.objects.get(id=first["id"])
    ProjectMember.objects.create(
        organization=manager_user.organization,
        project=project,
        user=researcher_user,
        member_role=ProjectMemberRole.RESEARCHER,
        created_by=manager_user,
    )

    api_client.force_authenticate(researcher_user)
    response = api_client.get("/api/v1/projects")

    assert response.status_code == 200
    assert response.json()["meta"]["total"] == 1
    assert response.json()["data"][0]["id"] == first["id"]


@pytest.mark.django_db
def test_update_project_rejects_stale_version(api_client, manager_user) -> None:
    """过期版本不得覆盖最新项目。"""
    api_client.force_authenticate(manager_user)
    created = api_client.post(
        "/api/v1/projects",
        project_payload(manager_user.id),
        format="json",
        HTTP_IDEMPOTENCY_KEY=str(uuid.uuid4()),
    ).json()["data"]

    update_response = api_client.patch(
        f"/api/v1/projects/{created['id']}",
        {"name": "更新后的项目名称"},
        format="json",
        HTTP_IF_MATCH='"1"',
    )
    stale_response = api_client.patch(
        f"/api/v1/projects/{created['id']}",
        {"name": "错误覆盖名称"},
        format="json",
        HTTP_IF_MATCH='"1"',
    )

    assert update_response.status_code == 200
    assert update_response.headers["ETag"] == '"2"'
    assert stale_response.status_code == 412
    assert stale_response.json()["code"] == "RESOURCE_VERSION_CONFLICT"


@pytest.mark.django_db
def test_update_project_by_business_number_persists_editable_fields_and_members(
    api_client,
    manager_user,
    researcher_user,
) -> None:
    """业务编号更新应持久化基础信息、人员和里程碑。"""
    api_client.force_authenticate(manager_user)
    created = api_client.post(
        "/api/v1/projects",
        {
            **project_payload(manager_user.id),
            "member_ids": [str(researcher_user.id)],
        },
        format="json",
        HTTP_IDEMPOTENCY_KEY=str(uuid.uuid4()),
    ).json()["data"]

    response = api_client.patch(
        f"/api/v1/projects/{created['project_no']}",
        {
            "name": "高分子复合材料工程化验证",
            "project_type_code": "功能材料",
            "objectives": ["完成三轮配方筛选", "形成工艺窗口"],
            "milestones": [
                {
                    "date": "2026-08-31",
                    "name": "完成阶段评审",
                    "state": "current",
                }
            ],
            "member_ids": [str(researcher_user.id)],
        },
        format="json",
        HTTP_IF_MATCH='"1"',
    )

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["name"] == "高分子复合材料工程化验证"
    assert data["objectives"] == ["完成三轮配方筛选", "形成工艺窗口"]
    assert data["milestones"][0]["name"] == "完成阶段评审"
    assert {member["user_id"] for member in data["members"]} == {
        str(manager_user.id),
        str(researcher_user.id),
    }
    assert ProjectMember.objects.filter(
        project_id=created["id"],
        user=researcher_user,
        member_role=ProjectMemberRole.RESEARCHER,
    ).exists()


@pytest.mark.django_db
def test_archived_project_cannot_be_edited(api_client, manager_user) -> None:
    """已归档项目必须保持只读。"""
    api_client.force_authenticate(manager_user)
    created = api_client.post(
        "/api/v1/projects",
        project_payload(manager_user.id),
        format="json",
        HTTP_IDEMPOTENCY_KEY=str(uuid.uuid4()),
    ).json()["data"]
    Project.objects.filter(id=created["id"]).update(status=ProjectStatus.ARCHIVED)

    response = api_client.patch(
        f"/api/v1/projects/{created['project_no']}",
        {"name": "不应保存的名称"},
        format="json",
        HTTP_IF_MATCH='"1"',
    )

    assert response.status_code == 409
    assert Project.objects.get(id=created["id"]).name == "高分子复合材料验证"
