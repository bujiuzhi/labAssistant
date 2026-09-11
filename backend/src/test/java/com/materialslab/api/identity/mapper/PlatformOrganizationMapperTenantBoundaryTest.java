package com.materialslab.api.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/** 验证内部平台组织不会作为租户显示给平台控制面。 */
class PlatformOrganizationMapperTenantBoundaryTest {
    @Test
    void 租户目录应排除内部平台组织() throws NoSuchMethodException {
        Method method = PlatformOrganizationMapper.class.getMethod("listOrganizations");
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("WHERE is_platform = FALSE");
    }
}
