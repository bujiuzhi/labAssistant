package com.materialslab.api.identity.security;

import com.materialslab.api.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/** 统一校验由系统管理员设置的账号密码，避免不同入口产生弱口令。 */
public final class PasswordPolicy {
    private PasswordPolicy() { }

    /**
     * 校验初始密码或重置密码。
     *
     * @param password 明文密码，仅在当前请求中使用
     * @param username 关联用户名，用于拒绝相同口令
     * @throws BusinessException 密码不满足生产策略时抛出
     */
    public static void validateManagedPassword(String password, String username) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character))
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)
                || (username != null && password.equalsIgnoreCase(username.trim()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error",
                    "密码须至少 8 个字符且不超过 72 个 UTF-8 字节，包含字母和数字，不含空白或控制字符且不得与用户名相同");
        }
    }
}
