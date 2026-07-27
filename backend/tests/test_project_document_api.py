"""项目文档接口核心路径测试。"""

import pytest
from apps.projects.models import Project, ProjectMember, ProjectMemberRole, ProjectStatus
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings


@pytest.fixture
def document_project(manager_user) -> Project:
    """创建项目文档测试项目。"""
    project = Project.objects.create(
        organization=manager_user.organization,
        project_no="PRJ-2026-DOC-001",
        name="项目文档测试",
        project_type_code="功能材料",
        owner=manager_user,
        status=ProjectStatus.ACTIVE,
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
    return project


@pytest.mark.django_db
def test_upload_list_and_download_project_document(
    api_client,
    manager_user,
    document_project,
    tmp_path,
) -> None:
    """上传后应可按项目列出并下载原始内容。"""
    api_client.force_authenticate(manager_user)
    uploaded = SimpleUploadedFile(
        "实验方案.txt",
        b"project-document-content",
        content_type="text/plain",
    )

    with override_settings(MEDIA_ROOT=tmp_path):
        upload_response = api_client.post(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {
                "file": uploaded,
                "category": "experiment_plan",
                "related_content": "EXP-2026-018",
                "version_label": "V1.0",
            },
            format="multipart",
        )
        assert upload_response.status_code == 201
        document = upload_response.json()["data"]

        list_response = api_client.get(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {"search": "实验方案"},
        )
        assert list_response.status_code == 200
        assert list_response.json()["meta"]["total"] == 1
        assert list_response.json()["data"][0]["category_label"] == "实验方案"

        content_response = api_client.get(
            f"/api/v1/projects/{document_project.project_no}/documents/"
            f"{document['id']}/content?download=1"
        )
        assert content_response.status_code == 200
        assert b"".join(content_response.streaming_content) == b"project-document-content"
        assert "attachment" in content_response.headers["Content-Disposition"]


@pytest.mark.django_db
def test_archived_project_rejects_document_upload(
    api_client,
    manager_user,
    document_project,
    tmp_path,
) -> None:
    """归档项目不得继续上传文档。"""
    document_project.status = ProjectStatus.ARCHIVED
    document_project.save(update_fields=["status", "updated_at"])
    api_client.force_authenticate(manager_user)

    with override_settings(MEDIA_ROOT=tmp_path):
        response = api_client.post(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {
                "file": SimpleUploadedFile("归档后.txt", b"blocked"),
                "category": "other",
                "related_content": "项目整体",
                "version_label": "V1.0",
            },
            format="multipart",
        )

    assert response.status_code == 409


@pytest.mark.django_db
def test_project_document_preview_returns_original_pdf(
    api_client,
    manager_user,
    document_project,
    tmp_path,
) -> None:
    """PDF 在线预览应返回真实原文件而不是模拟内容。"""
    api_client.force_authenticate(manager_user)
    uploaded = SimpleUploadedFile(
        "阶段报告.pdf",
        b"%PDF-1.4\nactual-project-document\n%%EOF",
        content_type="application/pdf",
    )

    with override_settings(MEDIA_ROOT=tmp_path):
        upload_response = api_client.post(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {
                "file": uploaded,
                "category": "stage_report",
                "related_content": "项目整体",
                "version_label": "V1.0",
            },
            format="multipart",
        )
        document = upload_response.json()["data"]

        preview_response = api_client.get(
            f"/api/v1/projects/{document_project.project_no}/documents/"
            f"{document['id']}/preview"
        )

        assert preview_response.status_code == 200
        assert preview_response.headers["Content-Type"] == "application/pdf"
        assert b"".join(preview_response.streaming_content) == (
            b"%PDF-1.4\nactual-project-document\n%%EOF"
        )


@pytest.mark.django_db
def test_project_document_preview_converts_text_file(
    api_client,
    manager_user,
    document_project,
    tmp_path,
) -> None:
    """可转换文档预览应由服务器生成真实 PDF。"""
    api_client.force_authenticate(manager_user)
    with override_settings(MEDIA_ROOT=tmp_path):
        upload_response = api_client.post(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {
                "file": SimpleUploadedFile(
                    "实验摘要.txt",
                    "真实实验摘要内容".encode(),
                    content_type="text/plain",
                ),
                "category": "experiment_plan",
                "related_content": "EXP-2026-020",
                "version_label": "V1.0",
            },
            format="multipart",
        )
        document = upload_response.json()["data"]

        preview_response = api_client.get(
            f"/api/v1/projects/{document_project.project_no}/documents/"
            f"{document['id']}/preview"
        )

        assert preview_response.status_code == 200
        assert preview_response.headers["Content-Type"] == "application/pdf"
        assert b"".join(preview_response.streaming_content).startswith(b"%PDF-")
