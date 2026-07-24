"""项目模型后台管理。"""

from django.contrib import admin

from .models import BusinessNumberSequence, Project, ProjectDocument, ProjectMember

admin.site.register(Project)
admin.site.register(ProjectMember)
admin.site.register(ProjectDocument)
admin.site.register(BusinessNumberSequence)
