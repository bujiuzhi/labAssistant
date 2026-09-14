package com.materialslab.api.projects.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/** 验证项目总览查询的业务排序规则。 */
class DashboardMapperTest {
    @Test
    void 项目概览应展示未结束项目并按关注与状态排序() throws NoSuchMethodException {
        Select select = DashboardMapper.class
                .getMethod("overviewProjects", UUID.class, UUID.class, boolean.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value()));

        assertThat(sql).contains("p.status NOT IN ('completed', 'archived')", "ORDER BY followed DESC", "CASE p.status");
    }
}
