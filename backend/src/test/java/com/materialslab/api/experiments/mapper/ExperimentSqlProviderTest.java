package com.materialslab.api.experiments.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证实验列表 SQL 的范围和筛选条件。 */
class ExperimentSqlProviderTest {
    @Test
    void 应按组织范围筛选并支持项目状态和关键字条件() {
        String sql = new ExperimentSqlProvider().findVisible(Map.of(
                "organizationId", "org", "projectId", "project", "status", "in_progress", "search", "配方", "limit", 20, "offset", 0));
        assertThat(sql).contains("e.organization_id = #{organizationId}", "e.project_id = #{projectId}", "e.status = #{status}", "e.name ILIKE");
    }
}
