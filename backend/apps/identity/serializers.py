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

    class Meta:
        """序列化字段。"""

        model = User
        fields = [
            "id",
            "organization_id",
            "username",
            "display_name",
            "permissions",
        ]

    def get_permissions(self, instance: User) -> list[str]:
        """返回用户权限码。

        Args:
            instance: 当前用户。

        Returns:
            权限码列表。
        """
        return instance.permission_codes()
