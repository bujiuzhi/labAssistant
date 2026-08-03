"""项目接口核心路径测试。"""

import uuid

import pytest
from apps.experiments.models import (
    Experiment,
    ExperimentParticipant,
    ExperimentParticipantRole,
    ExperimentStatus,
)
from apps.identity.models import Organization, User
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
        "project_type_code": "聚酰亚胺",
        "description": "验证材料配比和关键工艺窗口",
        "owner_id": str(owner_id),
        "planned_start_date": "2026-07-24T09:00:00+08:00",
        "planned_end_date": "2026-10-31T18:00:00+08:00",
        "objectives": ["完成三轮配方筛选", "形成稳定工艺窗口"],
        "milestones": [
            {
                "date": "2026-08-31",
                "name": "完成阶段评审",
                "state": "current",
            }
        ],
    }


@pytest.mark.django_db
def test_dashboard_returns_realtime_counts_and_follow_state(api_client, manager_user) -> None:
    """总览统计与项目关注状态必须来自真实数据库。"""
    project = Project.objects.create(
        organization=manager_user.organization,
        project_no="PRJ-2026-DASH-001",
        name="实时总览测试项目",
        project_type_code="聚酰亚胺",
        status=ProjectStatus.ACTIVE,
        owner=manager_user,
        created_by=manager_user,
        updated_by=manager_user,
    )
    ProjectMember.objects.create(
        organization=manager_user.organization,
        project=project,
        user=manager_user,
        member_role=ProjectMemberRole.OWNER,
        created_by=manager_user,
    )
    api_client.force_authenticate(manager_user)

    summary = api_client.get("/api/v1/dashboard")
    assert summary.status_code == 200
    assert summary.json()["data"]["project_metrics"]["total"] == 1
    follow = api_client.post(f"/api/v1/projects/{project.project_no}/follow", {}, format="json")
    assert follow.status_code == 200
    refreshed = api_client.get("/api/v1/dashboard").json()["data"]
    assert refreshed["active_projects"][0]["is_followed"] is True


@pytest.mark.django_db
def test_dashboard_aggregates_do_not_duplicate_joined_members(
    api_client,
    manager_user,
    researcher_user,
) -> None:
    """总览聚合不得因项目成员和实验参与人关联而重复计数。"""
    project = Project.objects.create(
        organization=manager_user.organization,
        project_no="PRJ-2026-DASH-002",
        name="聚合去重测试项目",
        project_type_code="聚酰亚胺",
        status=ProjectStatus.ACTIVE,
        owner=manager_user,
        created_by=manager_user,
        updated_by=manager_user,
    )
    for user, member_role in [
        (manager_user, ProjectMemberRole.OWNER),
        (researcher_user, ProjectMemberRole.RESEARCHER),
    ]:
        ProjectMember.objects.create(
            organization=manager_user.organization,
            project=project,
            user=user,
            member_role=member_role,
            created_by=manager_user,
        )
    experiment = Experiment.objects.create(
        organization=manager_user.organization,
        project=project,
        experiment_no="EXP-2026-DASH-001",
        name="聚合去重测试实验",
        experiment_type="聚合",
        status=ExperimentStatus.IN_PROGRESS,
        owner=manager_user,
        created_by=manager_user,
        updated_by=manager_user,
    )
    for user, participant_role in [
        (manager_user, ExperimentParticipantRole.OWNER),
        (researcher_user, ExperimentParticipantRole.PARTICIPANT),
    ]:
        ExperimentParticipant.objects.create(
            organization=manager_user.organization,
            experiment=experiment,
            user=user,
            participant_role=participant_role,
            created_by=manager_user,
        )
    api_client.force_authenticate(manager_user)

    data = api_client.get("/api/v1/dashboard").json()["data"]

    assert data["project_metrics"] == {
        "total": 1,
        "active": 1,
        "archived": 0,
        "at_risk": 0,
    }
    assert data["experiment_metrics"] == {
        "total": 1,
        "in_progress": 1,
        "completed": 0,
    }
    assert data["type_distribution"] == [
        {
            "name": "聚酰亚胺",
            "project_count": 1,
            "experiment_count": 1,
        },
        {
            "name": "环氧树脂",
            "project_count": 0,
            "experiment_count": 0,
        },
    ]


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
def test_experimenter_sees_all_projects_in_own_organization(
    api_client,
    manager_user,
    researcher_user,
) -> None:
    """实验员按组织范围看到本组织全部项目，而非只看参与项目。"""
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
    api_client.force_authenticate(researcher_user)
    response = api_client.get("/api/v1/projects")

    assert response.status_code == 200
    assert response.json()["meta"]["total"] == 2
    assert first["id"] in {item["id"] for item in response.json()["data"]}


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
            "project_type_code": "环氧树脂",
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


@pytest.mark.django_db
def test_project_filters_archive_and_operation_logs(api_client, manager_user) -> None:
    """聚合筛选、归档和关键操作日志应来自真实持久化数据。"""
    api_client.force_authenticate(manager_user)
    created = api_client.post(
        "/api/v1/projects",
        project_payload(manager_user.id),
        format="json",
        HTTP_IDEMPOTENCY_KEY=str(uuid.uuid4()),
    ).json()["data"]

    filtered = api_client.get(
        "/api/v1/projects",
        {"status": "not_started", "project_type": "聚酰亚胺"},
    )
    assert filtered.status_code == 200
    assert filtered.json()["meta"]["total"] == 1

    archive_response = api_client.post(
        f"/api/v1/projects/{created['id']}/archive",
        {},
        format="json",
        HTTP_IF_MATCH='"1"',
    )
    assert archive_response.status_code == 200
    assert archive_response.json()["data"]["status"] == ProjectStatus.ARCHIVED

    logs_response = api_client.get(
        f"/api/v1/projects/{created['id']}/operation-logs"
    )
    assert logs_response.status_code == 200
    assert [item["action_type"] for item in logs_response.json()["data"]] == [
        "project_archived",
        "project_created",
    ]


@pytest.mark.django_db
def test_creator_is_automatically_added_when_owner_is_another_user(
    api_client,
    manager_user,
    researcher_user,
) -> None:
    """创建人不是负责人时仍应自动成为项目成员。"""
    api_client.force_authenticate(manager_user)
    response = api_client.post(
        "/api/v1/projects",
        project_payload(researcher_user.id),
        format="json",
        HTTP_IDEMPOTENCY_KEY=str(uuid.uuid4()),
    )

    assert response.status_code == 201
    members = {
        item["user_id"]: item["member_role"]
        for item in response.json()["data"]["members"]
    }
    assert members[str(researcher_user.id)] == ProjectMemberRole.OWNER
    assert members[str(manager_user.id)] == ProjectMemberRole.RESEARCHER


@pytest.mark.django_db
def test_project_scope_includes_descendant_organizations_and_external_membership(
    api_client,
    organization,
    manager_user,
    researcher_user,
) -> None:
    """项目范围应合并本组织下级组织与跨组织项目成员关系。"""
    child = Organization.objects.create(
        organization_code="TEST-CHILD",
        name="下级实验室",
        parent=organization,
    )
    external = Organization.objects.create(
        organization_code="TEST-EXTERNAL",
        name="外部协作组织",
    )
    child_owner = User.objects.create_user(
        organization=child,
        username="child-owner",
        display_name="下级负责人",
        password="test-password-123",
    )
    external_owner = User.objects.create_user(
        organization=external,
        username="external-owner",
        display_name="外部负责人",
        password="test-password-123",
    )
    child_project = Project.objects.create(
        organization=child,
        project_no="PRJ-2026-CHILD-001",
        name="下级组织项目",
        project_type_code="聚酰亚胺",
        owner=child_owner,
        created_by=child_owner,
        updated_by=child_owner,
    )
    external_project = Project.objects.create(
        organization=external,
        project_no="PRJ-2026-EXT-001",
        name="跨组织协作项目",
        project_type_code="环氧树脂",
        owner=external_owner,
        created_by=external_owner,
        updated_by=external_owner,
    )
    ProjectMember.objects.create(
        organization=external,
        project=external_project,
        user=researcher_user,
        member_role=ProjectMemberRole.RESEARCHER,
        created_by=external_owner,
    )

    api_client.force_authenticate(manager_user)
    manager_ids = {
        item["id"]
        for item in api_client.get("/api/v1/projects?page_size=100").json()["data"]
    }
    assert str(child_project.id) in manager_ids
    assert str(external_project.id) not in manager_ids

    api_client.force_authenticate(researcher_user)
    researcher_ids = {
        item["id"]
        for item in api_client.get("/api/v1/projects?page_size=100").json()["data"]
    }
    assert str(child_project.id) in researcher_ids
    assert str(external_project.id) in researcher_ids
