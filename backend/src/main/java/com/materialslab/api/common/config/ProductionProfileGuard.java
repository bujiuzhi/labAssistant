package com.materialslab.api.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** 在配置加载后、创建任何 Bean 前拒绝同时激活生产、开发 Profile。 */
public class ProductionProfileGuard implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(Profiles.of("prod")) && environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException("禁止同时启用 prod 与 dev Profile；未执行初始化");
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
