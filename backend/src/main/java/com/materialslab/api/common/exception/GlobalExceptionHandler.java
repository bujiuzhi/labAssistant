package com.materialslab.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一转换校验、权限和业务异常为 Problem Details 兼容响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException error, HttpServletRequest request) {
        return response(error.status(), error.code(), error.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "validation_error", error.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "permission_denied", "当前用户无权执行该操作", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception error, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        LOGGER.error("请求处理失败，requestId={}, path={}", requestId, request.getRequestURI(), error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "type", "about:blank", "title", "服务器内部错误", "status", 500,
                "code", "internal_error", "request_id", requestId));
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String detail, String instance) {
        return ResponseEntity.status(status).body(Map.of(
                "type", "about:blank", "title", status.getReasonPhrase(), "status", status.value(),
                "code", code, "detail", detail == null ? "请求参数不合法" : detail,
                "instance", instance, "request_id", UUID.randomUUID().toString()));
    }
}
