"""按原型初始化可重复执行的电子实验记录本演示数据。"""

from datetime import datetime, timedelta

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from apps.experiments.models import (
    Experiment,
    ExperimentParticipant,
    ExperimentParticipantRole,
    ExperimentRecord,
)
from apps.identity.models import User
from apps.projects.models import Project

FORMULA_COLUMNS = [
    {"id": "material", "label": "原料名称"},
    {"id": "batch", "label": "规格/批次"},
    {"id": "amount", "label": "用量"},
    {"id": "unit", "label": "单位"},
    {"id": "note", "label": "备注"},
]

PI_FORMULA = [
    {
        "material": "聚酰亚胺树脂/聚酰胺酸溶液",
        "batch": "PI-PAA-26074",
        "amount": "100.0",
        "unit": "g",
        "note": "固含量 18±0.5 wt%",
    },
    {
        "material": "N,N-二甲基乙酰胺",
        "batch": "DMAc-26065",
        "amount": "50",
        "unit": "g",
        "note": "电子级溶剂",
    },
    {
        "material": "4,4′-二氨基二苯醚",
        "batch": "ODA-26054",
        "amount": "12.40",
        "unit": "g",
        "note": "芳香族二胺单体",
    },
    {
        "material": "均苯四甲酸二酐",
        "batch": "PMDA-26064",
        "amount": "13.55",
        "unit": "g",
        "note": "芳香族二酐单体",
    },
    {
        "material": "硅烷偶联剂",
        "batch": "S-260807",
        "amount": "0.80",
        "unit": "g",
        "note": "界面改性助剂",
    },
]

PLA_FORMULA = [
    {
        "material": "PLA",
        "batch": "4032D / P240618",
        "amount": "60.0",
        "unit": "g",
        "note": "主体树脂",
    },
    {
        "material": "PBAT",
        "batch": "TH801T / B240705",
        "amount": "30.0",
        "unit": "g",
        "note": "增韧组分",
    },
    {
        "material": "相容剂",
        "batch": "ADR-4468 / A240612",
        "amount": "5.0",
        "unit": "g",
        "note": "—",
    },
    {
        "material": "增塑剂",
        "batch": "ATBC / C240626",
        "amount": "5.0",
        "unit": "g",
        "note": "—",
    },
]

CORE_EXPERIMENTS = [
    {
        "experiment_no": "EXP-2026-107",
        "project_no": "PRJ-2026-PI-005",
        "name": "精密流延膜厚均匀性验证-批次03",
        "experiment_type": "工艺优化",
        "phase": "实验执行",
        "status": "in_progress",
        "estimated_start": "2026-07-23 08:30",
        "estimated_end": "2026-07-24 17:30",
        "updated_at": "2026-07-23 10:20",
        "purpose": (
            "验证精密流延间隙、基材速度与聚酰胺酸溶液黏度对膜厚均匀性的影响。"
            "目标干膜厚度为25±1.5 μm，横向厚度极差不超过3 μm，表面不得出现连续"
            "条纹、针孔或边缘收缩。"
        ),
        "formula_rows": PI_FORMULA,
        "process_text": (
            "08:30 核对PI-PAA-26074批次、固含量及黏度，溶液经10 μm滤芯过滤后"
            "在-0.095 MPa下脱泡30 min。\n"
            "09:15 清洁玻璃基材和流延辊，将刮刀间隙设置为190、210、230 μm，"
            "基材速度分别设置为0.8、1.0、1.2 m/min。\n"
            "09:50 完成第一组190 μm间隙样品流延，湿膜边缘稳定，未观察到明显气泡；"
            "取横向9点记录湿膜厚度。\n"
            "10:20 第二组210 μm间隙样品正在流延，在线测厚数据波动控制在±1.2 μm，"
            "待完成预烘及梯度亚胺化。"
        ),
    },
    {
        "experiment_no": "EXP-2026-108",
        "project_no": "PRJ-2026-PI-005",
        "name": "梯度亚胺化工艺窗口复测",
        "experiment_type": "工艺优化",
        "phase": "检测分析",
        "status": "completed",
        "estimated_start": "2026-07-21 09:00",
        "estimated_end": "2026-07-22 16:00",
        "updated_at": "2026-07-22 16:40",
        "purpose": "复测不同梯度升温程序对薄膜亚胺化程度、尺寸稳定性及外观质量的影响。",
        "formula_rows": PI_FORMULA,
        "process_text": "完成三组梯度升温程序、FTIR亚胺化程度和热收缩率复测。",
        "result_text": (
            "程序B的亚胺化程度为98.7%，纵向/横向热收缩率分别为0.21%和0.18%，"
            "确定120℃预烘、220℃中温转化、320℃终亚胺化为推荐工艺窗口。"
        ),
        "result_files": [
            {"name": "梯度亚胺化工艺复测数据.xlsx", "size": "36.8 KB"},
            {"name": "PI薄膜FTIR对比谱图.pdf", "size": "1.2 MB"},
        ],
    },
    {
        "experiment_no": "EXP-2026-109",
        "project_no": "PRJ-2026-PI-005",
        "name": "PI薄膜热稳定性与TGA测试",
        "experiment_type": "热分析",
        "phase": "检测分析",
        "status": "completed",
        "estimated_start": "2026-07-20 13:30",
        "estimated_end": "2026-07-21 14:00",
        "updated_at": "2026-07-21 14:15",
        "purpose": "评价优化工艺制备PI薄膜的热分解温度、玻璃化转变温度及高温残炭率。",
        "formula_rows": PI_FORMULA,
        "process_text": "完成左、中、右三点样品TGA与DMA测试并复核原始数据。",
        "result_text": "5%失重温度均值547.0℃，玻璃化转变温度367℃，各项指标达到阶段标准。",
        "result_files": [
            {"name": "PI-F-0720_TGA原始数据.xlsx", "size": "42.3 KB"},
            {"name": "热稳定性测试报告.pdf", "size": "886 KB"},
        ],
    },
    {
        "experiment_no": "EXP-2026-110",
        "project_no": "PRJ-2026-PI-005",
        "name": "薄膜介电性能测试-批次02",
        "experiment_type": "性能测试",
        "phase": "方案设计",
        "status": "not_started",
        "estimated_start": "2026-07-24 09:00",
        "estimated_end": "2026-07-25 16:30",
        "updated_at": "2026-07-23 09:10",
        "purpose": "测试优化工艺薄膜的介电常数、介电损耗及击穿强度。",
        "formula_rows": PI_FORMULA[:2],
        "process_text": "计划按横向左、中、右位置取样，完成介电频谱和阶梯升压测试。",
    },
    {
        "experiment_no": "EXP-2026-018",
        "project_no": "PRJ-2026-PLA-001",
        "name": "PLA/PBAT复合配方筛选-第三轮",
        "experiment_type": "配方筛选",
        "phase": "实验执行",
        "status": "in_progress",
        "estimated_start": "2026-07-20 09:00",
        "estimated_end": "2026-07-20 16:30",
        "updated_at": "2026-07-20 09:42",
        "purpose": "验证第三轮PLA/PBAT复合配方在混炼稳定性、样条成型完整性和柔韧性方面的表现。",
        "formula_rows": PLA_FORMULA,
        "process_text": (
            "09:20 按配方称量原料，置于80℃真空烘箱干燥4小时。\n"
            "14:10 依次加入密炼机，转速60 rpm，混炼温度165℃，持续8分钟。\n"
            "14:26 取出样品，冷却后切粒，样品外观均匀，无明显团聚。"
        ),
        "process_images": [
            {
                "name": "称量原料.jpg",
                "url": "/prototype/weighing-polymer-pellets.png",
                "size": "1.9 MB",
            },
            {
                "name": "混炼后样品.jpg",
                "url": "/prototype/mixed-polymer-sample.png",
                "size": "2.2 MB",
            },
        ],
        "result_text": (
            "本轮配方混炼稳定，样条成型完整。与第二轮相比，样品柔韧性提升，"
            "表面缺陷减少；待完成拉伸性能检测后确认最终配方。"
        ),
        "result_files": [{"name": "拉伸性能初测数据.xlsx", "size": "24.6 KB"}],
    },
    {
        "experiment_no": "EXP-2026-019",
        "project_no": "PRJ-2026-PLA-001",
        "name": "拉伸性能测试-批次02",
        "experiment_type": "性能测试",
        "phase": "方案设计",
        "status": "not_started",
        "estimated_start": "2026-07-22 09:30",
        "estimated_end": "2026-07-22 17:30",
        "updated_at": "2026-07-19 16:18",
        "purpose": "完成批次02样条拉伸性能测试，评估第三轮配方的强度与断裂伸长率。",
        "formula_rows": PLA_FORMULA,
        "process_text": "",
    },
    {
        "experiment_no": "EXP-2026-016",
        "project_no": "PRJ-2026-PLA-001",
        "name": "热重分析-样品A3",
        "experiment_type": "热分析",
        "phase": "检测分析",
        "status": "completed",
        "estimated_start": "2026-07-10 10:00",
        "estimated_end": "2026-07-10 15:30",
        "updated_at": "2026-07-18 11:05",
        "purpose": "采集样品A3热重分析曲线，确认热稳定性是否达到项目要求。",
        "formula_rows": PLA_FORMULA[:2],
        "process_text": "已完成样品升温与失重曲线采集。",
        "result_text": "样品5%失重温度达到阶段指标，数据有效。",
        "result_files": [{"name": "样品A3热重分析谱图.csv", "size": "18.2 KB"}],
    },
    {
        "experiment_no": "EXP-2026-015",
        "project_no": "PRJ-2026-PLA-001",
        "name": "界面相容性验证-样品B1",
        "experiment_type": "结构表征",
        "phase": "实验执行",
        "status": "in_progress",
        "estimated_start": "2026-07-17 09:00",
        "estimated_end": "2026-07-17 18:00",
        "updated_at": "2026-07-17 14:26",
        "purpose": "观察样品B1界面相容性与分散状态，判断相容剂方案是否需要调整。",
        "formula_rows": PLA_FORMULA[:3],
        "process_text": "已完成样品制备，等待显微观察。",
    },
    {
        "experiment_no": "EXP-2026-021",
        "project_no": "PRJ-2026-CO2-003",
        "name": "催化剂活性评价-批次04",
        "experiment_type": "性能测试",
        "phase": "检测分析",
        "status": "in_progress",
        "estimated_start": "2026-07-16 08:30",
        "estimated_end": "2026-07-22 18:00",
        "updated_at": "2026-07-16 11:05",
        "purpose": "验证第四批催化剂的CO₂转化率、目标产物选择性和连续运行稳定性。",
        "formula_rows": [
            {
                "material": "催化剂A",
                "batch": "CAT-A2406",
                "amount": "2.0",
                "unit": "g",
                "note": "反应催化剂",
            }
        ],
        "process_text": "",
    },
    {
        "experiment_no": "EXP-2026-022",
        "project_no": "PRJ-2026-BIO-004",
        "name": "环氧树脂固化窗口测试",
        "experiment_type": "工艺优化",
        "phase": "实验执行",
        "status": "in_progress",
        "estimated_start": "2026-07-14 09:00",
        "estimated_end": "2026-07-24 17:00",
        "updated_at": "2026-07-14 10:19",
        "purpose": "确定生物基环氧体系适用的固化温度、保温时间与后固化窗口。",
        "formula_rows": [
            {
                "material": "生物基环氧树脂",
                "batch": "BIO-E2407",
                "amount": "100",
                "unit": "g",
                "note": "主体树脂",
            }
        ],
        "process_text": "",
    },
]

STATUS_TARGETS = {"in_progress": 19, "not_started": 23, "completed": 52}
TYPE_SEQUENCE = ["配方筛选", "性能测试", "热分析", "结构表征", "工艺优化", "可靠性测试"]


def aware(value: str) -> datetime:
    """将原型日期文本转换为当前时区时间。

    Args:
        value: `YYYY-MM-DD HH:MM` 日期文本。

    Returns:
        带时区的日期时间。
    """
    return timezone.make_aware(datetime.strptime(value, "%Y-%m-%d %H:%M"))


class Command(BaseCommand):
    """初始化可重复执行的原型实验数据。"""

    help = "初始化电子实验记录本演示实验"

    @transaction.atomic
    def handle(self, *args, **options) -> None:
        """执行初始化。

        Args:
            *args: 未使用的位置参数。
            **options: 未使用的命令参数。

        Raises:
            CommandError: 依赖的项目或账号不存在。
        """
        actor = User.objects.filter(username="admin", is_active=True).first()
        researcher = User.objects.filter(username="researcher", is_active=True).first()
        if not actor or not researcher:
            raise CommandError("请先执行 bootstrap_development 初始化开发账号")
        projects = list(
            Project.objects.filter(organization=actor.organization).order_by("project_no")
        )
        if not projects:
            raise CommandError("请先执行 seed_development_projects 初始化项目")
        project_map = {project.project_no: project for project in projects}
        missing = {
            item["project_no"]
            for item in CORE_EXPERIMENTS
            if item["project_no"] not in project_map
        }
        if missing:
            raise CommandError(f"缺少原型项目：{', '.join(sorted(missing))}")

        specs = [dict(item) for item in CORE_EXPERIMENTS]
        existing_counts = {
            status: sum(item["status"] == status for item in specs)
            for status in STATUS_TARGETS
        }
        serial = 200
        base_time = aware("2026-07-01 08:00")
        for status, target in STATUS_TARGETS.items():
            for index in range(target - existing_counts[status]):
                project = projects[(serial + index) % len(projects)]
                experiment_type = TYPE_SEQUENCE[(serial + index) % len(TYPE_SEQUENCE)]
                number = serial
                serial += 1
                specs.append(
                    {
                        "experiment_no": f"EXP-2026-{number:03d}",
                        "project_no": project.project_no,
                        "name": (
                            f"{project.name.replace('研究', '').replace('开发', '')}"
                            f"—{experiment_type}批次{index % 6 + 1:02d}"
                        ),
                        "experiment_type": experiment_type,
                        "phase": (
                            "方案设计"
                            if status == "not_started"
                            else "检测分析"
                            if status == "completed"
                            else "实验执行"
                        ),
                        "status": status,
                        "estimated_start": (
                            base_time + timedelta(days=index % 24, hours=index % 8)
                        ).strftime("%Y-%m-%d %H:%M"),
                        "estimated_end": (
                            base_time + timedelta(days=index % 24 + 1, hours=index % 8)
                        ).strftime("%Y-%m-%d %H:%M"),
                        "updated_at": (
                            base_time + timedelta(days=index % 28, hours=index % 10)
                        ).strftime("%Y-%m-%d %H:%M"),
                        "purpose": f"围绕{project.name}开展{experiment_type}，记录关键过程和结果。",
                        "formula_rows": [
                            {
                                "material": "样品A",
                                "batch": f"B-{number:03d}",
                                "amount": "10.0",
                                "unit": "g",
                                "note": "原型演示样品",
                            }
                        ],
                        "process_text": (
                            "已完成实验准备与关键参数核对。"
                            if status != "not_started"
                            else "计划完成样品准备、实验执行与数据复核。"
                        ),
                        "result_text": (
                            "实验数据完整，关键指标达到阶段判定要求。"
                            if status == "completed"
                            else ""
                        ),
                    }
                )

        for spec in specs:
            project = project_map[spec["project_no"]]
            estimated_start = aware(spec["estimated_start"])
            estimated_end = aware(spec["estimated_end"])
            experiment, _ = Experiment.objects.update_or_create(
                organization=actor.organization,
                experiment_no=spec["experiment_no"],
                defaults={
                    "project": project,
                    "name": spec["name"],
                    "experiment_type": spec["experiment_type"],
                    "phase": spec["phase"],
                    "status": spec["status"],
                    "purpose": spec["purpose"],
                    "estimated_start": estimated_start,
                    "estimated_end": estimated_end,
                    "started_at": (
                        estimated_start if spec["status"] != "not_started" else None
                    ),
                    "completed_at": (
                        estimated_end if spec["status"] == "completed" else None
                    ),
                    "owner": researcher,
                    "created_by": actor,
                    "updated_by": actor,
                },
            )
            ExperimentRecord.objects.update_or_create(
                experiment=experiment,
                defaults={
                    "formula_columns": FORMULA_COLUMNS,
                    "formula_rows": spec["formula_rows"],
                    "extra_tables": spec.get("extra_tables", []),
                    "process_text": spec.get("process_text", ""),
                    "extra_processes": spec.get("extra_processes", []),
                    "process_images": spec.get("process_images", []),
                    "result_text": spec.get("result_text", ""),
                    "result_files": spec.get("result_files", []),
                },
            )
            ExperimentParticipant.objects.update_or_create(
                experiment=experiment,
                user=researcher,
                defaults={
                    "organization": actor.organization,
                    "participant_role": ExperimentParticipantRole.OWNER,
                    "created_by": actor,
                },
            )
            if project.owner_id != researcher.id:
                ExperimentParticipant.objects.update_or_create(
                    experiment=experiment,
                    user=project.owner,
                    defaults={
                        "organization": actor.organization,
                        "participant_role": ExperimentParticipantRole.PARTICIPANT,
                        "created_by": actor,
                    },
                )
            updated_at = aware(spec["updated_at"])
            Experiment.objects.filter(id=experiment.id).update(
                created_at=estimated_start,
                updated_at=updated_at,
            )
        for project in projects:
            project.experiment_count = Experiment.objects.filter(project=project).count()
            project.save(update_fields=["experiment_count"])

        self.stdout.write(
            self.style.SUCCESS(
                "电子实验记录本初始化完成："
                f"进行中 {STATUS_TARGETS['in_progress']}，"
                f"未开始 {STATUS_TARGETS['not_started']}，"
                f"已完成 {STATUS_TARGETS['completed']}"
            )
        )
