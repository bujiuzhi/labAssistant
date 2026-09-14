package com.materialslab.api.projects.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证文档列表查询可完整映射对象存储引用。 */
class ProjectDocumentSqlProviderTest {
    @Test
    void 文档列表查询应包含对象存储文件字段() {
        String sql = new ProjectDocumentSqlProvider().findByProject(Map.of("projectId", "project"));

        assertThat(sql).contains("d.version_label, d.file, d.mime_type");
    }
}
