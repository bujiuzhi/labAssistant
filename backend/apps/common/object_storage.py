"""S3 兼容对象存储连接、检查与桶初始化能力。"""

from __future__ import annotations

from typing import Any

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError
from django.conf import settings
from django.core.exceptions import ImproperlyConfigured


def build_object_storage_client() -> Any:
    """创建使用 SigV4 和 path-style 的 S3 客户端。

    Returns:
        已按当前环境配置的 Boto3 S3 客户端。

    Raises:
        ImproperlyConfigured: 当前环境未启用对象存储。
    """
    if not settings.OBJECT_STORAGE_ENABLED:
        raise ImproperlyConfigured("当前环境未启用对象存储")
    return boto3.client(
        "s3",
        endpoint_url=settings.OBJECT_STORAGE_ENDPOINT_URL,
        aws_access_key_id=settings.OBJECT_STORAGE_ACCESS_KEY,
        aws_secret_access_key=settings.OBJECT_STORAGE_SECRET_KEY,
        region_name=settings.OBJECT_STORAGE_REGION,
        verify=settings.OBJECT_STORAGE_VERIFY_TLS,
        config=Config(
            signature_version="s3v4",
            s3={"addressing_style": "path"},
        ),
    )


def check_object_storage_bucket() -> None:
    """检查应用对象存储桶是否存在且当前凭据可以访问。

    Raises:
        ImproperlyConfigured: 当前环境未启用对象存储。
        BotoCoreError: 无法连接对象存储。
        ClientError: 凭据错误、桶不存在或无访问权限。
    """
    client = build_object_storage_client()
    client.head_bucket(Bucket=settings.OBJECT_STORAGE_BUCKET_NAME)


def ensure_object_storage_bucket() -> bool:
    """确保应用私有桶存在。

    Returns:
        新建桶时返回 ``True``，桶已存在时返回 ``False``。

    Raises:
        ImproperlyConfigured: 当前环境未启用对象存储。
        BotoCoreError: 无法连接对象存储。
        ClientError: 凭据错误、创建失败或无访问权限。
    """
    client = build_object_storage_client()
    bucket_name = settings.OBJECT_STORAGE_BUCKET_NAME
    try:
        client.head_bucket(Bucket=bucket_name)
        return False
    except ClientError as error:
        response_metadata = error.response.get("ResponseMetadata", {})
        status_code = response_metadata.get("HTTPStatusCode")
        error_code = str(error.response.get("Error", {}).get("Code", ""))
        if status_code != 404 and error_code not in {"404", "NoSuchBucket", "NotFound"}:
            raise

    client.create_bucket(Bucket=bucket_name)
    return True
