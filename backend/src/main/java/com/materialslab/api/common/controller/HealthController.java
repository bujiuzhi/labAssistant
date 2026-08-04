package com.materialslab.api.common.controller;

import com.materialslab.api.common.model.ApiResponse;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 服务存活和依赖就绪检查接口。 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final DataSource dataSource;
    public HealthController(DataSource dataSource) { this.dataSource = dataSource; }

    /** 检查 API 进程是否存活。 */
    @GetMapping("/live")
    public ApiResponse<?> live() { return ApiResponse.of(Map.of("status", "ok")); }

    /** 检查数据库连接是否可用。 */
    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<?>> ready() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) throw new IllegalStateException("数据库连接不可用");
            return ResponseEntity.ok(ApiResponse.of(Map.of("status", "ok", "database", "ready")));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.of(Map.of("status", "error", "database", "unavailable")));
        }
    }
}
