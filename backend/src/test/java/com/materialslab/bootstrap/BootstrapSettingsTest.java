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

/** 验证生产初始化参数无默认测试账号，密码通过 Secret 文件强校验。 */
class BootstrapSettingsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresExplicitIdentityAndValidatesSchemaLengths() {
        var environment = environment();
        assertEquals("REAL_LAB", BootstrapSettings.from(environment).organizationCode());
        environment.setProperty("MATERIALS_LAB_BOOTSTRAP_ORGANIZATION_CODE", "A".repeat(33));
        assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.from(environment));
        assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.from(new MockEnvironment()));
    }

    @Test
    void readsSecretWithOptionalTrailingNewline() throws IOException {
        Path secret = temporaryDirectory.resolve("admin-password");
        Files.writeString(secret, "Example.StrongPassword42!\r\n");
        var environment = environment().withProperty("MATERIALS_LAB_BOOTSTRAP_ADMIN_PASSWORD_FILE", secret.toString());
        assertEquals("Example.StrongPassword42!", BootstrapSettings.readPassword(environment));
        assertTrue(!BootstrapSettings.from(environment).toString().contains("Password42"));
    }

    @Test
    void rejectsWeakOrBcryptTruncatedPasswordsWithoutExposingThem() {
        for (String password : new String[] {"00000000", "abcdefghij1234567890", "ABCDEF1234567890!",
                "Strong Password42!", "StrongPassword42!\u0000", "Aa1!" + "中".repeat(24)}) {
            var exception = assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.validatePassword(password));
            assertTrue(!exception.getMessage().contains(password));
        }
    }

    @Test
    void refusesMissingSecretInsteadOfUsingEnvironmentPassword() {
        var environment = environment().withProperty("MATERIALS_LAB_BOOTSTRAP_ADMIN_PASSWORD", "Example.StrongPassword42!");
        assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.readPassword(environment));
    }

    @Test
    void rejectsReservedDevelopmentOrganizationBeforeDatabaseAccess() {
        for (String code : new String[] {"DEV_TEST", "dev_test", "Dev_Test"}) {
            var environment = environment().withProperty("MATERIALS_LAB_BOOTSTRAP_ORGANIZATION_CODE", code);
            assertThrows(IllegalArgumentException.class, () -> BootstrapSettings.from(environment));
        }
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("MATERIALS_LAB_BOOTSTRAP_ORGANIZATION_CODE", "REAL_LAB")
                .withProperty("MATERIALS_LAB_BOOTSTRAP_ORGANIZATION_NAME", "正式实验组织")
                .withProperty("MATERIALS_LAB_BOOTSTRAP_ADMIN_USERNAME", "production.owner")
                .withProperty("MATERIALS_LAB_BOOTSTRAP_ADMIN_DISPLAY_NAME", "正式管理员");
    }
}
