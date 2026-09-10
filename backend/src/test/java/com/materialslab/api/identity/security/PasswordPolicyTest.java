package com.materialslab.api.identity.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialslab.api.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

/** 验证管理员创建与重置密码共享的生产强度策略。 */
class PasswordPolicyTest {
    @Test
    void 应接受至少六位且包含字母数字的普通密码() {
        assertThatCode(() -> PasswordPolicy.validateManagedPassword("ab1234", "researcher"))
                .doesNotThrowAnyException();
    }

    @Test
    void 应拒绝短密码单一字符类别含空白密码和用户名口令() {
        for (String password : new String[] { "a1234", "abcdef", "123456", "中12345", "pass word", "researcher" }) {
            assertThatThrownBy(() -> PasswordPolicy.validateManagedPassword(password, "researcher"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).code()).isEqualTo("validation_error");
        }
    }
}
