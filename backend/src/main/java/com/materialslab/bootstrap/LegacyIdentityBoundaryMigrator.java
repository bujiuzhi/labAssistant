package com.materialslab.bootstrap;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅处理首版唯一双身份管理员，拒绝猜测或批量修复任意历史身份数据。 */
final class LegacyIdentityBoundaryMigrator {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    LegacyIdentityBoundaryMigrator(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /** 创建内部平台组织和独立平台账号，并将旧账号收敛为其原业务组织的超级管理员。 */
    void migrate(BootstrapSettings settings, String encodedPlatformPassword) {
        transactionTemplate.executeWithoutResult(status -> {
            List<UUID> legacyAdmins = jdbcTemplate.queryForList("""
                    SELECT id FROM user_account
                    WHERE is_super_admin AND is_platform_admin AND is_active AND status = 'active'
                    FOR UPDATE
                    """, UUID.class);
            long platformOrganizations = jdbcTemplate.queryForObject("SELECT count(*) FROM organization WHERE is_platform", Long.class);
            if (legacyAdmins.size() != 1 || platformOrganizations != 0) {
                throw new IllegalStateException("身份边界升级仅支持恰有一个活动双身份管理员且尚无平台组织的首版数据库");
            }
            Boolean usernameExists = jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM user_account WHERE username = ?)",
                    Boolean.class, settings.platformAdminUsername());
            if (Boolean.TRUE.equals(usernameExists)) {
                throw new IllegalStateException("平台管理员用户名已存在，拒绝覆盖已有账号");
            }
            UUID platformOrganizationId = UUID.randomUUID();
            UUID platformAdminId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO organization(id, organization_code, name, is_platform) VALUES (?, 'PLATFORM', '平台管理组织', TRUE)",
                    platformOrganizationId);
            jdbcTemplate.update("""
                    INSERT INTO user_account(id, organization_id, password, username, display_name,
                      is_super_admin, is_platform_admin, is_superuser, is_staff)
                    VALUES (?, ?, ?, ?, ?, FALSE, TRUE, FALSE, FALSE)
                    """, platformAdminId, platformOrganizationId, encodedPlatformPassword,
                    settings.platformAdminUsername(), settings.platformAdminDisplayName());
            jdbcTemplate.update("""
                    UPDATE user_account SET is_platform_admin = FALSE, is_superuser = FALSE, is_staff = FALSE,
                      session_version = session_version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND is_super_admin AND is_platform_admin
                    """, legacyAdmins.getFirst());
        });
    }
}
