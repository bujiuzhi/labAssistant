package com.materialslab.api.projects.controller;

import com.materialslab.api.common.model.ApiResponse;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.identity.service.IdentityService;
import com.materialslab.api.projects.domain.ProjectDocumentListResponse;
import com.materialslab.api.projects.domain.ProjectDocumentResponse;
import com.materialslab.api.projects.service.ProjectDocumentFilePolicy;
import com.materialslab.api.projects.service.ProjectDocumentService;
import com.materialslab.api.projects.service.ProjectDocumentService.DocumentContent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 项目文档查询、上传和正文读取接口，URL 为 /api/v1/projects/{projectKey}/documents。 */
@RestController
@RequestMapping("/api/v1/projects/{projectKey}/documents")
public class ProjectDocumentController {
    private final ProjectDocumentService documentService;

    public ProjectDocumentController(ProjectDocumentService documentService) { this.documentService = documentService; }

    /** 查询项目文档
     * URL：GET /api/v1/projects/{projectKey}/documents
     * @param projectKey 项目编号或主键
     * @return 文档列表和分类统计
     */
    @GetMapping
    public ProjectDocumentListResponse list(@PathVariable String projectKey, @RequestParam(required = false) String category,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(name = "file_type", required = false) String fileType,
                                             @RequestParam(name = "updated_range", required = false) String updatedRange) {
        UserPrincipal principal = IdentityService.currentPrincipal();
        return documentService.list(principal, projectKey, category, search, fileType, updatedRange);
    }

    /** 上传项目文档
     * URL：POST /api/v1/projects/{projectKey}/documents
     * @param projectKey 项目编号或主键
     * @param file 文档文件
     * @param category 文档分类
     * @param versionLabel 文档版本
     * @return 已保存的文档元数据
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectDocumentResponse>> upload(@PathVariable String projectKey,
                                                                          @RequestParam MultipartFile file,
                                                                          @RequestParam String category,
                                                                          @RequestParam(name = "version_label") String versionLabel) {
        UserPrincipal principal = IdentityService.currentPrincipal();
        return ResponseEntity.status(201).body(ApiResponse.of(documentService.upload(principal, projectKey, file, category, versionLabel)));
    }

    /** 读取或下载项目文档正文
     * URL：GET /api/v1/projects/{projectKey}/documents/{documentId}/content
     * @param projectKey 项目编号或主键
     * @param documentId 文档主键
     * @param download 是否作为附件下载
     * @return 真实二进制正文
     */
    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String projectKey, @PathVariable UUID documentId,
                                          @RequestParam(defaultValue = "false") boolean download) {
        return fileResponse(documentService.content(IdentityService.currentPrincipal(), projectKey, documentId), download);
    }

    /** 在线预览项目文档
     * URL：GET /api/v1/projects/{projectKey}/documents/{documentId}/preview
     * @param projectKey 项目编号或主键
     * @param documentId 文档主键
     * @return 当前可直接预览的真实原始正文
     */
    @GetMapping("/{documentId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String projectKey, @PathVariable UUID documentId) {
        return fileResponse(documentService.content(IdentityService.currentPrincipal(), projectKey, documentId), false);
    }

    private ResponseEntity<byte[]> fileResponse(DocumentContent source, boolean download) {
        MediaType mediaType = ProjectDocumentFilePolicy.responseMediaType(source.document().mimeType());
        boolean inline = !download && ProjectDocumentFilePolicy.isSafeInlinePreview(source.document().mimeType());
        ContentDisposition disposition = !inline
                ? ContentDisposition.attachment().filename(source.document().name(), StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(source.document().name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(mediaType).contentLength(source.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; base-uri 'none'; frame-ancestors 'self'")
                .cacheControl(CacheControl.noStore().cachePrivate()).body(source.content());
    }
}
