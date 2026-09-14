package com.materialslab.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/** 验证生产初始化参数无默认测试账号，密码通过 Secret 文件统一校验。 */
class BootstrapSettingsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresExplicitIdentityAndValidatesSchemaLengths() {
        var environment = environment();
        assertEquals("platform.owner", BootstrapSettings.from(environment).platformAdminUsername());
        environment.setProperty("MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_USERNAME", "A".repeat(65));
        assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.from(environment));
        assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.from(new MockEnvironment()));
    }

    @Test
    void readsPlatformAdministratorSecret() throws IOException {
        Path secret = temporaryDirectory.resolve("platform-admin-password");
        Files.writeString(secret, "platform@123!\n");
        var environment = environment().withProperty("MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD_FILE", secret.toString());

        assertEquals("platform@123!", BootstrapSettings.readPlatformAdminPassword(environment, BootstrapSettings.from(environment)));
    }

    @Test
    void rejectsWeakOrBcryptTruncatedPasswordsWithoutExposingThem() {
        for (String password : new String[] {"a1234", "000000", "abcdef", "中12345", "pass word", "admin\u0000", "Aa1" + "中".repeat(24)}) {
            var exception = assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.validatePassword(password));
            assertTrue(!exception.getMessage().contains(password));
        }
    }

    @Test
    void rejectsPasswordMatchingBootstrapUsername() {
        assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.validatePassword("production.owner", "production.owner"));
    }

    @Test
    void refusesMissingSecretInsteadOfUsingEnvironmentPassword() {
        var environment = environment().withProperty("MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD", "Example.StrongPassword42!");
        assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.readPlatformAdminPassword(environment, BootstrapSettings.from(environment)));
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_USERNAME", "platform.owner")
                .withProperty("MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_DISPLAY_NAME", "平台管理员");
    }
}
