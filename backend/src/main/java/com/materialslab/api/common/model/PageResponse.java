package com.materialslab.api.common.model;

import java.util.List;
import java.util.UUID;

/** 统一的分页响应体。 */
public record PageResponse<T>(List<T> data, PageMeta meta, String requestId) {
    /** 根据查询结果和分页参数创建响应。 */
    public static <T> PageResponse<T> of(List<T> data, int page, int pageSize, long total) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        long totalPages = Math.max((total + normalizedPageSize - 1) / normalizedPageSize, 1);
        return new PageResponse<>(data, new PageMeta(normalizedPage, normalizedPageSize, total, totalPages), UUID.randomUUID().toString());
    }

    /** 分页元数据。 */
    public record PageMeta(int page, int pageSize, long total, long totalPages) { }
}
