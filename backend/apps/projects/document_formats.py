"""项目文档格式、预览策略和可信 MIME 类型定义。"""

from __future__ import annotations

DOCUMENT_EXTENSION_GROUPS = {
    "word": ("doc", "docx", "odt", "rtf"),
    "pdf": ("pdf",),
    "excel": ("csv", "ods", "xls", "xlsx"),
    "powerpoint": ("odp", "ppt", "pptx"),
    "image": ("bmp", "gif", "jpeg", "jpg", "png", "webp"),
    "text": ("txt",),
}

DIRECT_IMAGE_PREVIEW_EXTENSIONS = frozenset(DOCUMENT_EXTENSION_GROUPS["image"])
DIRECT_PREVIEW_EXTENSIONS = DIRECT_IMAGE_PREVIEW_EXTENSIONS | {"pdf"}
OFFICE_PREVIEW_EXTENSIONS = frozenset(
    extension
    for group_name in ("word", "excel", "powerpoint", "text")
    for extension in DOCUMENT_EXTENSION_GROUPS[group_name]
)
ALLOWED_DOCUMENT_EXTENSIONS = frozenset(
    extension for extensions in DOCUMENT_EXTENSION_GROUPS.values() for extension in extensions
)

OOXML_REQUIRED_PREFIXES = {
    "docx": "word/",
    "xlsx": "xl/",
    "pptx": "ppt/",
}
ODF_MIME_TYPES = {
    "odt": "application/vnd.oasis.opendocument.text",
    "ods": "application/vnd.oasis.opendocument.spreadsheet",
    "odp": "application/vnd.oasis.opendocument.presentation",
}

DOCUMENT_MIME_TYPES = {
    "doc": "application/msword",
    "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "odt": ODF_MIME_TYPES["odt"],
    "rtf": "application/rtf",
    "pdf": "application/pdf",
    "csv": "text/csv",
    "ods": ODF_MIME_TYPES["ods"],
    "xls": "application/vnd.ms-excel",
    "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "odp": ODF_MIME_TYPES["odp"],
    "ppt": "application/vnd.ms-powerpoint",
    "pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "txt": "text/plain",
    "bmp": "image/bmp",
    "gif": "image/gif",
    "jpeg": "image/jpeg",
    "jpg": "image/jpeg",
    "png": "image/png",
    "webp": "image/webp",
}
