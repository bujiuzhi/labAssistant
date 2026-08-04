package com.materialslab.api.common.model;

import java.util.UUID;

/** 统一的成功响应体。 */
public record ApiResponse<T>(T data, String requestId) {
    /** 创建响应对象。 */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, UUID.randomUUID().toString());
    }
}
