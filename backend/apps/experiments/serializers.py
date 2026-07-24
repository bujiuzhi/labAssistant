"""电子实验记录本接口序列化器。"""

from rest_framework import serializers

from apps.identity.models import User, UserStatus
from apps.projects.models import Project

from .models import Experiment, ExperimentRecord, ExperimentStatus


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
    content = serializers.CharField(max_length=20000, allow_blank=True)


class ProcessImageSerializer(serializers.Serializer):
    """过程图片结构。"""

    name = serializers.CharField(min_length=1, max_length=255)
    url = serializers.CharField(min_length=1)
    size = serializers.CharField(max_length=40, allow_blank=True, required=False)


class ResultFileSerializer(serializers.Serializer):
    """结果附件元数据结构。"""

    name = serializers.CharField(min_length=1, max_length=255)
    size = serializers.CharField(max_length=40, allow_blank=True)


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
    process_images = ProcessImageSerializer(
        many=True,
        allow_empty=True,
        required=False,
    )
    result_text = serializers.CharField(max_length=1000, allow_blank=True, required=False)
    result_files = ResultFileSerializer(many=True, allow_empty=True, required=False)

    def validate_process_images(self, value: list[dict]) -> list[dict]:
        """限制过程图片数量与总数据量。

        Args:
            value: 图片列表。

        Returns:
            校验后的图片列表。
        """
        if len(value) > 20:
            raise serializers.ValidationError("过程图片最多 20 张")
        total_chars = sum(len(item["url"]) for item in value)
        if total_chars > 28 * 1024 * 1024:
            raise serializers.ValidationError("过程图片总大小不得超过 20 MB")
        return value

    def validate_result_files(self, value: list[dict]) -> list[dict]:
        """限制结果附件数量。

        Args:
            value: 附件元数据列表。

        Returns:
            校验后的附件列表。
        """
        if len(value) > 30:
            raise serializers.ValidationError("结果附件最多 30 个")
        return value


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
            return {
                "formula_columns": [{"id": "material", "label": "原料名称"}],
                "formula_rows": [{"material": ""}, {"material": ""}],
                "extra_tables": [],
                "process_text": "",
                "extra_processes": [],
                "process_images": [],
                "result_text": "",
                "result_files": [],
            }
        return {
            "formula_columns": record.formula_columns,
            "formula_rows": record.formula_rows,
            "extra_tables": record.extra_tables,
            "process_text": record.process_text,
            "extra_processes": record.extra_processes,
            "process_images": record.process_images,
            "result_text": record.result_text,
            "result_files": record.result_files,
        }


class ExperimentWriteSerializer(ExperimentRecordWriteSerializer):
    """实验计划与记录写入结构。"""

    project_id = serializers.PrimaryKeyRelatedField(
        source="project",
        queryset=Project.objects.all(),
        required=False,
    )
    name = serializers.CharField(min_length=1, max_length=200, required=False)
    experiment_type = serializers.CharField(min_length=1, max_length=64, required=False)
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
    experiment_type = serializers.CharField(min_length=1, max_length=64)


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
