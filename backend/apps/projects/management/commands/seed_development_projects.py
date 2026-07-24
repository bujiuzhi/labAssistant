"""初始化可重复执行的开发演示项目。"""

from datetime import date

from django.core.management.base import BaseCommand, CommandError

from apps.identity.models import User
from apps.projects.services import create_project

DEVELOPMENT_PROJECTS = [
    {
        "name": "耐高温聚酰亚胺薄膜制备工艺研究",
        "project_type_code": "research",
        "description": "优化聚酰亚胺薄膜的配方与热处理工艺，建立性能验证数据链路。",
        "planned_start_date": date(2026, 7, 1),
        "planned_end_date": date(2026, 11, 30),
    },
    {
        "name": "高性能 PLA 基可降解复合材料开发",
        "project_type_code": "research",
        "description": "评估增强组分对 PLA 复合材料力学性能与降解行为的影响。",
        "planned_start_date": date(2026, 7, 15),
        "planned_end_date": date(2026, 12, 31),
    },
    {
        "name": "低介电环氧树脂电子封装材料验证",
        "project_type_code": "validation",
        "description": "完成候选环氧体系的介电、热学和可靠性验证。",
        "planned_start_date": date(2026, 8, 1),
        "planned_end_date": date(2027, 1, 31),
    },
    {
        "name": "硅碳负极粘结剂体系性能优化",
        "project_type_code": "research",
        "description": "比较不同粘结剂配方的循环性能与界面稳定性。",
        "planned_start_date": date(2026, 8, 10),
        "planned_end_date": date(2026, 12, 20),
    },
]


class Command(BaseCommand):
    """创建首个纵向切片所需演示项目。"""

    help = "初始化开发演示项目，可重复执行"

    def add_arguments(self, parser) -> None:
        """注册命令参数。

        Args:
            parser: Django 命令参数解析器。
        """
        parser.add_argument("--owner-username", default="admin")

    def handle(self, *args, **options) -> None:
        """执行初始化。

        Args:
            *args: 未使用的位置参数。
            **options: 命令参数。

        Raises:
            CommandError: 负责人不存在或不唯一时抛出。
        """
        owners = list(
            User.objects.filter(
                username=options["owner_username"],
                is_active=True,
            )[:2]
        )
        if len(owners) != 1:
            raise CommandError("开发项目负责人不存在或用户名不唯一")
        owner = owners[0]

        created_count = 0
        for index, project_data in enumerate(DEVELOPMENT_PROJECTS, start=1):
            _, created = create_project(
                actor=owner,
                validated_data={
                    **project_data,
                    "owner": owner,
                },
                idempotency_key=f"development-seed-project-{index:02d}",
            )
            created_count += int(created)

        self.stdout.write(
            self.style.SUCCESS(
                f"开发演示项目初始化完成，本次新增 {created_count} 个项目"
            )
        )
