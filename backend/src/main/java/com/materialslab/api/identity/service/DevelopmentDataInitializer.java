package com.materialslab.api.identity.service;

import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 在空数据库初始化可登录的开发组织和账户，不覆盖既有数据。 */
@Component
public class DevelopmentDataInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentDataInitializer.class);
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String developmentPassword;
    public DevelopmentDataInitializer(DataSource dataSource, PasswordEncoder passwordEncoder,
                                      @Value("${materials-lab.development-password}") String developmentPassword) {
        this.jdbcTemplate = new JdbcTemplate(dataSource); this.passwordEncoder = passwordEncoder; this.developmentPassword = developmentPassword;
    }
    /** 初始化 admin、manager、researcher 三个开发账户。 */
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Long.class) > 0) return;
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organization(id, organization_code, name) VALUES (?, ?, ?)", organizationId, "DEV", "开发组织");
        createUser(organizationId, "admin", "刘李园", true); createUser(organizationId, "manager", "张伟", false); createUser(organizationId, "researcher", "李娜", false);
        LOGGER.info("空数据库已初始化开发账户：admin、manager、researcher");
    }
    private void createUser(UUID organizationId, String username, String displayName, boolean superAdmin) {
        jdbcTemplate.update("""
                INSERT INTO user_account(id, organization_id, password, username, display_name, is_super_admin, is_superuser, is_staff)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), organizationId, passwordEncoder.encode(developmentPassword), username, displayName, superAdmin, superAdmin, superAdmin);
    }
}
