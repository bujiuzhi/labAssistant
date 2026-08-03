"""电子实验记录本接口核心路径测试。"""

import pytest
from apps.experiments.models import (
    Experiment,
    ExperimentParticipant,
    ExperimentParticipantRole,
    ExperimentStatus,
)
from apps.projects.models import Project, ProjectMember, ProjectMemberRole
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings


@pytest.fixture
def visible_project(manager_user, researcher_user) -> Project:
    """创建研究人员可见项目。"""
    project = Project.objects.create(
        organization=manager_user.organization,
        project_no="PRJ-2026-ELN-001",
        name="电子实验记录本测试项目",
        project_type_code="聚酰亚胺",
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
    ProjectMember.objects.create(
        organization=manager_user.organization,
        project=project,
        user=researcher_user,
        member_role=ProjectMemberRole.RESEARCHER,
        created_by=manager_user,
    )
    return project


def experiment_payload(project_id) -> dict:
    """生成实验计划和记录请求。

    Args:
        project_id: 关联项目 ID。

    Returns:
        实验创建数据。
    """
    return {
        "project_id": str(project_id),
        "name": "PLA/PBAT配方复核",
        "experiment_type": "聚合",
        "purpose": "复核关键配比与混炼窗口",
        "estimated_start": "2026-07-24T09:00:00+08:00",
        "estimated_end": "2026-07-24T18:00:00+08:00",
        "formula_columns": [
            {"id": "material", "label": "原料名称"},
            {"id": "amount", "label": "用量"},
        ],
        "formula_rows": [{"material": "PLA", "amount": "60.0"}],
        "process_text": "09:00 完成称量。",
        "result_text": "",
    }


@pytest.mark.django_db
def test_create_and_update_experiment_record(
    api_client,
    researcher_user,
    visible_project,
) -> None:
    """研究人员可创建实验并持久化动态配方与过程记录。"""
    api_client.force_authenticate(researcher_user)

    create_response = api_client.post(
        "/api/v1/experiments",
        experiment_payload(visible_project.id),
        format="json",
    )
    assert create_response.status_code == 201
    created = create_response.json()["data"]
    assert created["status"] == ExperimentStatus.NOT_STARTED
    assert created["record"]["formula_rows"][0]["material"] == "PLA"

    update_response = api_client.patch(
        f"/api/v1/experiments/{created['experiment_no']}",
        {
            "process_text": "09:00 完成称量。\n10:20 混炼稳定。",
            "result_text": "样条外观完整。",
        },
        format="json",
        HTTP_IF_MATCH='"1"',
    )

    assert update_response.status_code == 200
    updated = update_response.json()["data"]
    assert updated["version"] == 2
    assert "混炼稳定" in updated["record"]["process_text"]
    assert updated["record"]["result_text"] == "样条外观完整。"


@pytest.mark.django_db
def test_experimenter_sees_all_experiments_in_visible_project(
    api_client,
    manager_user,
    researcher_user,
    visible_project,
) -> None:
    """项目可见后应同时看到项目下全部实验。"""
    visible = Experiment.objects.create(
        organization=manager_user.organization,
        project=visible_project,
        experiment_no="EXP-2026-901",
        name="可见实验",
        experiment_type="其他",
        owner=manager_user,
        created_by=manager_user,
        updated_by=manager_user,
    )
    ExperimentParticipant.objects.create(
        organization=manager_user.organization,
        experiment=visible,
        user=researcher_user,
        participant_role=ExperimentParticipantRole.PARTICIPANT,
        created_by=manager_user,
    )
    hidden = Experiment.objects.create(
        organization=manager_user.organization,
        project=visible_project,
        experiment_no="EXP-2026-902",
        name="不可见实验",
        experiment_type="其他",
        owner=manager_user,
        created_by=manager_user,
        updated_by=manager_user,
    )

    api_client.force_authenticate(researcher_user)
    response = api_client.get("/api/v1/experiments?page_size=100")

    assert response.status_code == 200
    returned_ids = {item["id"] for item in response.json()["data"]}
    assert str(visible.id) in returned_ids
    assert str(hidden.id) in returned_ids


@pytest.mark.django_db
def test_copy_and_status_transition(
    api_client,
    researcher_user,
    visible_project,
) -> None:
    """复制计划后可开始并完成实验。"""
    api_client.force_authenticate(researcher_user)
    created = api_client.post(
        "/api/v1/experiments",
        experiment_payload(visible_project.id),
        format="json",
    ).json()["data"]

    copy_response = api_client.post(
        f"/api/v1/experiments/{created['experiment_no']}/copy",
        {},
        format="json",
    )
    assert copy_response.status_code == 201
    copied = copy_response.json()["data"]
    assert copied["name"].endswith("-副本")
    assert copied["record"]["result_text"] == ""

    start_response = api_client.post(
        f"/api/v1/experiments/{copied['experiment_no']}/transition",
        {"target_status": ExperimentStatus.IN_PROGRESS},
        format="json",
        HTTP_IF_MATCH='"1"',
    )
    assert start_response.status_code == 200
    started = start_response.json()["data"]
    assert started["status"] == ExperimentStatus.IN_PROGRESS

    incomplete_response = api_client.post(
        f"/api/v1/experiments/{copied['experiment_no']}/transition",
        {"target_status": ExperimentStatus.COMPLETED},
        format="json",
        HTTP_IF_MATCH=f'"{started["version"]}"',
    )
    assert incomplete_response.status_code == 400

    saved_response = api_client.patch(
        f"/api/v1/experiments/{copied['experiment_no']}",
        {"result_text": "实验结果达到阶段要求。"},
        format="json",
        HTTP_IF_MATCH=f'"{started["version"]}"',
    )
    saved = saved_response.json()["data"]
    complete_response = api_client.post(
        f"/api/v1/experiments/{copied['experiment_no']}/transition",
        {"target_status": ExperimentStatus.COMPLETED},
        format="json",
        HTTP_IF_MATCH=f'"{saved["version"]}"',
    )
    assert complete_response.status_code == 200
    assert complete_response.json()["data"]["status"] == ExperimentStatus.COMPLETED


@pytest.mark.django_db
def test_completed_experiment_can_save_revision(
    api_client,
    researcher_user,
    visible_project,
) -> None:
    """已完成实验按需求仍可保存记录修订。"""
    api_client.force_authenticate(researcher_user)
    created = api_client.post(
        "/api/v1/experiments",
        experiment_payload(visible_project.id),
        format="json",
    ).json()["data"]
    Experiment.objects.filter(id=created["id"]).update(
        status=ExperimentStatus.COMPLETED
    )

    response = api_client.patch(
        f"/api/v1/experiments/{created['experiment_no']}",
        {"result_text": "完成后补充复核结论"},
        format="json",
        HTTP_IF_MATCH='"1"',
    )

    assert response.status_code == 200
    assert response.json()["data"]["record"]["result_text"] == "完成后补充复核结论"


@pytest.mark.django_db
def test_upload_experiment_result_attachment(
    api_client,
    researcher_user,
    visible_project,
    tmp_path,
) -> None:
    """结果附件应保存真实文件并可通过受控地址读取。"""
    api_client.force_authenticate(researcher_user)
    created = api_client.post(
        "/api/v1/experiments",
        experiment_payload(visible_project.id),
        format="json",
    ).json()["data"]
    with override_settings(MEDIA_ROOT=tmp_path):
        upload = api_client.post(
            f"/api/v1/experiments/{created['experiment_no']}/attachments",
            {
                "kind": "result_file",
                "file": SimpleUploadedFile(
                    "result.csv",
                    b"sample,value\nA,1.2\n",
                    content_type="text/csv",
                ),
            },
        )

        assert upload.status_code == 201
        attachment = upload.json()["data"]["record"]["result_files"][0]
        assert attachment["name"] == "result.csv"
        content = api_client.get(attachment["url"])
        assert content.status_code == 200
        assert b"sample,value" in b"".join(content.streaming_content)

        delete_response = api_client.delete(
            f"/api/v1/experiments/{created['experiment_no']}/attachments/"
            f"{attachment['id']}"
        )
        assert delete_response.status_code == 200
        assert delete_response.json()["data"]["record"]["result_files"] == []


@pytest.mark.django_db
def test_experiment_create_validates_required_fields_and_default_formula(
    api_client,
    researcher_user,
    visible_project,
) -> None:
    """实验类型、实验目的和默认四列两行应与需求一致。"""
    api_client.force_authenticate(researcher_user)
    invalid = api_client.post(
        "/api/v1/experiments",
        {
            "project_id": str(visible_project.id),
            "name": "缺少目的的实验",
            "experiment_type": "配方筛选",
            "purpose": "",
        },
        format="json",
    )
    assert invalid.status_code == 400

    created_response = api_client.post(
        "/api/v1/experiments",
        {
            "project_id": str(visible_project.id),
            "name": "默认配方表实验",
            "experiment_type": "其他",
            "purpose": "验证默认记录结构",
        },
        format="json",
    )
    assert created_response.status_code == 201
    record = created_response.json()["data"]["record"]
    assert len(record["formula_columns"]) == 4
    assert len(record["formula_rows"]) == 2


@pytest.mark.django_db
def test_extra_process_content_is_limited_to_one_thousand_characters(
    api_client,
    researcher_user,
    visible_project,
) -> None:
    """单个新增实验过程不得超过一千字。"""
    api_client.force_authenticate(researcher_user)
    payload = experiment_payload(visible_project.id)
    payload["extra_processes"] = [
        {"id": "process-1", "name": "补充过程", "content": "测" * 1001}
    ]
    response = api_client.post("/api/v1/experiments", payload, format="json")
    assert response.status_code == 400
