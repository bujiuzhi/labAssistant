package com.materialslab.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.env.Environment;

/** 一次性初始化的真实身份参数；密码只从 Secret 文件读取，不保留在配置对象中。 */
record BootstrapSettings(String organizationCode, String organizationName, String organizationAdminUsername,
                         String organizationAdminDisplayName, String platformAdminUsername, String platformAdminDisplayName) {
    static BootstrapSettings from(Environment environment) {
        String code = required(environment, "ORGANIZATION_CODE", 32);
        String organizationAdminUsername = required(environment, "ADMIN_USERNAME", 64);
        String platformAdminUsername = required(environment, "PLATFORM_ADMIN_USERNAME", 64);
        if (code.equalsIgnoreCase("DEV_TEST")) {
            throw new IllegalArgumentException("DEV_TEST 为开发环境保留组织编码，禁止用于生产首次初始化");
        }
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9_-]*") || !organizationAdminUsername.matches("[A-Za-z0-9][A-Za-z0-9_.@-]*")
                || !platformAdminUsername.matches("[A-Za-z0-9][A-Za-z0-9_.@-]*")) {
            throw new IllegalArgumentException("组织编码与管理员用户名含不支持的字符");
        }
        if (organizationAdminUsername.equalsIgnoreCase(platformAdminUsername)) {
            throw new IllegalArgumentException("平台管理员与组织管理员必须使用不同用户名");
        }
        return new BootstrapSettings(code, required(environment, "ORGANIZATION_NAME", 200), organizationAdminUsername,
                required(environment, "ADMIN_DISPLAY_NAME", 100), platformAdminUsername,
                required(environment, "PLATFORM_ADMIN_DISPLAY_NAME", 100));
    }

    /** 读取最多 72 个 UTF-8 字节的首次管理员密码；允许 Secret 文件末尾有一个换行，不裁剪密码空格。 */
    static String readOrganizationAdminPassword(Environment environment, BootstrapSettings settings) {
        return readPassword(environment, "ADMIN_PASSWORD_FILE", settings.organizationAdminUsername());
    }

    /** 读取平台管理员密码；平台控制面与业务租户管理员必须使用独立凭据。 */
    static String readPlatformAdminPassword(Environment environment, BootstrapSettings settings) {
        return readPassword(environment, "PLATFORM_ADMIN_PASSWORD_FILE", settings.platformAdminUsername());
    }

    /** 读取管理员密码，并按对应管理员用户名校验口令。 */
    private static String readPassword(Environment environment, String suffix, String username) {
        String secretPath = required(environment, suffix, 4096);
        try {
            Path path = Path.of(secretPath);
            if (!Files.isRegularFile(path) || Files.size(path) > 74) {
                throw new IllegalArgumentException("管理员密码 Secret 必须为不超过 74 字节的普通文件");
            }
            String password = Files.readString(path, StandardCharsets.UTF_8).replaceFirst("\\r?\\n$", "");
            validatePassword(password, username);
            return password;
        } catch (IOException | java.nio.file.InvalidPathException exception) {
            // 不附带路径或原始异常，避免将 Secret 目录与文件内容写入启动日志。
            throw new IllegalArgumentException("无法读取管理员密码 Secret 文件");
        }
    }

    static void validatePassword(String password) {
        validatePassword(password, null);
    }

    static void validatePassword(String password, String username) {
        if (password == null || password.length() < 6 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character))
                || password.chars().noneMatch(character -> (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z'))
                || password.chars().noneMatch(character -> character >= '0' && character <= '9')
                || (username != null && password.equalsIgnoreCase(username.trim()))) {
            throw new IllegalArgumentException("管理员密码须至少 6 个字符且不超过 72 个 UTF-8 字节，包含英文字母和数字，无空白或控制字符且不得与用户名相同");
        }
    }

    private static String required(Environment environment, String suffix, int maxLength) {
        String key = "MATERIALS_LAB_BOOTSTRAP_" + suffix;
        String value = environment.getProperty(key);
        if (value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(key + " 必须显式配置，且满足长度和格式限制");
        }
        return value;
    }
}
