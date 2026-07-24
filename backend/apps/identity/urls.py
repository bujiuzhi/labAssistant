"""身份接口路由。"""

from django.urls import path

from .views import CsrfView, LoginView, LogoutView, SessionView

urlpatterns = [
    path("csrf", CsrfView.as_view(), name="auth-csrf"),
    path("login", LoginView.as_view(), name="auth-login"),
    path("logout", LogoutView.as_view(), name="auth-logout"),
    path("session", SessionView.as_view(), name="auth-session"),
]
