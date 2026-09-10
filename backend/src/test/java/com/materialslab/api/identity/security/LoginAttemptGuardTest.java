package com.materialslab.api.identity.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialslab.api.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

/** 验证登录连续失败被有界地临时限制，成功后可恢复。 */
class LoginAttemptGuardTest {
    @Test
    void 连续五次失败后应拒绝下一次登录() {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        for (int index = 0; index < 5; index += 1) guard.recordFailure("Researcher");

        assertThatThrownBy(() -> guard.requireAllowed("researcher"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("login_temporarily_locked");
    }

    @Test
    void 成功登录应清除先前失败记录() {
        LoginAttemptGuard guard = new LoginAttemptGuard();
        guard.recordFailure("researcher");
        guard.recordSuccess("researcher");

        assertThatCode(() -> guard.requireAllowed("researcher")).doesNotThrowAnyException();
    }
}
