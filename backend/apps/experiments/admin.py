"""电子实验记录本后台管理注册。"""

from django.contrib import admin

from .models import Experiment, ExperimentParticipant, ExperimentRecord


@admin.register(Experiment)
class ExperimentAdmin(admin.ModelAdmin):
    """实验计划后台管理。"""

    list_display = (
        "experiment_no",
        "name",
        "project",
        "owner",
        "status",
        "updated_at",
    )
    list_filter = ("status", "experiment_type")
    search_fields = ("experiment_no", "name", "project__name")


admin.site.register(ExperimentRecord)
admin.site.register(ExperimentParticipant)
