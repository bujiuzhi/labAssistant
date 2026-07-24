"""会话接口序列化器。"""

from rest_framework import serializers

from .models import User


class LoginSerializer(serializers.Serializer):
    """登录请求。"""

    username = serializers.CharField(max_length=64)
    password = serializers.CharField(max_length=256, trim_whitespace=False, write_only=True)


class SessionUserSerializer(serializers.ModelSerializer):
    """当前会话用户。"""

    organization_id = serializers.UUIDField(read_only=True)
    permissions = serializers.SerializerMethodField()
    role_codes = serializers.SerializerMethodField()
    role_names = serializers.SerializerMethodField()

    class Meta:
        """序列化字段。"""

        model = User
        fields = [
            "id",
            "organization_id",
            "username",
            "display_name",
            "permissions",
            "role_codes",
            "role_names",
        ]

    def get_permissions(self, instance: User) -> list[str]:
        """返回用户权限码。

        Args:
            instance: 当前用户。

        Returns:
            权限码列表。
        """
        return instance.permission_codes()

    def get_role_codes(self, instance: User) -> list[str]:
        """返回用户角色代码。

        Args:
            instance: 当前用户。

        Returns:
            角色代码列表。
        """
        return list(
            instance.user_roles.order_by("role__role_code").values_list(
                "role__role_code",
                flat=True,
            )
        )

    def get_role_names(self, instance: User) -> list[str]:
        """返回用户角色名称。

        Args:
            instance: 当前用户。

        Returns:
            角色名称列表。
        """
        return list(
            instance.user_roles.order_by("role__role_code").values_list(
                "role__name",
                flat=True,
            )
        )


class OrganizationUserOptionSerializer(serializers.ModelSerializer):
    """组织用户选择项。"""

    class Meta:
        """序列化字段。"""

        model = User
        fields = ["id", "username", "display_name"]
