package com.materialslab.api.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证统一分页响应的元数据计算。 */
class PageResponseTest {
    @Test
    void 应返回前端约定的分页元数据() {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 2, 20, 42);

        assertThat(response.data()).containsExactly("a", "b");
        assertThat(response.meta().page()).isEqualTo(2);
        assertThat(response.meta().pageSize()).isEqualTo(20);
        assertThat(response.meta().total()).isEqualTo(42);
        assertThat(response.meta().totalPages()).isEqualTo(3);
        assertThat(response.requestId()).isNotBlank();
    }
}
