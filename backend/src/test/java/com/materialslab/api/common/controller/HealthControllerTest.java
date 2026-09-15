package com.materialslab.api.common.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.materialslab.api.common.storage.ObjectStorageService;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 使用真实 Spring 就绪状态与隔离 JDBC 测试替身验证启动、就绪及故障门禁。 */
class HealthControllerTest {
    @Test
    void refusesTrafficBeforeRunnersCompleteWithoutOpeningDatabaseConnection() {
        var availability = new ApplicationAvailabilityBean();
        var database = new DatabaseFixture(true, false);
        var response = new HealthController(database.dataSource(), availability, readyStorage()).ready();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of("status", "error", "application", "not_ready"), response.getBody().data());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    void preservesSuccessfulResponseOnlyAfterApplicationAndDatabaseAreReady() {
        var database = new DatabaseFixture(true, false);
        var controller = new HealthController(database.dataSource(), readyAvailability(), readyStorage());
        var response = controller.ready();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("status", "ok", "database", "ready", "objectStorage", "ready"), response.getBody().data());
        assertEquals(1, database.connectionRequests);
        assertTrue(database.connectionClosed);
        assertEquals(HttpStatus.OK, controller.ready().getStatusCode());
        assertEquals(1, database.connectionRequests);
    }

    @Test
    void reportsUnavailableWhenDatabaseConnectionThrows() {
        var database = new DatabaseFixture(true, true);
        var response = new HealthController(database.dataSource(), readyAvailability(), readyStorage()).ready();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of("status", "error", "database", "unavailable"), response.getBody().data());
    }

    @Test
    void reportsUnavailableWhenConnectionIsInvalid() {
        var database = new DatabaseFixture(false, false);
        var response = new HealthController(database.dataSource(), readyAvailability(), readyStorage()).ready();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(database.connectionClosed);
    }

    @Test
    void refusesTrafficAgainWhenApplicationStopsAcceptingTraffic() {
        var availability = readyAvailability();
        availability.onApplicationEvent(new AvailabilityChangeEvent<>(this, ReadinessState.REFUSING_TRAFFIC));
        var database = new DatabaseFixture(true, false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                new HealthController(database.dataSource(), availability, readyStorage()).ready().getStatusCode());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    void failsClosedDuringFreshDatabaseProbeInsteadOfReturningStaleSuccess() throws Exception {
        var database = new BlockingFailureDatabaseFixture();
        var controller = new HealthController(database.dataSource(), readyAvailability(), readyStorage());
        assertEquals(HttpStatus.OK, controller.ready().getStatusCode());
        ReflectionTestUtils.setField(controller, "nextDependencyCheckNanos", 0L);
        database.failAndBlockNextProbe = true;

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(controller::ready);
            assertTrue(database.probeStarted.await(1, TimeUnit.SECONDS));
            var concurrent = executor.submit(controller::ready);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, concurrent.get(1, TimeUnit.SECONDS).getStatusCode());

            database.releaseProbe.countDown();
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, first.get(1, TimeUnit.SECONDS).getStatusCode());
            assertEquals(2, database.connectionRequests.get());
        } finally {
            database.releaseProbe.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void refusesTrafficWhenEnabledObjectStorageIsUnavailable() {
        var storage = mock(ObjectStorageService.class);
        when(storage.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("隔离测试对象存储不可用")).when(storage).verifyReady();
        var response = new HealthController(new DatabaseFixture(true, false).dataSource(), readyAvailability(), storage).ready();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of("status", "error", "objectStorage", "unavailable"), response.getBody().data());
    }

    private ApplicationAvailabilityBean readyAvailability() {
        var availability = new ApplicationAvailabilityBean();
        availability.onApplicationEvent(new AvailabilityChangeEvent<>(this, ReadinessState.ACCEPTING_TRAFFIC));
        return availability;
    }

    private ObjectStorageService readyStorage() {
        var storage = mock(ObjectStorageService.class);
        when(storage.isEnabled()).thenReturn(true);
        return storage;
    }

    /** 不连接任何真实数据库，同时记录连接释放和探测次数。 */
    private static final class DatabaseFixture {
        final boolean valid;
        final boolean failConnection;
        int connectionRequests;
        boolean connectionClosed;

        DatabaseFixture(boolean valid, boolean failConnection) {
            this.valid = valid;
            this.failConnection = failConnection;
        }

        DataSource dataSource() {
            return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                    (proxy, method, args) -> {
                        if (!method.getName().equals("getConnection")) throw new AssertionError("未预期的数据源方法");
                        connectionRequests++;
                        if (failConnection) throw new SQLException("隔离测试模拟连接不可用");
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                                (connection, operation, parameters) -> switch (operation.getName()) {
                                    case "isValid" -> valid;
                                    case "close" -> { connectionClosed = true; yield null; }
                                    default -> throw new AssertionError("未预期的连接方法");
                                });
                    });
        }
    }

    /** 阻塞一次失败探测，用于确认并发请求不会复用过期的成功结果。 */
    private static final class BlockingFailureDatabaseFixture {
        final AtomicInteger connectionRequests = new AtomicInteger();
        final CountDownLatch probeStarted = new CountDownLatch(1);
        final CountDownLatch releaseProbe = new CountDownLatch(1);
        volatile boolean failAndBlockNextProbe;

        DataSource dataSource() {
            return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                    (proxy, method, args) -> {
                        if (!method.getName().equals("getConnection")) throw new AssertionError("未预期的数据源方法");
                        connectionRequests.incrementAndGet();
                        if (failAndBlockNextProbe) {
                            probeStarted.countDown();
                            try {
                                if (!releaseProbe.await(2, TimeUnit.SECONDS)) throw new SQLException("隔离测试等待探测释放超时");
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new SQLException("隔离测试探测被中断", exception);
                            }
                        }
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                                (connection, operation, parameters) -> switch (operation.getName()) {
                                    case "isValid" -> !failAndBlockNextProbe;
                                    case "close" -> null;
                                    default -> throw new AssertionError("未预期的连接方法");
                                });
                    });
        }
    }
}
