package com.materialslab.api.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.materialslab.api.identity.service.DevelopmentDataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

/** 验证配置阶段即拒绝混合 Profile，并为开发种子保留独立的生产排除条件。 */
class ProductionProfileGuardTest {
    @Test
    void rejectsMixedProfilesBeforeBeanCreation() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "dev");
        var guard = new ProductionProfileGuard();
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(environment, null));
        assertTrue(guard.getOrder() > ConfigDataEnvironmentPostProcessor.ORDER);
        assertTrue(!environment.acceptsProfiles(Profiles.of(DevelopmentDataInitializer.class.getAnnotation(Profile.class).value())));
    }

    @Test
    void preservesProductionOnlyAndDevelopmentOnlyStartup() {
        var environment = new MockEnvironment();
        var guard = new ProductionProfileGuard();
        environment.setActiveProfiles("prod");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
        environment.setActiveProfiles("dev");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void alsoRejectsMixedDefaultProfiles() {
        var environment = new MockEnvironment();
        environment.setDefaultProfiles("prod", "dev");
        assertThrows(IllegalStateException.class, () -> new ProductionProfileGuard().postProcessEnvironment(environment, null));
    }
}
