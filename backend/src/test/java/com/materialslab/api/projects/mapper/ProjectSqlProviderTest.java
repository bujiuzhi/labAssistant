package com.materialslab.api.projects.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证项目数据范围与组合筛选 SQL。 */
class ProjectSqlProviderTest {
    @Test
    void 应按当前组织和成员关系限制可见项目并附加筛选条件() {
        String sql = new ProjectSqlProvider().findVisible(Map.of(
                "organizationId", "org", "userId", "user", "readAll", false, "status", "active", "search", "材料", "limit", 20, "offset", 0));
        assertThat(sql).contains("p.organization_id = #{organizationId}", "#{readAll} = TRUE", "project_member", "p.status = #{status}", "p.name ILIKE", "LIMIT #{limit}");
    }

    @Test
    void 总数查询应复用列表的数据范围和筛选条件() {
        String sql = new ProjectSqlProvider().countVisible(Map.of(
                "organizationId", "org", "userId", "user", "readAll", false, "status", "active", "search", "材料"));
        assertThat(sql).contains("SELECT COUNT(*)", "p.organization_id = #{organizationId}", "#{readAll} = TRUE", "project_member", "p.status = #{status}", "p.name ILIKE");
    }

    @Test
    void 已结束筛选应同时包含已完成和已归档项目() {
        Map<String, Object> parameters = Map.of(
                "organizationId", "org", "userId", "user", "readAll", false,
                "status", "ended", "search", "", "limit", 20, "offset", 0);

        assertThat(new ProjectSqlProvider().findVisible(parameters))
                .contains("p.status IN ('completed', 'archived')")
                .doesNotContain("p.status = #{status}");
        assertThat(new ProjectSqlProvider().countVisible(parameters))
                .contains("p.status IN ('completed', 'archived')")
                .doesNotContain("p.status = #{status}");
    }
}
