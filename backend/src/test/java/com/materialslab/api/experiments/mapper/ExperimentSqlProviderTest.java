package com.materialslab.api.experiments.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证实验列表 SQL 的范围和筛选条件。 */
class ExperimentSqlProviderTest {
    @Test
    void 应按组织范围筛选并支持项目状态和关键字条件() {
        String sql = new ExperimentSqlProvider().findVisible(Map.of(
                "organizationId", "org", "userId", "user", "readAll", false, "projectId", "project", "status", "in_progress", "search", "配方", "limit", 20, "offset", 0));
        assertThat(sql).contains("e.organization_id = #{organizationId}", "#{readAll} = TRUE", "e.owner_id = #{userId}",
                "experiment_participant", "project_member", "e.project_id = #{projectId}", "e.status = #{status}", "e.name ILIKE");
    }

    @Test
    void 总数查询应复用列表筛选条件() {
        String sql = new ExperimentSqlProvider().countVisible(Map.of(
                "organizationId", "org", "userId", "user", "readAll", false, "projectId", "project", "status", "in_progress", "search", "配方"));
        assertThat(sql).contains("SELECT COUNT(*)", "e.organization_id = #{organizationId}", "#{readAll} = TRUE",
                "e.owner_id = #{userId}", "experiment_participant", "project_member", "e.project_id = #{projectId}",
                "e.status = #{status}", "e.name ILIKE");
    }
}
