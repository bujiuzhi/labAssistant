package com.materialslab.api.common.exception;

import org.springframework.http.HttpStatus;

/** 表示可安全返回给调用方的业务异常。 */
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /**
     * @param status HTTP 状态码
     * @param code 稳定错误码
     * @param message 错误说明
     */
    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
