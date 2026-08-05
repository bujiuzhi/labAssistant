package com.materialslab.api.common.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 在业务初始化前执行 PostgreSQL 结构迁移。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrationInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaMigrationInitializer.class);
    private final DataSource dataSource;

    public SchemaMigrationInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 执行 classpath 中的 Flyway 迁移并记录结果。 */
    @Override
    public void run(ApplicationArguments args) {
        var result = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        LOGGER.info("Flyway 迁移完成，已执行 {} 个版本", result.migrationsExecuted);
    }
}
