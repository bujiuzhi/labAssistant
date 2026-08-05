package com.materialslab.api.projects.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 项目文档查询结果及分类统计。 */
public record ProjectDocumentListResponse(
        List<ProjectDocumentResponse> data,
        Meta meta,
        String requestId) {
    /** 文档列表统计信息。 */
    public record Meta(long total, long filteredTotal, Map<String, Long> categoryCounts) { }

    /** 创建前端约定的文档列表响应。 */
    public static ProjectDocumentListResponse of(
            List<ProjectDocumentResponse> data,
            long total,
            long filteredTotal,
            Map<String, Long> categoryCounts) {
        return new ProjectDocumentListResponse(data, new Meta(total, filteredTotal, categoryCounts), UUID.randomUUID().toString());
    }
}
