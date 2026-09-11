package com.materialslab.api.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/** 验证用户选项查询不能因组织层级泄露其他租户账户。 */
class IdentityMapperTenantIsolationTest {
    @Test
    void 用户选项应只查询当前组织() throws NoSuchMethodException {
        Method method = IdentityMapper.class.getMethod("listVisibleActiveUsers", UUID.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("organization_id = #{organizationId}")
                .doesNotContain("WITH RECURSIVE", "parent_id", "IN (SELECT id FROM visible_org)");
    }

    @Test
    void 普通用户角色选项应排除超级管理员() throws NoSuchMethodException {
        Method method = IdentityMapper.class.getMethod("listManagedRoleOptions", UUID.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("role_code <> 'super_admin'");
    }
}
