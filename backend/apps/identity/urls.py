"""身份接口路由。"""

from django.urls import path

from .views import (
    CsrfView,
    LoginView,
    LogoutView,
    ManagedRoleOptionsView,
    ManagedUserDetailView,
    ManagedUserListCreateView,
    ManagedUserPasswordResetView,
    OrganizationUserOptionsView,
    SessionView,
)

urlpatterns = [
    path("csrf", CsrfView.as_view(), name="auth-csrf"),
    path("login", LoginView.as_view(), name="auth-login"),
    path("logout", LogoutView.as_view(), name="auth-logout"),
    path("session", SessionView.as_view(), name="auth-session"),
    path("users/options", OrganizationUserOptionsView.as_view(), name="auth-user-options"),
    path("users", ManagedUserListCreateView.as_view(), name="managed-user-list"),
    path(
        "users/<uuid:user_id>",
        ManagedUserDetailView.as_view(),
        name="managed-user-detail",
    ),
    path(
        "users/<uuid:user_id>/reset-password",
        ManagedUserPasswordResetView.as_view(),
        name="managed-user-reset-password",
    ),
    path("roles/options", ManagedRoleOptionsView.as_view(), name="managed-role-options"),
]
