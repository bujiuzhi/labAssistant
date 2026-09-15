package com.materialslab.api.common.controller;

import com.materialslab.api.common.model.ApiResponse;
import com.materialslab.api.common.storage.ObjectStorageService;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 服务存活和依赖就绪检查接口。 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private static final long DATABASE_CHECK_CACHE_NANOS = Duration.ofSeconds(5).toNanos();
    private final DataSource dataSource;
    private final ApplicationAvailability applicationAvailability;
    private final ObjectStorageService objectStorageService;
    private final ReentrantLock readinessLock = new ReentrantLock();
    private volatile long nextDependencyCheckNanos;
    private volatile boolean lastDatabaseReady;
    private volatile boolean lastObjectStorageReady;
    private volatile boolean hasDependencyCheck;

    public HealthController(DataSource dataSource, ApplicationAvailability applicationAvailability,
            ObjectStorageService objectStorageService) {
        this.dataSource = dataSource;
        this.applicationAvailability = applicationAvailability;
        this.objectStorageService = objectStorageService;
    }

    /** 检查 API 进程是否存活。 */
    @GetMapping("/live")
    public ApiResponse<?> live() { return ApiResponse.of(Map.of("status", "ok")); }

    /** 全部启动任务完成且 PostgreSQL、已启用 RustFS 均有效时才接受流量，避免迁移或对象存储未就绪时提前放行。 */
    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<?>> ready() {
        if (applicationAvailability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.of(Map.of("status", "error", "application", "not_ready")));
        }
        if (dependenciesReady()) {
            return ResponseEntity.ok(ApiResponse.of(Map.of("status", "ok", "database", "ready",
                    "objectStorage", "ready")));
        }
        if (!lastDatabaseReady) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.of(Map.of("status", "error", "database", "unavailable")));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.of(Map.of("status", "error", "objectStorage", "unavailable")));
    }

    /** 缓存短周期依赖探测结果，避免公开探针高频调用时耗尽连接池或反复访问对象存储。 */
    private boolean dependenciesReady() {
        long now = System.nanoTime();
        if (hasDependencyCheck && now - nextDependencyCheckNanos < 0) return lastDatabaseReady && lastObjectStorageReady;
        // 探测进行中按未就绪处理，既不复用旧成功状态，也不让公开探针请求堆积等待。
        if (!readinessLock.tryLock()) return false;
        try {
            now = System.nanoTime();
            if (hasDependencyCheck && now - nextDependencyCheckNanos < 0) return lastDatabaseReady && lastObjectStorageReady;
            try (Connection connection = dataSource.getConnection()) {
                lastDatabaseReady = connection.isValid(2);
            } catch (Exception error) {
                lastDatabaseReady = false;
            }
            if (!objectStorageService.isEnabled()) {
                lastObjectStorageReady = true;
            } else {
                try {
                    objectStorageService.verifyReady();
                    lastObjectStorageReady = true;
                } catch (RuntimeException error) {
                    lastObjectStorageReady = false;
                }
            }
            nextDependencyCheckNanos = System.nanoTime() + DATABASE_CHECK_CACHE_NANOS;
            hasDependencyCheck = true;
            return lastDatabaseReady && lastObjectStorageReady;
        } finally {
            readinessLock.unlock();
        }
    }
}
