"""将数据库引用的本地媒体原件复制到对象存储并校验内容。"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO

from botocore.exceptions import BotoCoreError, ClientError
from django.conf import settings
from django.core.files import File
from django.core.files.storage import default_storage
from django.core.management.base import BaseCommand, CommandError, CommandParser

from apps.common.object_storage import ensure_object_storage_bucket
from apps.experiments.models import ExperimentAttachment
from apps.projects.models import ProjectDocument


@dataclass(frozen=True)
class ReferencedMedia:
    """数据库引用的媒体对象。"""

    storage_name: str
    expected_size: int
    source_label: str


def _stream_sha256(stream: BinaryIO) -> str:
    """计算文件流的 SHA-256。

    Args:
        stream: 以二进制方式打开的可读文件流。

    Returns:
        十六进制 SHA-256 摘要。
    """
    digest = hashlib.sha256()
    while chunk := stream.read(1024 * 1024):
        digest.update(chunk)
    return digest.hexdigest()


def _referenced_media() -> list[ReferencedMedia]:
    """读取项目文档和实验附件的唯一存储键。

    Returns:
        按存储键排序且去重后的媒体引用。
    """
    references: dict[str, ReferencedMedia] = {}
    for storage_name, file_size in ProjectDocument.objects.exclude(file="").values_list(
        "file",
        "file_size",
    ):
        references[str(storage_name)] = ReferencedMedia(
            storage_name=str(storage_name),
            expected_size=file_size,
            source_label="项目文档",
        )
    for storage_name, file_size in ExperimentAttachment.objects.exclude(file="").values_list(
        "file",
        "file_size",
    ):
        references[str(storage_name)] = ReferencedMedia(
            storage_name=str(storage_name),
            expected_size=file_size,
            source_label="实验附件",
        )
    return [references[name] for name in sorted(references)]


class Command(BaseCommand):
    """迁移业务原件并保留本地源文件作为回滚副本。"""

    help = "将数据库引用的 MEDIA_ROOT 原件复制到对象存储并执行内容校验"

    def add_arguments(self, parser: CommandParser) -> None:
        """声明迁移命令参数。

        Args:
            parser: Django 命令参数解析器。
        """
        parser.add_argument(
            "--source-root",
            default=str(settings.MEDIA_ROOT),
            help="原本地媒体根目录，默认使用 MEDIA_ROOT",
        )
        parser.add_argument(
            "--verify-only",
            action="store_true",
            help="仅核对对象存储，不上传文件",
        )
        parser.add_argument(
            "--overwrite",
            action="store_true",
            help="对象内容与本地原件不一致时允许以本地原件覆盖",
        )

    def handle(self, *args, **options) -> None:
        """执行迁移或只读校验。

        Args:
            args: Django 命令位置参数。
            options: Django 命令选项。

        Raises:
            CommandError: 对象存储未启用、文件缺失或校验失败。
        """
        del args
        if not settings.OBJECT_STORAGE_ENABLED:
            raise CommandError("当前环境未启用对象存储，禁止执行迁移")

        source_root = Path(options["source_root"]).expanduser().resolve()
        verify_only = options["verify_only"]
        overwrite = options["overwrite"]
        if not source_root.is_dir():
            raise CommandError(f"本地媒体目录不存在：{source_root}")

        try:
            ensure_object_storage_bucket()
        except (BotoCoreError, ClientError, OSError) as error:
            raise CommandError(f"对象存储不可用：{error}") from error

        references = _referenced_media()
        uploaded_count = 0
        verified_count = 0
        failed_messages: list[str] = []

        for reference in references:
            try:
                outcome = self._migrate_reference(
                    reference=reference,
                    source_root=source_root,
                    verify_only=verify_only,
                    overwrite=overwrite,
                )
            except (BotoCoreError, ClientError, OSError, ValueError) as error:
                failed_messages.append(f"{reference.storage_name}：{error}")
                continue
            if outcome == "uploaded":
                uploaded_count += 1
            else:
                verified_count += 1

        self.stdout.write(
            f"媒体引用 {len(references)} 个，上传 {uploaded_count} 个，"
            f"已验证 {verified_count} 个，失败 {len(failed_messages)} 个"
        )
        if failed_messages:
            for message in failed_messages[:20]:
                self.stderr.write(message)
            if len(failed_messages) > 20:
                self.stderr.write(f"其余 {len(failed_messages) - 20} 个失败项已省略")
            raise CommandError("对象存储迁移或校验未通过，本地原件未删除")
        self.stdout.write(self.style.SUCCESS("对象存储迁移与内容校验通过"))

    def _migrate_reference(
        self,
        *,
        reference: ReferencedMedia,
        source_root: Path,
        verify_only: bool,
        overwrite: bool,
    ) -> str:
        """迁移单个媒体引用。

        Args:
            reference: 数据库媒体引用。
            source_root: 本地媒体根目录。
            verify_only: 是否只执行校验。
            overwrite: 内容不一致时是否覆盖对象。

        Returns:
            ``uploaded`` 或 ``verified``。

        Raises:
            ValueError: 存储键越界、源文件缺失或内容不一致。
        """
        source_path = (source_root / reference.storage_name).resolve()
        if not source_path.is_relative_to(source_root):
            raise ValueError("存储键超出本地媒体目录")

        object_exists = default_storage.exists(reference.storage_name)
        if not source_path.is_file():
            if object_exists and default_storage.size(reference.storage_name) == (
                reference.expected_size
            ):
                return "verified"
            raise ValueError(f"{reference.source_label}本地原件不存在")

        local_size = source_path.stat().st_size
        if reference.expected_size and local_size != reference.expected_size:
            raise ValueError(f"本地大小 {local_size} 与数据库记录 {reference.expected_size} 不一致")

        with source_path.open("rb") as local_stream:
            local_digest = _stream_sha256(local_stream)

        if object_exists:
            remote_size = default_storage.size(reference.storage_name)
            with default_storage.open(reference.storage_name, "rb") as remote_stream:
                remote_digest = _stream_sha256(remote_stream)
            if remote_size == local_size and remote_digest == local_digest:
                return "verified"
            if verify_only or not overwrite:
                raise ValueError("对象内容与本地原件不一致，未执行覆盖")
            default_storage.delete(reference.storage_name)
        elif verify_only:
            raise ValueError("对象不存在")

        with source_path.open("rb") as local_stream:
            saved_name = default_storage.save(
                reference.storage_name,
                File(local_stream, name=source_path.name),
            )
        if saved_name != reference.storage_name:
            raise ValueError(f"对象存储返回了非预期存储键：{saved_name}")

        remote_size = default_storage.size(reference.storage_name)
        with default_storage.open(reference.storage_name, "rb") as remote_stream:
            remote_digest = _stream_sha256(remote_stream)
        if remote_size != local_size or remote_digest != local_digest:
            raise ValueError("上传后对象大小或 SHA-256 校验失败")
        return "uploaded"
