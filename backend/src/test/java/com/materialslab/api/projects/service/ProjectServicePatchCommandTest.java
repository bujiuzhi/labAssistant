package com.materialslab.api.projects.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.projects.domain.Project;
import com.materialslab.api.projects.mapper.ProjectMapper;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 验证项目部分更新不会清空未提交字段。 */
class ProjectServicePatchCommandTest {
    /** 仅更新里程碑时应沿用项目名称和其他基础信息。 */
    @Test
    void shouldPreserveExistingFieldsWhenOnlyMilestonesArePatched() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.parse("2026-08-01T08:00:00+08:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-12-01T18:00:00+08:00");
        Project existing = new Project(projectId, organizationId, "PRJ-001", "材料项目", "聚酰亚胺",
                "项目描述", "实验执行", 45, "active", ownerId, "负责人", "[\"目标\"]", "[]",
                0, 0, 0, start, end, null, null, 3, start, start);
        ProjectMapper mapper = (ProjectMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ProjectMapper.class}, (proxy, method, args) -> null);
        ObjectMapper objectMapper = new ObjectMapper();
        ProjectService service = new ProjectService(mapper, null, objectMapper, new AccessControlService());

        var command = service.command(existing, organizationId, ownerId,
                objectMapper.readTree("{\"milestones\":[{\"name\":\"中试\",\"date\":\"2026-10-01\",\"state\":\"current\"}]}"),
                existing.version(), existing.projectNo());

        assertEquals(existing.name(), command.name());
        assertEquals(existing.ownerId(), command.ownerId());
        assertEquals(existing.plannedStartDate(), command.plannedStartDate());
        assertEquals(existing.objectives(), command.objectives());
        assertEquals("中试", objectMapper.readTree(command.milestones()).get(0).path("name").asText());
    }
}
