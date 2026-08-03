"""对象存储初始化、迁移和内容校验测试。"""

from pathlib import Path

import pytest
from apps.projects.models import Project, ProjectDocument, ProjectDocumentCategory
from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import override_settings


def test_bootstrap_object_storage_rejects_disabled_configuration() -> None:
    """未启用对象存储时初始化命令应返回可诊断的命令错误。"""
    with (
        override_settings(OBJECT_STORAGE_ENABLED=False),
        pytest.raises(CommandError, match="当前环境未启用对象存储"),
    ):
        call_command("bootstrap_object_storage")


@pytest.mark.django_db
def test_migrate_referenced_media_and_verify_content(
    manager_user,
    tmp_path: Path,
    monkeypatch,
) -> None:
    """迁移命令应复制数据库引用原件并可重复执行内容校验。"""
    source_root = tmp_path / "source"
    destination_root = tmp_path / "object-storage"
    storage_name = (
        "project-documents/"
        f"{manager_user.organization_id}/00000000-0000-0000-0000-000000000001/"
        "document.txt"
    )
    source_path = source_root / storage_name
    source_path.parent.mkdir(parents=True)
    expected_content = b"real-object-storage-content"
    source_path.write_bytes(expected_content)

    project = Project.objects.create(
        organization=manager_user.organization,
        project_no="PRJ-2026-STORE-001",
        name="对象存储迁移测试",
        project_type_code="环氧树脂",
        owner=manager_user,
        created_by=manager_user,
        updated_by=manager_user,
    )
    ProjectDocument.objects.create(
        organization=manager_user.organization,
        project=project,
        name="document.txt",
        file=storage_name,
        extension="txt",
        mime_type="text/plain",
        file_size=len(expected_content),
        category=ProjectDocumentCategory.OTHER,
        uploaded_by=manager_user,
        updated_by=manager_user,
    )

    monkeypatch.setattr(
        "apps.common.management.commands.migrate_media_to_object_storage."
        "ensure_object_storage_bucket",
        lambda: False,
    )
    test_storages = {
        "default": {
            "BACKEND": "django.core.files.storage.FileSystemStorage",
            "OPTIONS": {"location": str(destination_root)},
        },
        "staticfiles": {
            "BACKEND": "django.contrib.staticfiles.storage.StaticFilesStorage",
        },
    }
    with override_settings(
        OBJECT_STORAGE_ENABLED=True,
        STORAGES=test_storages,
    ):
        call_command(
            "migrate_media_to_object_storage",
            source_root=str(source_root),
        )
        call_command(
            "migrate_media_to_object_storage",
            source_root=str(source_root),
            verify_only=True,
        )

    assert (destination_root / storage_name).read_bytes() == expected_content
