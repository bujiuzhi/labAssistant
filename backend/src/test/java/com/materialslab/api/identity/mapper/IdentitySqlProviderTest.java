package com.materialslab.api.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证管理员用户筛选 SQL。 */
class IdentitySqlProviderTest {
    @Test
    void 用户列表与总数查询应使用一致的筛选条件() {
        Map<String, Object> parameters = Map.of(
                "organizationId", "org", "status", "active", "search", "材料", "roleCode", "researcher", "limit", 20, "offset", 0);

        String listSql = new IdentitySqlProvider().listManagedUsers(parameters);
        String countSql = new IdentitySqlProvider().countManagedUsers(parameters);

        assertThat(listSql).contains("string_agg", "u.status = #{status}", "u.display_name ILIKE", "filtered_r.role_code = #{roleCode}", "LIMIT #{limit}");
        assertThat(countSql).contains("SELECT COUNT(*)", "u.status = #{status}", "u.display_name ILIKE", "filtered_r.role_code = #{roleCode}");
    }
}
