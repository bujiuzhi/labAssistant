"""项目接口序列化器。"""

from rest_framework import serializers

from apps.identity.models import User, UserStatus

from .models import Project, ProjectDocument, ProjectDocumentCategory


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
    project_type_code = serializers.CharField(min_length=1, max_length=64, required=False)
    description = serializers.CharField(max_length=10000, allow_blank=True, required=False)
    current_stage = serializers.CharField(max_length=64, required=False)
    objectives = serializers.ListField(
        child=serializers.CharField(max_length=1000),
        required=False,
    )
    milestones = serializers.ListField(
        child=ProjectMilestoneSerializer(),
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
    planned_start_date = serializers.DateField(allow_null=True, required=False)
    planned_end_date = serializers.DateField(allow_null=True, required=False)

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
    project_type_code = serializers.CharField(min_length=1, max_length=64)
    owner_id = serializers.PrimaryKeyRelatedField(
        source="owner",
        queryset=User.objects.filter(status=UserStatus.ACTIVE),
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
            "related_content",
            "version_label",
            "uploaded_by_name",
            "created_at",
            "updated_at",
        ]


class ProjectDocumentCreateSerializer(serializers.Serializer):
    """项目文档上传请求。"""

    file = serializers.FileField()
    category = serializers.ChoiceField(choices=ProjectDocumentCategory.choices)
    related_content = serializers.CharField(
        max_length=200,
        default="项目整体",
        allow_blank=False,
    )
    version_label = serializers.RegexField(
        regex=r"^[A-Za-z0-9._-]{1,32}$",
        default="V1.0",
    )

    def validate_file(self, value):
        """校验文件大小和名称。

        Args:
            value: 上传文件。

        Returns:
            校验后的上传文件。
        """
        if value.size > 25 * 1024 * 1024:
            raise serializers.ValidationError("单个文档不得超过 25 MB")
        if not value.name or len(value.name) > 255:
            raise serializers.ValidationError("文件名长度必须为 1–255 个字符")
        return value
