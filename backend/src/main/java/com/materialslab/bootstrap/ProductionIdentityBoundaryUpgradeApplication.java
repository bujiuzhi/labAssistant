package com.materialslab.bootstrap;

import com.materialslab.api.common.config.SchemaMigrationInitializer;
import com.materialslab.api.identity.security.PasswordEncodingConfiguration;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** 将首版双身份管理员一次性拆分为租户管理员和平台管理员的受控入口。 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(PasswordEncodingConfiguration.class)
public class ProductionIdentityBoundaryUpgradeApplication {
    /** 不接受命令行覆盖；所有身份参数来自 Compose 环境与 Docker Secret。 */
    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("生产身份边界升级不接受命令行参数");
        SpringApplication application = new SpringApplication(ProductionIdentityBoundaryUpgradeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("prod");
        try (var context = application.run()) {
            // 一次性任务由 ApplicationRunner 完成，关闭上下文以释放数据库连接。
        }
    }

    /** 迁移结构后在同一事务内完成账号拆分；只支持明确的首版双身份形态。 */
    @Bean
    ApplicationRunner productionIdentityBoundaryUpgrade(DataSource dataSource, PasswordEncoder passwordEncoder, Environment environment) {
        if (!Arrays.asList(environment.getActiveProfiles()).equals(java.util.List.of("prod"))) {
            throw new IllegalStateException("生产身份边界升级只允许 prod Profile");
        }
        BootstrapSettings settings = BootstrapSettings.from(environment);
        String encodedPlatformPassword = passwordEncoder.encode(BootstrapSettings.readPlatformAdminPassword(environment, settings));
        var migrator = new LegacyIdentityBoundaryMigrator(new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        return args -> {
            new SchemaMigrationInitializer(dataSource).run(args);
            migrator.migrate(settings, encodedPlatformPassword);
        };
    }
}
