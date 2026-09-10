package com.materialslab.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBeanValidation(MethodArgumentNotValidException error,
                                                                    HttpServletRequest request) {
        var fieldError = error.getBindingResult().getFieldError();
        String detail = fieldError == null ? "请求参数内容不合法"
                : fieldError.getField() + " " + (fieldError.getDefaultMessage() == null ? "不合法" : fieldError.getDefaultMessage());
        return response(HttpStatus.BAD_REQUEST, "validation_error", detail, request.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "validation_error", "请求参数格式或内容不合法", request.getRequestURI());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "upload_too_large", "上传文件超过服务端限制", request.getRequestURI());
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
