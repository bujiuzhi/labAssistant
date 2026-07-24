"""项目模型后台管理。"""

from django.contrib import admin

from .models import BusinessNumberSequence, Project, ProjectMember

admin.site.register(Project)
admin.site.register(ProjectMember)
admin.site.register(BusinessNumberSequence)
