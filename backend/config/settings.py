"""材料实验助手 Django 配置。"""

import os
from pathlib import Path

from django.core.exceptions import ImproperlyConfigured

BASE_DIR = Path(__file__).resolve().parent.parent


def env_bool(name: str, default: bool = False) -> bool:
    """读取布尔环境变量。

    Args:
        name: 环境变量名。
        default: 未配置时的默认值。

    Returns:
        解析后的布尔值。
    """
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


def env_list(name: str, default: str = "") -> list[str]:
    """读取逗号分隔的环境变量。

    Args:
        name: 环境变量名。
        default: 未配置时的默认文本。

    Returns:
        去除空白后的字符串列表。
    """
    return [item.strip() for item in os.getenv(name, default).split(",") if item.strip()]


def env_positive_int(name: str, default: int) -> int:
    """读取正整数环境变量。

    Args:
        name: 环境变量名。
        default: 未配置时的默认值。

    Returns:
        解析后的正整数。

    Raises:
        ImproperlyConfigured: 配置值不是正整数。
    """
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    try:
        value = int(raw_value)
    except ValueError as error:
        raise ImproperlyConfigured(f"{name} 必须是正整数") from error
    if value <= 0:
        raise ImproperlyConfigured(f"{name} 必须是正整数")
    return value


OBJECT_STORAGE_ENABLED = env_bool("OBJECT_STORAGE_ENABLED", default=False)

SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "development-only-change-before-production")
DEBUG = env_bool("DJANGO_DEBUG", default=False)
ALLOWED_HOSTS = env_list("ALLOWED_HOSTS", "127.0.0.1,localhost")
CSRF_TRUSTED_ORIGINS = env_list("CSRF_TRUSTED_ORIGINS")

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    *(["storages"] if OBJECT_STORAGE_ENABLED else []),
    "apps.common",
    "apps.identity",
    "apps.projects",
    "apps.experiments",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "apps.common.middleware.RequestIdMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

if os.getenv("DATABASE_ENGINE", "postgresql") == "sqlite":
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": BASE_DIR / "db.sqlite3",
        }
    }
else:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": os.getenv("POSTGRES_DB", "materials_lab"),
            "USER": os.getenv("POSTGRES_USER", "materials_lab"),
            "PASSWORD": os.getenv("POSTGRES_PASSWORD", ""),
            "HOST": os.getenv("POSTGRES_HOST", "127.0.0.1"),
            "PORT": os.getenv("POSTGRES_PORT", "15432"),
            "CONN_MAX_AGE": 60,
            "OPTIONS": {"connect_timeout": 5},
        }
    }

AUTH_USER_MODEL = "identity.User"
AUTHENTICATION_BACKENDS = ["apps.identity.backends.OrganizationBackend"]
# 用户名由组织范围唯一约束与自定义认证后端共同处理。
SILENCED_SYSTEM_CHECKS = ["auth.W004"]
PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.Argon2PasswordHasher",
    "django.contrib.auth.hashers.PBKDF2PasswordHasher",
]

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

LANGUAGE_CODE = "zh-hans"
TIME_ZONE = "Asia/Shanghai"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
STATIC_ROOT = BASE_DIR / "staticfiles"
MEDIA_URL = "media/"
MEDIA_ROOT = BASE_DIR / "media"
OBJECT_STORAGE_ENDPOINT_URL = os.getenv(
    "OBJECT_STORAGE_ENDPOINT_URL",
    "http://127.0.0.1:19000",
).rstrip("/")
OBJECT_STORAGE_ACCESS_KEY = os.getenv("OBJECT_STORAGE_ACCESS_KEY", "")
OBJECT_STORAGE_SECRET_KEY = os.getenv("OBJECT_STORAGE_SECRET_KEY", "")
OBJECT_STORAGE_BUCKET_NAME = os.getenv(
    "OBJECT_STORAGE_BUCKET_NAME",
    "materials-lab-assistant",
)
OBJECT_STORAGE_REGION = os.getenv("OBJECT_STORAGE_REGION", "us-east-1")
OBJECT_STORAGE_VERIFY_TLS = env_bool("OBJECT_STORAGE_VERIFY_TLS", default=True)
OBJECT_STORAGE_PRESIGNED_URL_EXPIRY_SECONDS = env_positive_int(
    "OBJECT_STORAGE_PRESIGNED_URL_EXPIRY_SECONDS",
    300,
)

if OBJECT_STORAGE_ENABLED:
    missing_object_storage_settings = [
        name
        for name, value in {
            "OBJECT_STORAGE_ENDPOINT_URL": OBJECT_STORAGE_ENDPOINT_URL,
            "OBJECT_STORAGE_ACCESS_KEY": OBJECT_STORAGE_ACCESS_KEY,
            "OBJECT_STORAGE_SECRET_KEY": OBJECT_STORAGE_SECRET_KEY,
            "OBJECT_STORAGE_BUCKET_NAME": OBJECT_STORAGE_BUCKET_NAME,
            "OBJECT_STORAGE_REGION": OBJECT_STORAGE_REGION,
        }.items()
        if not value
    ]
    if missing_object_storage_settings:
        raise ImproperlyConfigured(
            "启用对象存储时缺少配置：" + ", ".join(missing_object_storage_settings)
        )

STORAGES = {
    "default": {
        "BACKEND": "django.core.files.storage.FileSystemStorage",
    },
    "staticfiles": {
        "BACKEND": "django.contrib.staticfiles.storage.StaticFilesStorage",
    },
}
if OBJECT_STORAGE_ENABLED:
    STORAGES["default"] = {
        "BACKEND": "storages.backends.s3.S3Storage",
        "OPTIONS": {
            "access_key": OBJECT_STORAGE_ACCESS_KEY,
            "secret_key": OBJECT_STORAGE_SECRET_KEY,
            "bucket_name": OBJECT_STORAGE_BUCKET_NAME,
            "endpoint_url": OBJECT_STORAGE_ENDPOINT_URL,
            "region_name": OBJECT_STORAGE_REGION,
            "addressing_style": "path",
            "signature_version": "s3v4",
            "default_acl": None,
            "querystring_auth": True,
            "querystring_expire": OBJECT_STORAGE_PRESIGNED_URL_EXPIRY_SECONDS,
            "file_overwrite": True,
            "verify": OBJECT_STORAGE_VERIFY_TLS,
        },
    }

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SECURE = not DEBUG
SESSION_COOKIE_SAMESITE = "Lax"
CSRF_COOKIE_SECURE = not DEBUG
CSRF_COOKIE_SAMESITE = "Lax"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "rest_framework.authentication.SessionAuthentication",
    ],
    "DEFAULT_PERMISSION_CLASSES": [
        "rest_framework.permissions.IsAuthenticated",
    ],
    "DEFAULT_PAGINATION_CLASS": "apps.common.pagination.EnvelopePageNumberPagination",
    "PAGE_SIZE": 20,
    "EXCEPTION_HANDLER": "apps.common.exceptions.problem_exception_handler",
}

CELERY_BROKER_URL = os.getenv("CELERY_BROKER_URL", "redis://127.0.0.1:16379/1")
CELERY_RESULT_BACKEND = os.getenv("CELERY_RESULT_BACKEND", "redis://127.0.0.1:16379/2")
CELERY_TASK_TRACK_STARTED = True
CELERY_TASK_TIME_LIMIT = 30 * 60

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "structured": {
            "format": (
                '{{"time":"{asctime}","level":"{levelname}","logger":"{name}",'
                '"message":"{message}"}}'
            ),
            "style": "{",
        }
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "formatter": "structured",
        }
    },
    "root": {"handlers": ["console"], "level": os.getenv("LOG_LEVEL", "INFO")},
}
