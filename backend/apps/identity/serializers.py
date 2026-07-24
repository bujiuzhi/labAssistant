"""会话与组织用户管理接口序列化器。"""

from rest_framework import serializers

from .models import Role, User, UserStatus


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
            "is_super_admin",
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


class ManagedUserSerializer(serializers.ModelSerializer):
    """超级管理员维护的组织用户读取结构。"""

    role_codes = serializers.SerializerMethodField()
    role_names = serializers.SerializerMethodField()

    class Meta:
        """序列化字段。"""

        model = User
        fields = [
            "id",
            "username",
            "display_name",
            "email",
            "status",
            "is_active",
            "is_super_admin",
            "role_codes",
            "role_names",
            "last_login",
            "created_at",
            "updated_at",
        ]

    def get_role_codes(self, instance: User) -> list[str]:
        """返回用户角色代码。

        Args:
            instance: 组织用户。

        Returns:
            排序后的角色代码。
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
            instance: 组织用户。

        Returns:
            排序后的角色名称。
        """
        return list(
            instance.user_roles.order_by("role__role_code").values_list(
                "role__name",
                flat=True,
            )
        )


class RoleOptionSerializer(serializers.ModelSerializer):
    """可分配系统角色选项。"""

    class Meta:
        """序列化字段。"""

        model = Role
        fields = ["role_code", "name", "description"]


class ManagedUserWriteSerializer(serializers.Serializer):
    """组织用户通用写入字段。"""

    username = serializers.RegexField(
        regex=r"^[A-Za-z0-9._-]{3,64}$",
        required=False,
        error_messages={
            "invalid": "用户名只能包含字母、数字、点、下划线和短横线，长度为3–64位"
        },
    )
    display_name = serializers.CharField(min_length=1, max_length=100, required=False)
    email = serializers.EmailField(allow_blank=True, required=False)
    status = serializers.ChoiceField(choices=UserStatus.choices, required=False)
    role_codes = serializers.ListField(
        child=serializers.CharField(min_length=1, max_length=64),
        allow_empty=False,
        required=False,
    )


class ManagedUserCreateSerializer(ManagedUserWriteSerializer):
    """创建组织用户请求。"""

    username = serializers.RegexField(
        regex=r"^[A-Za-z0-9._-]{3,64}$",
        error_messages={
            "invalid": "用户名只能包含字母、数字、点、下划线和短横线，长度为3–64位"
        },
    )
    display_name = serializers.CharField(min_length=1, max_length=100)
    password = serializers.CharField(min_length=8, max_length=128, write_only=True)
    role_codes = serializers.ListField(
        child=serializers.CharField(min_length=1, max_length=64),
        allow_empty=False,
    )


class ManagedUserUpdateSerializer(ManagedUserWriteSerializer):
    """更新组织用户请求。"""

    def validate(self, attrs):
        """确保至少提供一个更新字段。

        Args:
            attrs: 已完成字段级校验的数据。

        Returns:
            校验后的更新参数。
        """
        if not attrs:
            raise serializers.ValidationError("至少提供一个待更新字段")
        return attrs


class PasswordResetSerializer(serializers.Serializer):
    """重置组织用户密码请求。"""

    password = serializers.CharField(min_length=8, max_length=128, write_only=True)
