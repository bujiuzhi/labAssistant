"""电子实验记录本接口序列化器。"""

from pathlib import Path

from rest_framework import serializers

from apps.identity.models import User, UserStatus
from apps.projects.models import Project

from .models import (
    Experiment,
    ExperimentAttachment,
    ExperimentAttachmentKind,
    ExperimentRecord,
    ExperimentStatus,
)

EXPERIMENT_TYPE_CHOICES = ["单体", "聚合", "其他"]


class FormulaColumnSerializer(serializers.Serializer):
    """动态配方列结构。"""

    id = serializers.CharField(min_length=1, max_length=64)
    label = serializers.CharField(max_length=100, allow_blank=True)


class ExtraTableSerializer(serializers.Serializer):
    """自定义表格结构。"""

    id = serializers.CharField(min_length=1, max_length=100)
    name = serializers.CharField(min_length=1, max_length=100)
    columns = FormulaColumnSerializer(many=True, allow_empty=False)
    rows = serializers.ListField(child=serializers.DictField(), allow_empty=True)


class ExtraProcessSerializer(serializers.Serializer):
    """自定义实验过程模块结构。"""

    id = serializers.CharField(min_length=1, max_length=100)
    name = serializers.CharField(min_length=1, max_length=100)
    content = serializers.CharField(max_length=1000, allow_blank=True)


class ExperimentAttachmentUploadSerializer(serializers.Serializer):
    """实验附件上传请求。"""

    file = serializers.FileField()
    kind = serializers.ChoiceField(choices=ExperimentAttachmentKind.choices)

    def validate(self, attrs):
        """按用途校验文件大小、扩展名和真实签名。"""
        upload = attrs["file"]
        kind = attrs["kind"]
        if not upload.name or len(upload.name) > 255:
            raise serializers.ValidationError(
                {"file": ["文件名长度必须为 1–255 个字符"]}
            )
        if kind == ExperimentAttachmentKind.PROCESS_IMAGE:
            if upload.size > 10 * 1024 * 1024:
                raise serializers.ValidationError(
                    {"file": ["单张过程图片不得超过 10 MB"]}
                )
            extension = Path(upload.name).suffix.lower()
            upload.seek(0)
            head = upload.read(12)
            upload.seek(0)
            valid_image = (
                extension in {".jpg", ".jpeg"}
                and head.startswith(b"\xff\xd8\xff")
            ) or (
                extension == ".png"
                and head.startswith(b"\x89PNG\r\n\x1a\n")
            )
            if not valid_image:
                raise serializers.ValidationError(
                    {"file": ["过程图片仅支持内容有效的 JPG、PNG 文件"]}
                )
        elif upload.size > 25 * 1024 * 1024:
            raise serializers.ValidationError(
                {"file": ["单个结果附件不得超过 25 MB"]}
            )
        return attrs


class ExperimentRecordWriteSerializer(serializers.Serializer):
    """电子实验记录写入结构。"""

    formula_columns = FormulaColumnSerializer(many=True, allow_empty=False, required=False)
    formula_rows = serializers.ListField(
        child=serializers.DictField(),
        allow_empty=True,
        required=False,
    )
    extra_tables = ExtraTableSerializer(many=True, allow_empty=True, required=False)
    process_text = serializers.CharField(max_length=50000, allow_blank=True, required=False)
    extra_processes = ExtraProcessSerializer(
        many=True,
        allow_empty=True,
        required=False,
    )
    result_text = serializers.CharField(max_length=1000, allow_blank=True, required=False)


class ExperimentSerializer(serializers.ModelSerializer):
    """实验计划读取结构。"""

    project_id = serializers.UUIDField(read_only=True)
    project_no = serializers.CharField(source="project.project_no", read_only=True)
    project_name = serializers.CharField(source="project.name", read_only=True)
    owner_id = serializers.UUIDField(read_only=True)
    owner_display_name = serializers.CharField(source="owner.display_name", read_only=True)
    participant_ids = serializers.SerializerMethodField()
    participant_names = serializers.SerializerMethodField()
    record = serializers.SerializerMethodField()

    class Meta:
        """序列化字段。"""

        model = Experiment
        fields = [
            "id",
            "experiment_no",
            "name",
            "project_id",
            "project_no",
            "project_name",
            "experiment_type",
            "phase",
            "status",
            "purpose",
            "estimated_start",
            "estimated_end",
            "started_at",
            "completed_at",
            "owner_id",
            "owner_display_name",
            "participant_ids",
            "participant_names",
            "record",
            "version",
            "created_at",
            "updated_at",
        ]

    def get_participant_ids(self, instance: Experiment) -> list[str]:
        """返回参与用户 ID。

        Args:
            instance: 实验计划。

        Returns:
            用户 ID 列表。
        """
        return [str(item.user_id) for item in instance.participants.all()]

    def get_participant_names(self, instance: Experiment) -> list[str]:
        """返回参与用户名称。

        Args:
            instance: 实验计划。

        Returns:
            用户显示名称列表。
        """
        return [item.user.display_name for item in instance.participants.all()]

    def get_record(self, instance: Experiment) -> dict:
        """返回电子实验记录。

        Args:
            instance: 实验计划。

        Returns:
            记录字段字典。
        """
        try:
            record = instance.record
        except ExperimentRecord.DoesNotExist:
            columns = [
                {
                    "id": f"col_{index + 1}",
                    "label": "原料名称" if index == 0 else "",
                }
                for index in range(4)
            ]
            return {
                "formula_columns": columns,
                "formula_rows": [
                    {column["id"]: "" for column in columns},
                    {column["id"]: "" for column in columns},
                ],
                "extra_tables": [],
                "process_text": "",
                "extra_processes": [],
                "process_images": [],
                "result_text": "",
                "result_files": [],
            }
        attachments = list(instance.attachments.all())

        def attachment_data(item: ExperimentAttachment) -> dict:
            return {
                "id": str(item.id),
                "name": item.name,
                "size": f"{item.file_size} B",
                "url": (
                    f"/api/v1/experiments/{instance.experiment_no}/"
                    f"attachments/{item.id}/content"
                ),
            }

        return {
            "formula_columns": record.formula_columns,
            "formula_rows": record.formula_rows,
            "extra_tables": record.extra_tables,
            "process_text": record.process_text,
            "extra_processes": record.extra_processes,
            "process_images": [
                attachment_data(item)
                for item in attachments
                if item.kind == ExperimentAttachmentKind.PROCESS_IMAGE
            ],
            "result_text": record.result_text,
            "result_files": [
                attachment_data(item)
                for item in attachments
                if item.kind == ExperimentAttachmentKind.RESULT_FILE
            ],
        }


class ExperimentWriteSerializer(ExperimentRecordWriteSerializer):
    """实验计划与记录写入结构。"""

    project_id = serializers.PrimaryKeyRelatedField(
        source="project",
        queryset=Project.objects.all(),
        required=False,
    )
    name = serializers.CharField(min_length=1, max_length=200, required=False)
    experiment_type = serializers.ChoiceField(
        choices=EXPERIMENT_TYPE_CHOICES,
        required=False,
    )
    purpose = serializers.CharField(max_length=10000, allow_blank=True, required=False)
    estimated_start = serializers.DateTimeField(allow_null=True, required=False)
    estimated_end = serializers.DateTimeField(allow_null=True, required=False)
    owner_id = serializers.PrimaryKeyRelatedField(
        source="owner",
        queryset=User.objects.filter(status=UserStatus.ACTIVE),
        required=False,
    )
    participant_ids = serializers.ListField(
        child=serializers.UUIDField(),
        allow_empty=True,
        required=False,
    )

    def validate(self, attrs):
        """校验实验日期与配方结构。

        Args:
            attrs: 已完成字段级校验的数据。

        Returns:
            校验后的数据。
        """
        instance = self.context.get("experiment")
        start = attrs.get(
            "estimated_start",
            instance.estimated_start if instance else None,
        )
        end = attrs.get(
            "estimated_end",
            instance.estimated_end if instance else None,
        )
        if start and end and end < start:
            raise serializers.ValidationError(
                {"estimated_end": ["预估结束时间不能早于开始时间"]}
            )
        columns = attrs.get("formula_columns")
        rows = attrs.get("formula_rows")
        if columns is not None:
            column_ids = [item["id"] for item in columns]
            if len(column_ids) != len(set(column_ids)):
                raise serializers.ValidationError(
                    {"formula_columns": ["配方列标识不能重复"]}
                )
            if rows is not None:
                allowed_ids = set(column_ids)
                if any(set(row) - allowed_ids for row in rows):
                    raise serializers.ValidationError(
                        {"formula_rows": ["配方行包含未定义列"]}
                    )
        participant_ids = attrs.get("participant_ids")
        if participant_ids is not None and len(participant_ids) != len(
            set(participant_ids)
        ):
            raise serializers.ValidationError(
                {"participant_ids": ["实验参与人员不能重复"]}
            )
        return attrs


class ExperimentCreateSerializer(ExperimentWriteSerializer):
    """实验计划创建请求。"""

    project_id = serializers.PrimaryKeyRelatedField(
        source="project",
        queryset=Project.objects.all(),
    )
    name = serializers.CharField(min_length=1, max_length=200)
    experiment_type = serializers.ChoiceField(choices=EXPERIMENT_TYPE_CHOICES)
    purpose = serializers.CharField(
        min_length=1,
        max_length=10000,
        allow_blank=False,
    )


class ExperimentUpdateSerializer(ExperimentWriteSerializer):
    """实验计划和记录更新请求。"""

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


class ExperimentStatusSerializer(serializers.Serializer):
    """实验状态迁移请求。"""

    target_status = serializers.ChoiceField(
        choices=[
            ExperimentStatus.IN_PROGRESS,
            ExperimentStatus.COMPLETED,
        ]
    )
