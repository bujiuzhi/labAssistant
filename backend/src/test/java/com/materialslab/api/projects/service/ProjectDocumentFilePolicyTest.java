package com.materialslab.api.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialslab.api.common.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    @Test
    void 应拒绝伪装成Office文档的普通Zip与非法UTF8文本() throws Exception {
        byte[] archive;
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("unrelated.txt"));
            zip.write("not an office document".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            archive = output.toByteArray();
        }
        assertThatThrownBy(() -> ProjectDocumentFilePolicy.validateAndResolveMimeType("report.docx", archive))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("invalid_document_content");
        assertThatThrownBy(() -> ProjectDocumentFilePolicy.validateAndResolveMimeType("report.txt", new byte[] {(byte) 0xff}))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("invalid_document_content");
    }

    @Test
    void 应限制Office归档条目数量与ODF的MIME正文长度() throws Exception {
        byte[] manyEntries;
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            for (int index = 0; index < 1_000; index += 1) {
                zip.putNextEntry(new ZipEntry("metadata/" + index + ".xml"));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.closeEntry();
            zip.finish();
            manyEntries = output.toByteArray();
        }
        assertThatThrownBy(() -> ProjectDocumentFilePolicy.validateAndResolveMimeType("report.docx", manyEntries))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("invalid_document_content");

        byte[] oversizedMime;
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("x".repeat(129).getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            zip.finish();
            oversizedMime = output.toByteArray();
        }
        assertThatThrownBy(() -> ProjectDocumentFilePolicy.validateAndResolveMimeType("report.odt", oversizedMime))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code()).isEqualTo("invalid_document_content");
    }

    @Test
    void 应接受结构正确的Docx和Odt() throws Exception {
        byte[] docx;
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            docx = output.toByteArray();
        }
        assertThat(ProjectDocumentFilePolicy.validateAndResolveMimeType("report.docx", docx))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        byte[] odt;
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write("<document-content/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            odt = output.toByteArray();
        }
        assertThat(ProjectDocumentFilePolicy.validateAndResolveMimeType("report.odt", odt))
                .isEqualTo("application/vnd.oasis.opendocument.text");
    }
}
