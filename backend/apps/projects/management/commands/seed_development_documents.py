"""按原型初始化可重复执行的项目文档演示数据。"""

from datetime import datetime, timedelta

from django.core.files.base import ContentFile
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from apps.identity.models import User
from apps.projects.models import (
    Project,
    ProjectDocument,
    ProjectDocumentCategory,
)

CATEGORY_SEQUENCE = [
    ProjectDocumentCategory.PROJECT_PLAN,
    ProjectDocumentCategory.LITERATURE,
    ProjectDocumentCategory.EXPERIMENT_PLAN,
    ProjectDocumentCategory.STAGE_REPORT,
    ProjectDocumentCategory.MEETING_MINUTES,
    ProjectDocumentCategory.OTHER,
]

MIME_TYPES = {
    "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "pdf": "application/pdf",
    "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
}


def aware(value: str) -> datetime:
    """将原型时间文本转换为当前时区时间。

    Args:
        value: `YYYY-MM-DD HH:MM` 格式时间。

    Returns:
        带时区的日期时间。
    """
    return timezone.make_aware(datetime.strptime(value, "%Y-%m-%d %H:%M"))


class Command(BaseCommand):
    """初始化项目文档演示数据。"""

    help = "初始化项目详情文档资料演示数据"

    @transaction.atomic
    def handle(self, *args, **options) -> None:
        """执行初始化。

        Args:
            *args: 未使用的位置参数。
            **options: 未使用的命令参数。

        Raises:
            CommandError: 项目或开发账号尚未初始化。
        """
        actor = User.objects.filter(username="admin", is_active=True).first()
        if not actor:
            raise CommandError("请先执行 bootstrap_development 初始化开发账号")
        projects = list(
            Project.objects.filter(organization=actor.organization).order_by("project_no")
        )
        if not projects:
            raise CommandError("请先执行 seed_development_projects 初始化项目")

        base_time = aware("2026-07-20 10:24")
        created_count = 0
        for project in projects:
            target_count = max(project.document_count, 4)
            core_specs = [
                (
                    "项目实施方案V3.docx",
                    ProjectDocumentCategory.PROJECT_PLAN,
                    "项目整体",
                    "V3.0",
                    "docx",
                ),
                (
                    f"{project.name[:18]}研究综述.pdf",
                    ProjectDocumentCategory.LITERATURE,
                    "第三轮配方筛选",
                    "V1.0",
                    "pdf",
                ),
                (
                    "第三轮配方筛选记录.xlsx",
                    ProjectDocumentCategory.EXPERIMENT_PLAN,
                    "EXP-2026-018",
                    "V2.1",
                    "xlsx",
                ),
                (
                    "项目中期汇报-202607.pptx",
                    ProjectDocumentCategory.STAGE_REPORT,
                    "项目整体",
                    "V1.2",
                    "pptx",
                ),
            ]
            specs = list(core_specs)
            for index in range(max(0, target_count - len(core_specs))):
                category = CATEGORY_SEQUENCE[index % len(CATEGORY_SEQUENCE)]
                extension = ["docx", "pdf", "xlsx", "pptx"][index % 4]
                specs.append(
                    (
                        f"{category.label}归档资料-{index + 1:02d}.{extension}",
                        category,
                        "项目整体",
                        f"V1.{index + 1}",
                        extension,
                    )
                )

            for index, (
                name,
                category,
                related_content,
                version_label,
                extension,
            ) in enumerate(specs):
                document = ProjectDocument.objects.filter(
                    project=project,
                    name=name,
                ).first()
                if document:
                    continue
                payload = (
                    f"{name}\n\n项目：{project.name}\n分类：{category.label}\n"
                    "该文件用于电子实验助手开发环境的项目文档流程演示。\n"
                ).encode()
                document = ProjectDocument(
                    organization=project.organization,
                    project=project,
                    name=name,
                    extension=extension,
                    mime_type=MIME_TYPES[extension],
                    file_size=len(payload),
                    category=category,
                    related_content=related_content,
                    version_label=version_label,
                    uploaded_by=project.owner,
                    updated_by=project.owner,
                )
                document.file.save(name, ContentFile(payload), save=False)
                document.save()
                updated_at = base_time - timedelta(hours=index * 7)
                ProjectDocument.objects.filter(id=document.id).update(
                    created_at=updated_at,
                    updated_at=updated_at,
                )
                created_count += 1
            project.document_count = project.documents.count()
            project.save(update_fields=["document_count", "updated_at"])

        self.stdout.write(
            self.style.SUCCESS(f"项目文档初始化完成，本次新增 {created_count} 个文档")
        )
