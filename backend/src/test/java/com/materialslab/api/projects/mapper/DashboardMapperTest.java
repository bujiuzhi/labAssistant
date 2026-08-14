package com.materialslab.api.projects.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/** 验证项目总览查询的业务排序规则。 */
class DashboardMapperTest {
    @Test
    void 进行中项目应优先展示当前用户收藏的项目() throws NoSuchMethodException {
        Select select = DashboardMapper.class
                .getMethod("activeProjects", UUID.class, UUID.class, boolean.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value()));

        assertThat(sql).contains("ORDER BY followed DESC, p.updated_at DESC");
    }
}
