package com.materialslab.api.projects.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证项目数据范围与组合筛选 SQL。 */
class ProjectSqlProviderTest {
    @Test
    void 应按组织层级和成员关系限制可见项目并附加筛选条件() {
        String sql = new ProjectSqlProvider().findVisible(Map.of(
                "organizationId", "org", "userId", "user", "status", "active", "search", "材料", "limit", 20, "offset", 0));
        assertThat(sql).contains("WITH RECURSIVE visible_org", "project_member", "p.status = #{status}", "p.name ILIKE", "LIMIT #{limit}");
    }

    @Test
    void 总数查询应复用列表的数据范围和筛选条件() {
        String sql = new ProjectSqlProvider().countVisible(Map.of(
                "organizationId", "org", "userId", "user", "status", "active", "search", "材料"));
        assertThat(sql).contains("SELECT COUNT(*)", "WITH RECURSIVE visible_org", "project_member", "p.status = #{status}", "p.name ILIKE");
    }
}
