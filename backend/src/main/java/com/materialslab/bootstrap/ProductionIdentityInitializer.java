package com.materialslab.bootstrap;

import com.materialslab.api.common.config.SchemaMigrationInitializer;
import com.materialslab.api.identity.service.SystemIdentityCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 首次生产建账：已有业务数据一律拒绝，所有身份写入在同一事务内完成。 */
final class ProductionIdentityInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionIdentityInitializer.class);
    private static final Set<String> APPLICATION_TABLES = Set.of("organization", "user_account", "role", "permission",
            "user_role", "role_permission", "business_number_sequence", "project", "project_member", "project_follow",
            "registration_invitation",
            "project_document", "project_document_content", "experiment", "experiment_record", "experiment_participant",
            "experiment_attachment", "experiment_attachment_content", "idempotency_request", "business_operation_log");
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    ProductionIdentityInitializer(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /** 检查空库、复用正式 Flyway 迁移，再原子创建平台控制面账号及首个租户管理员；迁移失败或非空库均终止。 */
    void initialize(DataSource dataSource, ApplicationArguments args, BootstrapSettings settings, String encodedPlatformPassword, String encodedOrganizationPassword) {
        assertEmptyDatabase(false);
        new SchemaMigrationInitializer(dataSource).run(args);
        initializeIdentity(settings, encodedPlatformPassword, encodedOrganizationPassword);
        LOGGER.info("生产首次身份初始化完成：已创建 1 个内部平台组织、1 个业务组织、平台管理员及组织超级管理员，未写入项目或实验样例");
    }

    void initializeIdentity(BootstrapSettings settings, String encodedPlatformPassword, String encodedOrganizationPassword) {
        transactionTemplate.executeWithoutResult(status -> {
            // 锁定全部业务表后重新检查，阻止并发初始化或 API 写入绕过空库检查。
            assertEmptyDatabase(true);
            createIdentity(settings, encodedPlatformPassword, encodedOrganizationPassword);
        });
    }

    void assertEmptyDatabase(boolean lockTables) {
        List<String> otherSchemas = jdbcTemplate.queryForList("""
                SELECT nspname FROM pg_namespace
                WHERE left(nspname, 3) <> 'pg_' AND nspname NOT IN ('information_schema', 'public')
                """, String.class);
        if (!otherSchemas.isEmpty() || !"public".equals(jdbcTemplate.queryForObject("SELECT current_schema()", String.class))) {
            throw new IllegalStateException("目标数据库含非项目 schema 或默认 schema 不是 public，拒绝生产初始化");
        }
        List<String> relations = jdbcTemplate.queryForList("""
                SELECT c.relname::text || ':' || c.relkind::text FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f') ORDER BY c.relname
                """, String.class);
        for (String relation : relations) {
            String table = relation.substring(0, relation.lastIndexOf(':'));
            if (!relation.endsWith(":r") || (!APPLICATION_TABLES.contains(table) && !table.equals("flyway_schema_history"))) {
                throw new IllegalStateException("目标数据库含非项目表、视图、序列或外部关系，拒绝生产初始化");
            }
        }
        List<String> tables = relations.stream().map(relation -> relation.substring(0, relation.lastIndexOf(':')))
                .filter(table -> !table.equals("flyway_schema_history")).toList();
        if (lockTables && !tables.isEmpty()) {
            jdbcTemplate.execute("LOCK TABLE " + String.join(", ", tables.stream().map(table -> "\"public\".\"" + table + "\"").toList())
                    + " IN ACCESS EXCLUSIVE MODE");
        }
        for (String table : tables) {
            if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM \"public\".\"" + table + "\")", Boolean.class))) {
                throw new IllegalStateException("目标数据库已有数据；拒绝重复初始化，不修改或重置任何账号");
            }
        }
    }

    private void createIdentity(BootstrapSettings settings, String encodedPlatformPassword, String encodedOrganizationPassword) {
        UUID platformOrganizationId = UUID.randomUUID();
        UUID platformAdminId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID organizationAdminId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organization(id, organization_code, name, is_platform) VALUES (?, 'PLATFORM', '平台管理组织', TRUE)",
                platformOrganizationId);
        jdbcTemplate.update("INSERT INTO organization(id, organization_code, name) VALUES (?, ?, ?)",
                organizationId, settings.organizationCode(), settings.organizationName());
        jdbcTemplate.update("""
                INSERT INTO user_account(id, organization_id, password, username, display_name,
                  is_super_admin, is_platform_admin, is_superuser, is_staff) VALUES (?, ?, ?, ?, ?, FALSE, TRUE, FALSE, FALSE)
                """, platformAdminId, platformOrganizationId, encodedPlatformPassword, settings.platformAdminUsername(), settings.platformAdminDisplayName());
        jdbcTemplate.update("""
                INSERT INTO user_account(id, organization_id, password, username, display_name,
                  is_super_admin, is_platform_admin, is_superuser, is_staff) VALUES (?, ?, ?, ?, ?, TRUE, FALSE, FALSE, FALSE)
                """, organizationAdminId, organizationId, encodedOrganizationPassword, settings.organizationAdminUsername(), settings.organizationAdminDisplayName());
        Map<String, UUID> permissionIds = new HashMap<>();
        for (var permission : SystemIdentityCatalog.PERMISSIONS) {
            UUID id = UUID.randomUUID();
            permissionIds.put(permission.code(), id);
            jdbcTemplate.update("""
                    INSERT INTO permission(id, permission_code, name, module_code, description) VALUES (?, ?, ?, ?, ?)
                    """, id, permission.code(), permission.name(), permission.moduleCode(), "系统 RBAC 权限");
        }
        for (var role : SystemIdentityCatalog.ROLES) {
            UUID roleId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO role(id, organization_id, role_code, name, description, is_system) VALUES (?, ?, ?, ?, ?, TRUE)
                    """, roleId, organizationId, role.code(), role.name(), "系统角色");
            for (String permissionCode : role.permissions()) {
                jdbcTemplate.update("INSERT INTO role_permission(id, role_id, permission_id) VALUES (?, ?, ?)",
                        UUID.randomUUID(), roleId, permissionIds.get(permissionCode));
            }
            if (role.code().equals("super_admin")) {
                jdbcTemplate.update("""
                        INSERT INTO user_role(id, organization_id, user_id, role_id, created_by_id) VALUES (?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), organizationId, organizationAdminId, roleId, organizationAdminId);
            }
        }
    }
}
