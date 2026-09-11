package com.materialslab.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 验证旧首版身份只能按明确形态一次性拆分，不能猜测复杂历史数据。 */
class LegacyIdentityBoundaryMigratorTest {
    private final BootstrapSettings settings = new BootstrapSettings("REAL_LAB", "正式组织", "admin", "组织管理员", "platform_admin", "平台管理员");

    @Test
    void splitsOnlyLegacyDualAdministratorIntoTwoSeparateAccounts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID legacyAdminId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(UUID.class))).thenReturn(List.of(legacyAdminId));
        when(jdbc.queryForObject("SELECT count(*) FROM organization WHERE is_platform", Long.class)).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(false);

        migrator(jdbc).migrate(settings, "encoded-platform-password");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO organization"), any(UUID.class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO user_account"), any(), any(), any(), any(), any());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE user_account SET is_platform_admin = FALSE"), eq(legacyAdminId));
    }

    @Test
    void refusesUnexpectedLegacyStateBeforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(UUID.class))).thenReturn(List.of());
        when(jdbc.queryForObject("SELECT count(*) FROM organization WHERE is_platform", Long.class)).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> migrator(jdbc).migrate(settings, "encoded-platform-password"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private LegacyIdentityBoundaryMigrator migrator(JdbcTemplate jdbc) {
        return new LegacyIdentityBoundaryMigrator(jdbc, new TransactionTemplate(new TestTransactionManager()));
    }

    private static final class TestTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
