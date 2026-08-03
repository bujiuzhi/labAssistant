"""按原型初始化可重复执行的开发演示项目。"""

from datetime import date, datetime, time, timedelta

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from apps.identity.models import Role, User, UserRole
from apps.projects.models import Project, ProjectMember, ProjectMemberRole

DEMO_PEOPLE = {
    "刘李园": "admin",
    "张伟": "manager",
    "李娜": "researcher",
    "王强": "inspector",
    "陈思": "demo_chensi",
    "赵敏": "demo_zhaomin",
    "周浩": "demo_zhouhao",
    "刘洋": "demo_liuyang",
}

LEGACY_PROJECT_NAMES = {
    "PRJ-2026-PI-005": ["耐高温聚酰亚胺薄膜制备工艺研究"],
    "PRJ-2026-PLA-001": [
        "高性能 PLA 基可降解复合材料开发",
        "高性能PLA基可降解复合材料开发",
    ],
    "PRJ-2026-EP-006": [
        "低介电环氧树脂电子封装材料验证",
        "低介电环氧树脂电子封装材料开发",
    ],
    "PRJ-2026-BAT-008": ["硅碳负极粘结剂体系性能优化"],
}


def milestone(date_value: str, name: str, state: str = "current") -> list[dict[str, str]]:
    """生成原型里程碑列表。

    Args:
        date_value: 计划日期。
        name: 里程碑名称。
        state: 里程碑状态。

    Returns:
        可写入 JSONField 的里程碑列表。
    """
    return [{"date": date_value, "name": name, "state": state}]


def project_milestones(project_data: dict) -> list[dict[str, str]]:
    """根据项目周期生成完整阶段里程碑。

    Args:
        project_data: 单个开发项目数据。

    Returns:
        按时间排序且最多一个当前阶段的里程碑。
    """
    explicit_milestones = project_data.get("milestone_sequence")
    if explicit_milestones is not None:
        return [dict(item) for item in explicit_milestones]

    start = date.fromisoformat(project_data["planned_start_date"])
    end = date.fromisoformat(project_data["planned_end_date"])
    current = project_data["milestones"][0]
    current_date = date.fromisoformat(current["date"])
    if project_data["status"] == "archived":
        return [
            {"date": start.isoformat(), "name": "项目立项", "state": "done"},
            {"date": current_date.isoformat(), "name": current["name"], "state": "done"},
            {"date": end.isoformat(), "name": "项目归档", "state": "done"},
        ]
    design_date = max(
        start,
        min(start + timedelta(days=45), current_date - timedelta(days=1)),
    )
    review_date = min(max(current_date + timedelta(days=30), current_date), end)
    return [
        {"date": start.isoformat(), "name": "项目立项", "state": "done"},
        {
            "date": design_date.isoformat(),
            "name": "完成实验方案设计",
            "state": "done",
        },
        {**current, "state": "current"},
        {"date": review_date.isoformat(), "name": "阶段评审", "state": "todo"},
        {"date": end.isoformat(), "name": "项目归档", "state": "todo"},
    ]


def aware_at(date_value: str, time_value: time) -> datetime:
    """将日期文本转换为当前时区日期时间。"""
    return timezone.make_aware(
        datetime.combine(date.fromisoformat(date_value), time_value)
    )


DEVELOPMENT_PROJECTS = [
    {
        "project_no": "PRJ-2026-PLA-001",
        "name": "高性能PLA基可降解复合材料开发",
        "project_type_code": "功能材料",
        "owner_name": "张伟",
        "current_stage": "实验执行",
        "progress_percent": 48,
        "document_count": 12,
        "experiment_count": 11,
        "status": "active",
        "planned_start_date": "2026-03-01",
        "planned_end_date": "2026-12-31",
        "description": "开发兼具力学性能与可降解性的PLA基复合材料，完成配方筛选与性能验证。",
        "milestones": milestone("2026-07-25", "完成第三轮配方筛选"),
        "milestone_sequence": [
            {"date": "2026-05-16", "name": "方案设计", "state": "done"},
            {
                "date": "2026-07-25",
                "name": "完成第三轮配方筛选",
                "state": "current",
            },
            {"date": "2026-07-31", "name": "实验验证", "state": "todo"},
            {"date": "2026-10-15", "name": "阶段评审", "state": "todo"},
            {"date": "2026-12-31", "name": "项目归档", "state": "todo"},
        ],
    },
    {
        "project_no": "PRJ-2026-SE-002",
        "name": "固态电解质材料离子电导率优化研究",
        "project_type_code": "新能源材料",
        "owner_name": "李娜",
        "current_stage": "方案设计",
        "progress_percent": 32,
        "document_count": 8,
        "experiment_count": 4,
        "status": "active",
        "planned_start_date": "2026-04-01",
        "planned_end_date": "2026-11-30",
        "description": "优化固态电解质配方与制备工艺，提升材料离子传导稳定性。",
        "milestones": milestone("2026-07-30", "完成固态电解质配方初筛"),
    },
    {
        "project_no": "PRJ-2026-CO2-003",
        "name": "CO₂加氢制备高附加值化学品工艺开发",
        "project_type_code": "绿色化工",
        "owner_name": "王强",
        "current_stage": "检测分析",
        "progress_percent": 67,
        "document_count": 15,
        "experiment_count": 9,
        "status": "at_risk",
        "planned_start_date": "2026-02-15",
        "planned_end_date": "2026-10-31",
        "description": "构建CO₂加氢催化与工艺体系，完成催化剂活性和稳定性评价。",
        "milestones": milestone("2026-07-22", "完成催化剂活性评价"),
    },
    {
        "project_no": "PRJ-2026-BIO-004",
        "name": "生物基环氧树脂合成与性能研究",
        "project_type_code": "环氧树脂",
        "owner_name": "陈思",
        "current_stage": "实验执行",
        "progress_percent": 52,
        "document_count": 10,
        "experiment_count": 6,
        "status": "active",
        "planned_start_date": "2026-03-20",
        "planned_end_date": "2026-12-15",
        "description": "开发生物基环氧树脂合成与固化工艺，验证材料综合性能。",
        "milestones": milestone("2026-08-05", "完成力学性能测试"),
    },
    {
        "project_no": "PRJ-2026-PI-005",
        "name": "耐高温聚酰亚胺薄膜制备工艺研究",
        "project_type_code": "聚酰亚胺",
        "owner_name": "李娜",
        "current_stage": "实验执行",
        "progress_percent": 58,
        "document_count": 14,
        "experiment_count": 4,
        "status": "active",
        "planned_start_date": "2026-02-10",
        "planned_end_date": "2026-11-20",
        "description": "开发长期耐温300℃以上的聚酰亚胺薄膜制备工艺，优化流延与亚胺化参数。",
        "milestones": milestone("2026-07-20", "完成精密流延与梯度亚胺化工艺窗口验证"),
    },
    {
        "project_no": "PRJ-2026-EP-006",
        "name": "低介电环氧树脂电子封装材料开发",
        "project_type_code": "环氧树脂",
        "owner_name": "周浩",
        "current_stage": "检测分析",
        "progress_percent": 61,
        "document_count": 11,
        "experiment_count": 4,
        "status": "active",
        "planned_start_date": "2026-01-18",
        "planned_end_date": "2026-10-18",
        "description": "开发兼顾低介电与高可靠性的环氧封装体系，完成关键配方及工艺验证。",
        "milestones": milestone("2026-07-26", "完成介电性能复测"),
    },
    {
        "project_no": "PRJ-2026-PI-007",
        "name": "热塑性聚酰亚胺复合材料界面改性",
        "project_type_code": "聚酰亚胺",
        "owner_name": "刘洋",
        "current_stage": "实验执行",
        "progress_percent": 43,
        "document_count": 9,
        "experiment_count": 4,
        "status": "active",
        "planned_start_date": "2026-03-12",
        "planned_end_date": "2027-01-15",
        "description": "改善热塑性聚酰亚胺与增强相界面结合，提升复合材料加工与力学性能。",
        "milestones": milestone("2026-08-01", "完成偶联剂方案筛选"),
    },
    {
        "project_no": "PRJ-2026-BAT-008",
        "name": "硅碳负极粘结剂体系性能优化",
        "project_type_code": "新能源材料",
        "owner_name": "李娜",
        "current_stage": "实验执行",
        "progress_percent": 39,
        "document_count": 8,
        "experiment_count": 4,
        "status": "active",
        "planned_start_date": "2026-04-08",
        "planned_end_date": "2026-12-28",
        "description": "优化硅碳负极水性粘结剂体系，改善电极循环稳定性与加工一致性。",
        "milestones": milestone("2026-08-03", "完成首轮循环性能评价"),
    },
    {
        "project_no": "PRJ-2026-EP-009",
        "name": "阻燃环氧树脂灌封胶体系研发",
        "project_type_code": "环氧树脂",
        "owner_name": "陈思",
        "current_stage": "方案设计",
        "progress_percent": 28,
        "document_count": 7,
        "experiment_count": 4,
        "status": "active",
        "planned_start_date": "2026-05-06",
        "planned_end_date": "2027-02-28",
        "description": "构建低粘度无卤阻燃环氧灌封体系，平衡阻燃、导热和施工性能。",
        "milestones": milestone("2026-08-08", "完成阻燃剂组合初筛"),
    },
    {
        "project_no": "PRJ-2026-MOF-010",
        "name": "多孔吸附材料VOCs捕集性能研究",
        "project_type_code": "功能材料",
        "owner_name": "王强",
        "current_stage": "检测分析",
        "progress_percent": 55,
        "document_count": 10,
        "experiment_count": 5,
        "status": "active",
        "planned_start_date": "2026-02-24",
        "planned_end_date": "2026-11-08",
        "description": "开发面向VOCs捕集的多孔吸附材料，明确孔结构与吸附选择性的关联。",
        "milestones": milestone("2026-07-29", "完成动态吸附曲线测试"),
    },
    {
        "project_no": "PRJ-2026-PI-011",
        "name": "光敏聚酰亚胺图形化性能评价",
        "project_type_code": "聚酰亚胺",
        "owner_name": "赵敏",
        "current_stage": "待开始",
        "progress_percent": 8,
        "document_count": 4,
        "experiment_count": 3,
        "status": "not_started",
        "planned_start_date": "2026-08-01",
        "planned_end_date": "2027-03-31",
        "description": "评价光敏聚酰亚胺曝光、显影及热处理窗口，支撑精细图形制备。",
        "milestones": milestone("2026-08-20", "完成基线配方准备"),
    },
    {
        "project_no": "PRJ-2026-EP-012",
        "name": "高韧性环氧结构胶配方开发",
        "project_type_code": "环氧树脂",
        "owner_name": "张伟",
        "current_stage": "待开始",
        "progress_percent": 5,
        "document_count": 3,
        "experiment_count": 3,
        "status": "not_started",
        "planned_start_date": "2026-08-15",
        "planned_end_date": "2027-04-20",
        "description": "开发高韧性环氧结构胶，兼顾室温固化效率、粘接强度与耐湿热性能。",
        "milestones": milestone("2026-09-05", "完成原料兼容性评估"),
    },
    {
        "project_no": "PRJ-2026-SOD-013",
        "name": "钠离子电池正极材料表面包覆研究",
        "project_type_code": "新能源材料",
        "owner_name": "李娜",
        "current_stage": "待开始",
        "progress_percent": 10,
        "document_count": 5,
        "experiment_count": 3,
        "status": "not_started",
        "planned_start_date": "2026-07-25",
        "planned_end_date": "2027-03-15",
        "description": "优化钠离子电池正极材料包覆层组成及厚度，提升循环与倍率性能。",
        "milestones": milestone("2026-08-18", "完成包覆工艺基线实验"),
    },
    {
        "project_no": "PRJ-2026-CAT-014",
        "name": "低温脱硝催化剂活性提升研究",
        "project_type_code": "绿色化工",
        "owner_name": "王强",
        "current_stage": "待开始",
        "progress_percent": 6,
        "document_count": 4,
        "experiment_count": 3,
        "status": "not_started",
        "planned_start_date": "2026-08-10",
        "planned_end_date": "2027-02-10",
        "description": "开发低温区间高活性脱硝催化剂，降低水硫条件下活性衰减。",
        "milestones": milestone("2026-09-01", "完成催化剂制备方案评审"),
    },
    {
        "project_no": "PRJ-2026-PI-015",
        "name": "聚酰亚胺气凝胶隔热材料开发",
        "project_type_code": "聚酰亚胺",
        "owner_name": "刘洋",
        "current_stage": "待开始",
        "progress_percent": 4,
        "document_count": 3,
        "experiment_count": 3,
        "status": "not_started",
        "planned_start_date": "2026-09-01",
        "planned_end_date": "2027-06-30",
        "description": "开发轻质聚酰亚胺气凝胶隔热材料，优化孔结构、力学强度与耐热性能。",
        "milestones": milestone("2026-09-25", "完成冻干工艺初步验证"),
    },
    {
        "project_no": "PRJ-2026-MEM-016",
        "name": "耐溶剂纳滤膜分离性能研究",
        "project_type_code": "功能材料",
        "owner_name": "周浩",
        "current_stage": "待开始",
        "progress_percent": 7,
        "document_count": 4,
        "experiment_count": 3,
        "status": "not_started",
        "planned_start_date": "2026-08-20",
        "planned_end_date": "2027-04-30",
        "description": "开发耐溶剂纳滤膜，建立膜结构、溶剂稳定性与分离效率之间的关系。",
        "milestones": milestone("2026-09-10", "完成膜材料候选清单"),
    },
    {
        "project_no": "PRJ-2026-REC-017",
        "name": "废旧复合材料化学回收工艺验证",
        "project_type_code": "绿色化工",
        "owner_name": "陈思",
        "current_stage": "待开始",
        "progress_percent": 3,
        "document_count": 2,
        "experiment_count": 2,
        "status": "not_started",
        "planned_start_date": "2026-09-15",
        "planned_end_date": "2027-05-31",
        "description": "验证废旧复合材料低能耗化学回收路线，提高树脂和增强相回收利用率。",
        "milestones": milestone("2026-10-10", "完成小试反应装置搭建"),
    },
    {
        "project_no": "PRJ-2025-EP-018",
        "name": "轨道交通用环氧防护涂层研究",
        "project_type_code": "环氧树脂",
        "owner_name": "赵敏",
        "current_stage": "已归档",
        "progress_percent": 100,
        "document_count": 18,
        "experiment_count": 5,
        "status": "archived",
        "planned_start_date": "2025-05-01",
        "planned_end_date": "2026-06-20",
        "description": "形成轨道交通用耐候环氧防护涂层配方及施工工艺。",
        "milestones": milestone("2026-06-20", "项目验收完成", "done"),
    },
    {
        "project_no": "PRJ-2025-PI-019",
        "name": "柔性基板用聚酰亚胺薄膜性能研究",
        "project_type_code": "聚酰亚胺",
        "owner_name": "刘洋",
        "current_stage": "已归档",
        "progress_percent": 100,
        "document_count": 16,
        "experiment_count": 6,
        "status": "archived",
        "planned_start_date": "2025-04-10",
        "planned_end_date": "2026-06-15",
        "description": "完成柔性基板用聚酰亚胺薄膜的热学、力学和介电性能评价。",
        "milestones": milestone("2026-06-15", "项目归档完成", "done"),
    },
    {
        "project_no": "PRJ-2025-FC-020",
        "name": "燃料电池双极板耐蚀涂层开发",
        "project_type_code": "新能源材料",
        "owner_name": "李娜",
        "current_stage": "已归档",
        "progress_percent": 100,
        "document_count": 14,
        "experiment_count": 6,
        "status": "archived",
        "planned_start_date": "2025-03-01",
        "planned_end_date": "2026-05-28",
        "description": "完成燃料电池双极板耐蚀导电涂层配方与寿命验证。",
        "milestones": milestone("2026-05-28", "结题验收完成", "done"),
    },
]


class Command(BaseCommand):
    """按原型创建完整开发项目数据。"""

    help = "按原型初始化 20 个开发演示项目，可重复执行"

    def add_arguments(self, parser) -> None:
        """注册命令参数。

        Args:
            parser: Django 命令参数解析器。
        """
        parser.add_argument("--owner-username", default="admin")

    def _prepare_people(self, actor: User) -> dict[str, User]:
        """准备项目负责人用户。

        Args:
            actor: 执行初始化的管理员。

        Returns:
            姓名到用户的映射。

        Raises:
            CommandError: 研究人员角色不存在。
        """
        researcher_role = Role.objects.filter(
            organization=actor.organization,
            role_code="researcher",
        ).first()
        if not researcher_role:
            raise CommandError("请先执行 bootstrap_development 初始化角色")

        people = {}
        for display_name, username in DEMO_PEOPLE.items():
            user, created = User.objects.get_or_create(
                organization=actor.organization,
                username=username,
                defaults={"display_name": display_name},
            )
            user.display_name = display_name
            user.is_active = True
            if created:
                user.set_unusable_password()
            user.save()
            if username.startswith("demo_"):
                UserRole.objects.get_or_create(
                    organization=actor.organization,
                    user=user,
                    role=researcher_role,
                )
            people[display_name] = user
        return people

    @transaction.atomic
    def handle(self, *args, **options) -> None:
        """执行初始化。

        Args:
            *args: 未使用的位置参数。
            **options: 命令参数。

        Raises:
            CommandError: 管理员账号不存在或不唯一。
        """
        actors = list(
            User.objects.filter(
                username=options["owner_username"],
                is_active=True,
            )[:2]
        )
        if len(actors) != 1:
            raise CommandError("开发项目初始化管理员不存在或用户名不唯一")
        actor = actors[0]
        people = self._prepare_people(actor)

        created_count = 0
        updated_count = 0
        for project_data in DEVELOPMENT_PROJECTS:
            owner_name = project_data["owner_name"]
            description = project_data["description"]
            project_defaults = {
                "name": project_data["name"],
                "project_type_code": (
                    "环氧树脂"
                    if project_data["project_type_code"] == "环氧树脂"
                    else "聚酰亚胺"
                ),
                "owner": people[owner_name],
                "current_stage": project_data["current_stage"],
                "progress_percent": project_data["progress_percent"],
                "document_count": project_data["document_count"],
                "experiment_count": project_data["experiment_count"],
                # 数据资产功能尚未上线，计数必须保持可回查的真实零值。
                "data_resource_count": 0,
                "status": project_data["status"],
                "planned_start_date": aware_at(
                    project_data["planned_start_date"],
                    time(hour=9),
                ),
                "planned_end_date": aware_at(
                    project_data["planned_end_date"],
                    time(hour=18),
                ),
                "description": description,
                "objectives": [description],
                "milestones": project_milestones(project_data),
                "created_by": actor,
                "updated_by": actor,
            }
            project = Project.objects.filter(
                organization=actor.organization,
                project_no=project_data["project_no"],
            ).first()
            created = project is None
            if project is None:
                project = Project.objects.filter(
                    organization=actor.organization,
                    project_no__regex=r"^PRJ-2026-\d{6}$",
                    name__in=LEGACY_PROJECT_NAMES.get(
                        project_data["project_no"],
                        [],
                    ),
                    created_by=actor,
                ).first()
                created = project is None
            if project is None:
                project = Project.objects.create(
                    organization=actor.organization,
                    project_no=project_data["project_no"],
                    **project_defaults,
                )
            else:
                project.project_no = project_data["project_no"]
                for field_name, value in project_defaults.items():
                    setattr(project, field_name, value)
                project.save()
            selected_people = {
                people[owner_name].id: (
                    people[owner_name],
                    ProjectMemberRole.OWNER,
                ),
                people["李娜"].id: (people["李娜"], ProjectMemberRole.RESEARCHER),
                people["王强"].id: (people["王强"], ProjectMemberRole.RESEARCHER),
            }
            selected_people[people[owner_name].id] = (
                people[owner_name],
                ProjectMemberRole.OWNER,
            )
            ProjectMember.objects.filter(project=project).exclude(
                user_id__in=selected_people,
            ).delete()
            for member_user, member_role in selected_people.values():
                ProjectMember.objects.update_or_create(
                    project=project,
                    user=member_user,
                    defaults={
                        "organization": actor.organization,
                        "member_role": member_role,
                        "created_by": actor,
                    },
                )
            created_count += int(created)
            updated_count += int(not created)

        self.stdout.write(
            self.style.SUCCESS(
                "原型项目初始化完成，"
                f"新增 {created_count} 个，更新 {updated_count} 个，"
                f"共 {len(DEVELOPMENT_PROJECTS)} 个"
            )
        )
