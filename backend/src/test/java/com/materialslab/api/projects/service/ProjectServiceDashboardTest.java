package com.materialslab.api.projects.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.projects.domain.DashboardRows.OverviewProject;
import com.materialslab.api.projects.domain.DashboardRows.Metrics;
import com.materialslab.api.projects.domain.DashboardRows.ProjectOption;
import com.materialslab.api.projects.domain.DashboardRows.TrendEntry;
import com.materialslab.api.projects.domain.DashboardRows.TypeDistribution;
import com.materialslab.api.projects.mapper.DashboardMapper;
import com.materialslab.api.projects.mapper.ProjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 验证总览接口仅组装 Mapper 返回的数据库聚合结果。 */
class ProjectServiceDashboardTest {
    /** 组装结果应符合前端总览接口契约。 */
    @Test
    void shouldBuildDashboardFromDatabaseRows() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        DashboardMapper dashboardMapper = dashboardMapper(projectId, organizationId, userId);
        ProjectMapper projectMapper = (ProjectMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {ProjectMapper.class}, (proxy, method, args) -> null);
        ProjectService service = new ProjectService(projectMapper, dashboardMapper, new ObjectMapper(), new AccessControlService());
        UserPrincipal principal = new UserPrincipal(
                new UserAccount(userId, organizationId, "admin", "", "测试管理员", "active", true, false, 0), List.of());
        Map<String, Object> result = service.dashboard(principal, projectId);

        assertEquals(3L, ((Map<?, ?>) result.get("project_metrics")).get("total"));
        assertEquals(2L, ((Map<?, ?>) result.get("experiment_metrics")).get("in_progress"));
        assertEquals(1, ((List<?>) result.get("type_distribution")).size());
        Map<?, ?> trend = assertInstanceOf(Map.class, result.get("trend"));
        assertEquals(projectId.toString(), trend.get("selected_project_id"));
        Map<?, ?> overviewProject = assertInstanceOf(Map.class, ((List<?>) result.get("overview_projects")).getFirst());
        assertEquals("耐热薄膜验证", overviewProject.get("name"));
        assertEquals("not_started", overviewProject.get("status"));
        assertEquals(true, overviewProject.get("is_followed"));
    }

    private DashboardMapper dashboardMapper(UUID projectId, UUID organizationId, UUID userId) {
        return (DashboardMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {DashboardMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "projectMetrics" -> new Metrics(3, 1, 0, 1, 0, 0);
                    case "experimentMetrics" -> new Metrics(6, 0, 0, 0, 2, 2);
                    case "typeDistribution" -> List.of(new TypeDistribution("聚酰亚胺", 1, 2));
                    case "trendEntries" -> List.of(new TrendEntry(LocalDate.now(), "聚酰亚胺", 2));
                    case "overviewProjects" -> List.of(new OverviewProject(
                            projectId, "PRJ-TEST-001", "耐热薄膜验证", "聚酰亚胺", "测试项目管理员",
                            "[\"验证热稳定性\"]", "[{\"name\":\"中试\",\"date\":\"2026-08-18\",\"state\":\"current\"}]", 68,
                            OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), "not_started", true));
                    case "projectOptions" -> List.of(new ProjectOption(projectId, "PRJ-TEST-001", "耐热薄膜验证"));
                    default -> throw new AssertionError("未预期的 Mapper 调用：" + method.getName());
                });
    }
}
