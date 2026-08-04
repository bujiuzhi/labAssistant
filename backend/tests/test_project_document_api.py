"""项目文档接口核心路径测试。"""

from io import BytesIO
from zipfile import ZipFile

import pytest
from apps.projects.document_formats import ODF_MIME_TYPES
from apps.projects.models import (
    Project,
    ProjectMember,
    ProjectMemberRole,
    ProjectStatus,
)
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings


@pytest.fixture
def document_project(manager_user) -> Project:
    """创建项目文档测试项目。"""
    project = Project.objects.create(
        organization=manager_user.organization,
        project_no="PRJ-2026-DOC-001",
        name="项目文档测试",
        project_type_code="环氧树脂",
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
        assert "related_content" not in list_response.json()["data"][0]

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
        assert preview_response.headers["X-Frame-Options"] == "SAMEORIGIN"
        assert preview_response.headers["Content-Security-Policy"] == (
            "frame-ancestors 'self'"
        )
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


@pytest.mark.django_db
def test_document_upload_rejects_forged_file_extension(
    api_client,
    manager_user,
    document_project,
    tmp_path,
) -> None:
    """文件扩展名与真实内容不匹配时不得保存。"""
    api_client.force_authenticate(manager_user)
    with override_settings(MEDIA_ROOT=tmp_path):
        response = api_client.post(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {
                "file": SimpleUploadedFile(
                    "伪造报告.pdf",
                    b"this-is-not-a-pdf",
                    content_type="application/pdf",
                ),
                "category": "stage_report",
                "version_label": "V1.0",
            },
            format="multipart",
        )
    assert response.status_code == 400


def _odf_payload(mime_type: str) -> bytes:
    """生成用于上传签名校验的最小 OpenDocument 压缩包。"""
    output = BytesIO()
    with ZipFile(output, "w") as archive:
        archive.writestr("mimetype", mime_type)
        archive.writestr(
            "content.xml",
            '<?xml version="1.0" encoding="UTF-8"?><office:document-content/>',
        )
    return output.getvalue()


@pytest.mark.django_db
@pytest.mark.parametrize(
    ("file_name", "content", "content_type", "expected_mime_type"),
    [
        (
            "过程照片.gif",
            (
                b"GIF89a\x01\x00\x01\x00\x80\x00\x00\x00\x00\x00"
                b"\xff\xff\xff!\xf9\x04\x01\x00\x00\x00\x00,\x00\x00"
                b"\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;"
            ),
            "application/octet-stream",
            "image/gif",
        ),
        (
            "显微图.bmp",
            b"BM" + b"\x00" * 64,
            "application/octet-stream",
            "image/bmp",
        ),
        (
            "会议记录.rtf",
            b"{\\rtf1\\ansi real laboratory note}",
            "application/octet-stream",
            "application/rtf",
        ),
        (
            "开放文档.odt",
            _odf_payload(ODF_MIME_TYPES["odt"]),
            "application/octet-stream",
            ODF_MIME_TYPES["odt"],
        ),
        (
            "开放表格.ods",
            _odf_payload(ODF_MIME_TYPES["ods"]),
            "application/octet-stream",
            ODF_MIME_TYPES["ods"],
        ),
        (
            "开放演示.odp",
            _odf_payload(ODF_MIME_TYPES["odp"]),
            "application/octet-stream",
            ODF_MIME_TYPES["odp"],
        ),
    ],
)
def test_upload_accepts_extended_preview_formats_with_trusted_mime_type(
    api_client,
    manager_user,
    document_project,
    tmp_path,
    file_name,
    content,
    content_type,
    expected_mime_type,
) -> None:
    """扩展预览格式应校验真实签名并保存服务端可信 MIME 类型。"""
    api_client.force_authenticate(manager_user)
    with override_settings(MEDIA_ROOT=tmp_path):
        response = api_client.post(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {
                "file": SimpleUploadedFile(
                    file_name,
                    content,
                    content_type=content_type,
                ),
                "category": "other",
                "version_label": "V1.0",
            },
            format="multipart",
        )

    assert response.status_code == 201
    assert response.json()["data"]["mime_type"] == expected_mime_type


@pytest.mark.django_db
def test_upload_rejects_office_archive_with_path_traversal(
    api_client,
    manager_user,
    document_project,
    tmp_path,
) -> None:
    """包含目录穿越路径的办公压缩包不得进入浏览器预览链路。"""
    payload = BytesIO()
    with ZipFile(payload, "w") as archive:
        archive.writestr("[Content_Types].xml", "<Types/>")
        archive.writestr("word/document.xml", "<document/>")
        archive.writestr("../outside.xml", "<unsafe/>")

    api_client.force_authenticate(manager_user)
    with override_settings(MEDIA_ROOT=tmp_path):
        response = api_client.post(
            f"/api/v1/projects/{document_project.project_no}/documents",
            {
                "file": SimpleUploadedFile(
                    "不安全文档.docx",
                    payload.getvalue(),
                    content_type="application/octet-stream",
                ),
                "category": "other",
                "version_label": "V1.0",
            },
            format="multipart",
        )

    assert response.status_code == 400
