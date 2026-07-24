"""项目接口序列化器。"""

from rest_framework import serializers

from apps.identity.models import User, UserStatus

from .models import Project


class ProjectSerializer(serializers.ModelSerializer):
    """项目读取结构。"""

    owner_id = serializers.UUIDField(read_only=True)
    owner_display_name = serializers.CharField(source="owner.display_name", read_only=True)

    class Meta:
        """序列化字段。"""

        model = Project
        fields = [
            "id",
            "project_no",
            "name",
            "project_type_code",
            "description",
            "status",
            "owner_id",
            "owner_display_name",
            "planned_start_date",
            "planned_end_date",
            "actual_end_at",
            "archived_at",
            "version",
            "created_at",
            "updated_at",
        ]


class ProjectWriteSerializer(serializers.Serializer):
    """项目创建和更新字段。"""

    name = serializers.CharField(min_length=1, max_length=200, required=False)
    project_type_code = serializers.CharField(min_length=1, max_length=64, required=False)
    description = serializers.CharField(max_length=10000, allow_blank=True, required=False)
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
