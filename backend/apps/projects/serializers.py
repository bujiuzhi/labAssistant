"""项目、操作日志与项目文档接口序列化器。"""

from pathlib import Path, PurePosixPath
from zipfile import BadZipFile, ZipFile

from rest_framework import serializers

from apps.common.models import BusinessOperationLog
from apps.identity.models import User, UserStatus

from .document_formats import (
    ALLOWED_DOCUMENT_EXTENSIONS,
    ODF_MIME_TYPES,
    OOXML_REQUIRED_PREFIXES,
)
from .models import Project, ProjectDocument, ProjectDocumentCategory

PROJECT_TYPE_CHOICES = ["聚酰亚胺", "环氧树脂"]
MAX_OFFICE_ARCHIVE_ENTRIES = 10_000
MAX_OFFICE_ARCHIVE_UNCOMPRESSED_BYTES = 512 * 1024 * 1024


class ProjectMilestoneSerializer(serializers.Serializer):
    """项目里程碑写入结构。"""

    date = serializers.DateField()
    name = serializers.CharField(min_length=1, max_length=500)
    state = serializers.ChoiceField(choices=["todo", "current", "done"])


class ProjectSerializer(serializers.ModelSerializer):
    """项目读取结构。"""

    owner_id = serializers.UUIDField(read_only=True)
    owner_display_name = serializers.CharField(source="owner.display_name", read_only=True)
    members = serializers.SerializerMethodField()

    class Meta:
        """序列化字段。"""

        model = Project
        fields = [
            "id",
            "project_no",
            "name",
            "project_type_code",
            "description",
            "current_stage",
            "progress_percent",
            "document_count",
            "experiment_count",
            "data_resource_count",
            "objectives",
            "milestones",
            "status",
            "owner_id",
            "owner_display_name",
            "members",
            "planned_start_date",
            "planned_end_date",
            "actual_end_at",
            "archived_at",
            "version",
            "created_at",
            "updated_at",
        ]

    def get_members(self, instance: Project) -> list[dict[str, str]]:
        """返回项目成员摘要。

        Args:
            instance: 项目对象。

        Returns:
            项目成员列表。
        """
        return [
            {
                "user_id": str(member.user_id),
                "display_name": member.user.display_name,
                "member_role": member.member_role,
            }
            for member in sorted(
                instance.members.all(),
                key=lambda item: (item.member_role != "owner", item.user.display_name),
            )
        ]


class ProjectWriteSerializer(serializers.Serializer):
    """项目创建和更新字段。"""

    name = serializers.CharField(min_length=1, max_length=200, required=False)
    project_type_code = serializers.ChoiceField(
        choices=PROJECT_TYPE_CHOICES,
        required=False,
    )
    description = serializers.CharField(max_length=10000, allow_blank=True, required=False)
    current_stage = serializers.CharField(max_length=64, required=False)
    objectives = serializers.ListField(
        child=serializers.CharField(min_length=1, max_length=1000),
        allow_empty=False,
        required=False,
    )
    milestones = serializers.ListField(
        child=ProjectMilestoneSerializer(),
        allow_empty=False,
        required=False,
    )
    member_ids = serializers.ListField(
        child=serializers.UUIDField(),
        allow_empty=True,
        required=False,
    )
    owner_id = serializers.PrimaryKeyRelatedField(
        source="owner",
        queryset=User.objects.filter(status=UserStatus.ACTIVE),
        required=False,
    )
    planned_start_date = serializers.DateTimeField(allow_null=True, required=False)
    planned_end_date = serializers.DateTimeField(allow_null=True, required=False)

    def validate(self, attrs):
        """校验计划日期顺序。

        Args:
            attrs: 已完成字段级校验的数据。

        Returns:
            校验后的数据。
        """
        start_date = attrs.get("planned_start_date")
        end_date = attrs.get("planned_end_date")
        instance = self.context.get("project")
        if instance:
            start_date = attrs.get("planned_start_date", instance.planned_start_date)
            end_date = attrs.get("planned_end_date", instance.planned_end_date)
        if start_date and end_date and end_date < start_date:
            raise serializers.ValidationError(
                {"planned_end_date": ["计划结束日期不能早于开始日期"]}
            )
        milestones = attrs.get("milestones")
        if milestones and sum(item["state"] == "current" for item in milestones) > 1:
            raise serializers.ValidationError(
                {"milestones": ["只能设置一个当前阶段里程碑"]}
            )
        if milestones is not None:
            attrs["milestones"] = [
                {**item, "date": item["date"].isoformat()}
                for item in milestones
            ]
        member_ids = attrs.get("member_ids")
        if member_ids is not None and len(member_ids) != len(set(member_ids)):
            raise serializers.ValidationError(
                {"member_ids": ["项目成员不能重复"]}
            )
        return attrs


class ProjectCreateSerializer(ProjectWriteSerializer):
    """项目创建请求。"""

    name = serializers.CharField(min_length=1, max_length=200)
    project_type_code = serializers.ChoiceField(choices=PROJECT_TYPE_CHOICES)
    owner_id = serializers.PrimaryKeyRelatedField(
        source="owner",
        queryset=User.objects.filter(status=UserStatus.ACTIVE),
    )
    planned_start_date = serializers.DateTimeField()
    planned_end_date = serializers.DateTimeField()
    objectives = serializers.ListField(
        child=serializers.CharField(min_length=1, max_length=1000),
        allow_empty=False,
    )
    milestones = serializers.ListField(
        child=ProjectMilestoneSerializer(),
        allow_empty=False,
    )


class ProjectUpdateSerializer(ProjectWriteSerializer):
    """项目部分更新请求。"""

    def validate(self, attrs):
        """确保至少更新一个字段。

        Args:
            attrs: 已完成字段级校验的数据。

        Returns:
            校验后的数据。
        """
        attrs = super().validate(attrs)
        if not attrs:
            raise serializers.ValidationError("至少提供一个待更新字段")
        return attrs


class ProjectOperationLogSerializer(serializers.ModelSerializer):
    """项目操作日志读取结构。"""

    actor_display_name = serializers.CharField(
        source="actor.display_name",
        read_only=True,
        default="已注销用户",
    )

    class Meta:
        """序列化字段。"""

        model = BusinessOperationLog
        fields = [
            "id",
            "action_type",
            "description",
            "changes",
            "actor_display_name",
            "created_at",
        ]


class ProjectDocumentSerializer(serializers.ModelSerializer):
    """项目文档读取结构。"""

    category_label = serializers.CharField(source="get_category_display", read_only=True)
    uploaded_by_name = serializers.CharField(
        source="uploaded_by.display_name",
        read_only=True,
    )

    class Meta:
        """序列化字段。"""

        model = ProjectDocument
        fields = [
            "id",
            "name",
            "extension",
            "mime_type",
            "file_size",
            "category",
            "category_label",
            "version_label",
            "uploaded_by_name",
            "created_at",
            "updated_at",
        ]


class ProjectDocumentCreateSerializer(serializers.Serializer):
    """项目文档上传请求。"""

    file = serializers.FileField()
    category = serializers.ChoiceField(choices=ProjectDocumentCategory.choices)
    version_label = serializers.RegexField(
        regex=r"^[A-Za-z0-9._-]{1,32}$",
        default="V1.0",
    )

    def validate_file(self, value):
        """校验文件大小、扩展名和真实文件签名。

        Args:
            value: 上传文件。

        Returns:
            校验后的上传文件。
        """
        if value.size > 100 * 1024 * 1024:
            raise serializers.ValidationError("单个文档不得超过 100 MB")
        if not value.name or len(value.name) > 255:
            raise serializers.ValidationError("文件名长度必须为 1–255 个字符")
        extension = Path(value.name).suffix.lower().lstrip(".")
        if extension not in ALLOWED_DOCUMENT_EXTENSIONS:
            raise serializers.ValidationError("不支持该文件格式")
        self._validate_file_signature(value, extension)
        return value

    @staticmethod
    def _validate_file_signature(value, extension: str) -> None:
        """根据扩展名验证不可由请求头伪造的文件内容。

        Args:
            value: Django 上传文件。
            extension: 小写无点扩展名。

        Raises:
            serializers.ValidationError: 文件内容与扩展名不匹配。
        """
        value.seek(0)
        head = value.read(16)
        value.seek(0)
        valid = True
        if extension == "pdf":
            valid = head.startswith(b"%PDF-")
        elif extension == "png":
            valid = head.startswith(b"\x89PNG\r\n\x1a\n")
        elif extension in {"jpg", "jpeg"}:
            valid = head.startswith(b"\xff\xd8\xff")
        elif extension == "webp":
            valid = head.startswith(b"RIFF") and head[8:12] == b"WEBP"
        elif extension == "gif":
            valid = head.startswith((b"GIF87a", b"GIF89a"))
        elif extension == "bmp":
            valid = head.startswith(b"BM")
        elif extension == "rtf":
            valid = head.lstrip().startswith(b"{\\rtf")
        elif extension in {"doc", "xls", "ppt"}:
            valid = head.startswith(b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1")
        elif extension in {*OOXML_REQUIRED_PREFIXES, *ODF_MIME_TYPES}:
            try:
                with ZipFile(value) as archive:
                    file_infos = archive.infolist()
                    names = archive.namelist()
                    archive_is_safe = (
                        len(file_infos) <= MAX_OFFICE_ARCHIVE_ENTRIES
                        and sum(item.file_size for item in file_infos)
                        <= MAX_OFFICE_ARCHIVE_UNCOMPRESSED_BYTES
                        and all(not item.flag_bits & 0x1 for item in file_infos)
                        and all(
                            not PurePosixPath(name).is_absolute()
                            and ".." not in PurePosixPath(name).parts
                            for name in names
                        )
                    )
                    if extension in OOXML_REQUIRED_PREFIXES:
                        required_prefix = OOXML_REQUIRED_PREFIXES[extension]
                        valid = (
                            archive_is_safe
                            and "[Content_Types].xml" in names
                            and any(name.startswith(required_prefix) for name in names)
                        )
                    else:
                        valid = (
                            archive_is_safe
                            and archive.read("mimetype").decode("ascii", errors="strict").strip()
                            == ODF_MIME_TYPES[extension]
                        )
            except (BadZipFile, KeyError, OSError, UnicodeDecodeError):
                valid = False
            finally:
                value.seek(0)
        elif extension in {"csv", "txt"}:
            value.seek(0)
            sample = value.read(4096)
            value.seek(0)
            valid = b"\x00" not in sample
        if not valid:
            raise serializers.ValidationError("文件内容与扩展名不匹配或文件已损坏")
