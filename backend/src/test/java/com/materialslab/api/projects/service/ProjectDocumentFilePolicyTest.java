package com.materialslab.api.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialslab.api.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 验证项目文档的服务端类型判定和内联预览边界。 */
class ProjectDocumentFilePolicyTest {
    @Test
    void 应按文件头确认PDF而非客户端声明的MIME() {
        assertThat(ProjectDocumentFilePolicy.validateAndResolveMimeType("report.pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");

        assertThatThrownBy(() -> ProjectDocumentFilePolicy.validateAndResolveMimeType("report.pdf",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("invalid_document_content");
    }

    @Test
    void 仅安全白名单格式可内联且历史HTML类型降级为下载() {
        assertThat(ProjectDocumentFilePolicy.isSafeInlinePreview("application/pdf")).isTrue();
        assertThat(ProjectDocumentFilePolicy.isSafeInlinePreview("text/html")).isFalse();
        assertThat(ProjectDocumentFilePolicy.responseMediaType("text/html")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
