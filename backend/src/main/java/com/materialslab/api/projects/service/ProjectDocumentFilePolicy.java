package com.materialslab.api.projects.service;

import com.materialslab.api.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** 项目文档格式白名单、文件头核验和内联预览边界。 */
public final class ProjectDocumentFilePolicy {
    private static final int MAX_ZIP_ENTRIES = 1_000;
    private static final int MAX_SKIPPED_ZIP_ENTRY_BYTES = 512 * 1024;
    private static final int MAX_ODF_MIME_BYTES = 128;
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
            case "doc" -> matchesOle(content, "WordDocument");
            case "xls" -> matchesOle(content, "Workbook");
            case "ppt" -> matchesOle(content, "PowerPoint Document");
            case "docx" -> matchesOoxml(content, "word/document.xml");
            case "xlsx" -> matchesOoxml(content, "xl/workbook.xml");
            case "pptx" -> matchesOoxml(content, "ppt/presentation.xml");
            case "odt" -> matchesOdf(content, "application/vnd.oasis.opendocument.text");
            case "ods" -> matchesOdf(content, "application/vnd.oasis.opendocument.spreadsheet");
            case "odp" -> matchesOdf(content, "application/vnd.oasis.opendocument.presentation");
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

    private static boolean matchesOle(byte[] content, String streamName) {
        return startsWith(content, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1)
                && containsUtf16Le(content, streamName);
    }

    private static boolean containsUtf16Le(byte[] content, String expected) {
        byte[] pattern = expected.getBytes(StandardCharsets.UTF_16LE);
        int[] prefix = new int[pattern.length];
        for (int index = 1, matched = 0; index < pattern.length; index += 1) {
            while (matched > 0 && pattern[index] != pattern[matched]) matched = prefix[matched - 1];
            if (pattern[index] == pattern[matched]) matched += 1;
            prefix[index] = matched;
        }
        int matched = 0;
        for (byte value : content) {
            while (matched > 0 && value != pattern[matched]) matched = prefix[matched - 1];
            if (value == pattern[matched]) matched += 1;
            if (matched == pattern.length) return true;
        }
        return false;
    }

    private static boolean matchesOoxml(byte[] content, String requiredEntry) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(content))) {
            boolean contentTypes = false;
            boolean expected = false;
            for (int entryCount = 0; entryCount < MAX_ZIP_ENTRIES; entryCount += 1) {
                ZipEntry entry = input.getNextEntry();
                if (entry == null) return false;
                contentTypes |= "[Content_Types].xml".equals(entry.getName());
                expected |= requiredEntry.equals(entry.getName());
                if (contentTypes && expected) return true;
                if (!consumeEntryWithinLimit(input, MAX_SKIPPED_ZIP_ENTRY_BYTES)) return false;
            }
            return false;
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean matchesOdf(byte[] content, String expectedMimeType) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry = input.getNextEntry();
            if (entry == null || !"mimetype".equals(entry.getName())) return false;
            byte[] mimeBytes = input.readNBytes(MAX_ODF_MIME_BYTES + 1);
            return mimeBytes.length <= MAX_ODF_MIME_BYTES
                    && expectedMimeType.equals(new String(mimeBytes, StandardCharsets.US_ASCII));
        } catch (IOException error) {
            return false;
        }
    }

    /** 只跳过小型元数据 entry；遇到大 entry 直接拒绝，避免为寻找文件名解压不受信任正文。 */
    private static boolean consumeEntryWithinLimit(ZipInputStream input, int limit) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int read; (read = input.read(buffer)) != -1;) {
            total += read;
            if (total > limit) return false;
        }
        return true;
    }

    private static boolean isPlainText(byte[] content) {
        // API 返回 charset=utf-8，因此 TXT/CSV 仅接受严格 UTF-8，避免下载时被错误解释。
        final String decoded;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException error) {
            return false;
        }
        for (int index = 0; index < decoded.length(); index += 1) {
            char value = decoded.charAt(index);
            if (value == 0 || (value < 0x20 && value != '\n' && value != '\r' && value != '\t')) return false;
        }
        return true;
    }
}
