"""开发数据与最新原型基线的一致性测试。"""

from pathlib import Path

import pytest
from apps.experiments.management.commands.seed_development_experiments import (
    CORE_EXPERIMENTS,
)
from apps.experiments.models import Experiment
from apps.identity.models import User
from apps.projects.management.commands.seed_development_projects import (
    DEVELOPMENT_PROJECTS,
)
from apps.projects.models import Project
from django.core.management import call_command
from django.test import override_settings


@pytest.mark.django_db
def test_project_seed_uses_traceable_counts_and_prototype_milestones() -> None:
    """项目种子应保留原型关键展示数据且不虚构未上线的数据资产。"""
    call_command("bootstrap_development")
    call_command("seed_development_projects")
    call_command("seed_development_projects")

    pla_project = Project.objects.get(project_no="PRJ-2026-PLA-001")
    assert pla_project.milestones == [
        {"date": "2026-05-16", "name": "方案设计", "state": "done"},
        {
            "date": "2026-07-25",
            "name": "完成第三轮配方筛选",
            "state": "current",
        },
        {"date": "2026-07-31", "name": "实验验证", "state": "todo"},
        {"date": "2026-10-15", "name": "阶段评审", "state": "todo"},
        {"date": "2026-12-31", "name": "项目归档", "state": "todo"},
    ]

    pi_project = Project.objects.get(project_no="PRJ-2026-PI-005")
    assert pi_project.owner.username == "researcher"
    assert Project.objects.count() == 20
    assert not Project.objects.exclude(data_resource_count=0).exists()


def test_experiment_seed_keeps_prototype_recent_experiment_dates() -> None:
    """原型详情中的三条最近实验应使用同一套计划日期。"""
    experiments = {item["experiment_no"]: item for item in CORE_EXPERIMENTS}

    expected_start_dates = {
        "EXP-2026-018": "2026-06-30 09:00",
        "EXP-2026-019": "2026-07-18 09:30",
        "EXP-2026-016": "2026-07-10 10:00",
    }
    assert {
        experiment_no: experiments[experiment_no]["estimated_start"]
        for experiment_no in expected_start_dates
    } == expected_start_dates


@pytest.mark.django_db
def test_experiment_seed_never_uses_projects_outside_development_baseline(
    tmp_path: Path,
) -> None:
    """实验种子不得污染用户后续创建的项目或其统计字段。"""
    call_command("bootstrap_development")
    call_command("seed_development_projects")
    admin = User.objects.get(username="admin")
    user_project = Project.objects.create(
        organization=admin.organization,
        project_no="PRJ-2026-USER-001",
        name="用户独立创建项目",
        project_type_code="other",
        owner=admin,
        created_by=admin,
        updated_by=admin,
    )

    with override_settings(MEDIA_ROOT=tmp_path):
        call_command("seed_development_experiments")
        call_command("seed_development_experiments")

    generated_numbers = [f"EXP-2026-{number:03d}" for number in range(200, 284)]
    development_project_numbers = {
        project["project_no"] for project in DEVELOPMENT_PROJECTS
    }
    generated_project_numbers = set(
        Experiment.objects.filter(experiment_no__in=generated_numbers).values_list(
            "project__project_no",
            flat=True,
        )
    )
    user_project.refresh_from_db()

    assert generated_project_numbers <= development_project_numbers
    assert Experiment.objects.count() == 94
    assert user_project.experiment_count == 0
