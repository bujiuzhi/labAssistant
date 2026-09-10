package com.materialslab.api.projects.service;

import com.materialslab.api.common.exception.BusinessException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** 项目文档格式白名单、文件头核验和内联预览边界。 */
public final class ProjectDocumentFilePolicy {
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"), Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"), Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"), Map.entry("rtf", "application/rtf"),
            Map.entry("doc", "application/msword"), Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation"));

    private ProjectDocumentFilePolicy() { }

    /** 验证文件名和正文匹配，并返回服务端决定的 MIME 类型。 */
    public static String validateAndResolveMimeType(String name, byte[] content) {
        String extension = extension(name);
        String mimeType = MIME_TYPES.get(extension);
        if (mimeType == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "unsupported_document_type", "不支持该文档格式上传");
        }
        if (!matchesSignature(extension, content)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_document_content", "文件内容与扩展名不匹配或文件已损坏");
        }
        return mimeType;
    }

    /** 验证实验附件，并限制过程图片只能是已确认的 JPEG 或 PNG。 */
    public static String validateExperimentAttachment(String kind, String name, byte[] content) {
        String mimeType = validateAndResolveMimeType(name, content);
        if ("process_image".equals(kind) && !"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_process_image", "过程图片仅支持 JPG、PNG");
        }
        return mimeType;
    }

    /** 仅允许经服务端白名单确认的 PDF 与位图图片以内联方式返回。 */
    public static boolean isSafeInlinePreview(String mimeType) {
        return MIME_TYPES.containsValue(mimeType)
                && ("application/pdf".equals(mimeType) || mimeType.startsWith("image/"));
    }

    /** 对历史或异常 MIME 使用二进制下载，避免客户端声明的类型参与响应。 */
    public static MediaType responseMediaType(String mimeType) {
        if (!MIME_TYPES.containsValue(mimeType)) return MediaType.APPLICATION_OCTET_STREAM;
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 1 || index == name.length() - 1 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSignature(String extension, byte[] content) {
        if (content == null || content.length == 0) return false;
        return switch (extension) {
            case "pdf" -> startsWith(content, 0x25, 0x50, 0x44, 0x46, 0x2d);
            case "png" -> startsWith(content, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "jpg", "jpeg" -> startsWith(content, 0xff, 0xd8, 0xff);
            case "gif" -> startsWith(content, 0x47, 0x49, 0x46, 0x38);
            case "webp" -> startsWith(content, 0x52, 0x49, 0x46, 0x46) && content.length >= 12
                    && startsWithAt(content, 8, 0x57, 0x45, 0x42, 0x50);
            case "bmp" -> startsWith(content, 0x42, 0x4d);
            case "doc", "xls", "ppt" -> startsWith(content, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
            case "docx", "xlsx", "pptx", "odt", "ods", "odp" -> startsWith(content, 0x50, 0x4b, 0x03, 0x04);
            case "rtf" -> startsWith(content, 0x7b, 0x5c, 0x72, 0x74, 0x66);
            case "txt", "csv" -> isPlainText(content);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, int... expected) {
        return startsWithAt(content, 0, expected);
    }

    private static boolean startsWithAt(byte[] content, int offset, int... expected) {
        if (content.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index += 1) {
            if ((content[offset + index] & 0xff) != expected[index]) return false;
        }
        return true;
    }

    private static boolean isPlainText(byte[] content) {
        for (byte value : content) {
            int unsigned = value & 0xff;
            if (unsigned == 0 || (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t')) return false;
        }
        return true;
    }
}
