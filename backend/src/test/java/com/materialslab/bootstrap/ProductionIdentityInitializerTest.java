package com.materialslab.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.materialslab.api.identity.service.SystemIdentityCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用隔离内存记录验证身份写入边界与事务，不连接任何运行中数据库。 */
class ProductionIdentityInitializerTest {
    private final BootstrapSettings settings = new BootstrapSettings("REAL_LAB", "正式组织", "owner", "正式管理员");

    @Test
    void initializesOnlyIdentityAndCommitsOnce() {
        var jdbc = new RecordingJdbcTemplate();
        var transactionManager = new RecordingTransactionManager();
        initializer(jdbc, transactionManager).initializeIdentity(settings, "encoded-password");
        assertEquals(1, transactionManager.commits);
        assertEquals(0, transactionManager.rollbacks);
        Set<String> allowedTables = Set.of("organization", "user_account", "permission", "role", "role_permission", "user_role");
        for (String sql : jdbc.writes) {
            assertTrue(allowedTables.stream().anyMatch(table -> sql.startsWith("INSERT INTO " + table + "(")));
        }
        assertEquals(1, jdbc.writes.stream().filter(sql -> sql.startsWith("INSERT INTO user_account(")).count());
        assertEquals(3, jdbc.writes.stream().filter(sql -> sql.startsWith("INSERT INTO role(")).count());
        assertEquals(SystemIdentityCatalog.PERMISSIONS.size(), jdbc.writes.stream().filter(sql -> sql.startsWith("INSERT INTO permission(")).count());
        assertTrue(jdbc.writes.stream().filter(sql -> sql.startsWith("INSERT INTO user_account(")).allMatch(sql -> sql.contains("is_platform_admin")));
        assertTrue(jdbc.commands.getFirst().startsWith("LOCK TABLE"));
        assertTrue(jdbc.arguments.stream().flatMap(Arrays::stream).noneMatch(value -> "DEV_TEST".equals(value)));
    }

    @Test
    void rejectsExistingDataWithoutWritingOrResettingAccounts() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.nonEmpty = true;
        var transactionManager = new RecordingTransactionManager();
        assertThrows(IllegalStateException.class, () -> initializer(jdbc, transactionManager).initializeIdentity(settings, "encoded-password"));
        assertTrue(jdbc.writes.isEmpty());
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void refusesUnknownSchemaTablesBeforeAnyWrite() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.tables = List.of("unrelated_table:r");
        assertThrows(IllegalStateException.class, () -> initializer(jdbc, new RecordingTransactionManager()).assertEmptyDatabase(false));
        assertTrue(jdbc.writes.isEmpty());
    }

    @Test
    void refusesNonProjectSchemasEvenWhenEmpty() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.otherSchemas = List.of("another_project");
        assertThrows(IllegalStateException.class, () -> initializer(jdbc, new RecordingTransactionManager()).assertEmptyDatabase(false));
        assertTrue(jdbc.writes.isEmpty());
    }

    @Test
    void refusesViewsMaterializedViewsSequencesForeignAndPartitionedRelations() {
        for (String kind : List.of("v", "m", "S", "f", "p")) {
            var jdbc = new RecordingJdbcTemplate();
            jdbc.tables = List.of("organization:" + kind);
            assertThrows(IllegalStateException.class, () -> initializer(jdbc, new RecordingTransactionManager()).assertEmptyDatabase(false));
            assertTrue(jdbc.writes.isEmpty());
        }
    }

    @Test
    void rollsBackWhenAnIdentityWriteFails() {
        var jdbc = new RecordingJdbcTemplate();
        jdbc.failAfter = 2;
        var transactionManager = new RecordingTransactionManager();
        assertThrows(IllegalStateException.class, () -> initializer(jdbc, transactionManager).initializeIdentity(settings, "encoded-password"));
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    private ProductionIdentityInitializer initializer(RecordingJdbcTemplate jdbc, RecordingTransactionManager transactionManager) {
        return new ProductionIdentityInitializer(jdbc, new TransactionTemplate(transactionManager));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        final List<String> writes = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final List<Object[]> arguments = new ArrayList<>();
        List<String> tables = List.of("organization:r", "user_account:r");
        List<String> otherSchemas = List.of();
        boolean nonEmpty;
        int failAfter = Integer.MAX_VALUE;

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType) {
            return (sql.contains("SELECT nspname") ? otherSchemas : tables).stream().map(elementType::cast).toList();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            if (sql.equals("SELECT current_schema()")) return requiredType.cast("public");
            return requiredType.cast(nonEmpty);
        }

        @Override
        public void execute(String sql) {
            commands.add(sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (writes.size() == failAfter) {
                throw new IllegalStateException("隔离测试模拟数据库写入失败");
            }
            writes.add(sql.strip());
            arguments.add(args);
            return 1;
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
