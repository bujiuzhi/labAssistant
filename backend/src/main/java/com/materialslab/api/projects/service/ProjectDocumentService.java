package com.materialslab.api.projects.service;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.projects.domain.Project;
import com.materialslab.api.projects.domain.ProjectDocument;
import com.materialslab.api.projects.domain.ProjectDocumentListResponse;
import com.materialslab.api.projects.domain.ProjectDocumentResponse;
import com.materialslab.api.projects.mapper.ProjectDocumentMapper;
import com.materialslab.api.projects.mapper.ProjectDocumentMapper.DocumentWriteCommand;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 提供项目文档的真实数据库查询、上传和内容读取能力。 */
@Service
public class ProjectDocumentService {
    private static final List<String> CATEGORIES = List.of("project_plan", "literature", "experiment_plan", "stage_report", "meeting_minutes", "other");
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "project_plan", "项目方案", "literature", "文献资料", "experiment_plan", "实验方案",
            "stage_report", "阶段报告", "meeting_minutes", "会议纪要", "other", "其他");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final ProjectDocumentMapper documentMapper;
    private final ProjectService projectService;
    private final AccessControlService accessControlService;

    public ProjectDocumentService(ProjectDocumentMapper documentMapper, ProjectService projectService,
                                  AccessControlService accessControlService) {
        this.documentMapper = documentMapper;
        this.projectService = projectService;
        this.accessControlService = accessControlService;
    }

    /** 查询当前用户有权查看的项目文档。 */
    public ProjectDocumentListResponse list(UserPrincipal principal, String projectKey, String category, String search,
                                            String fileType, String updatedRange) {
        accessControlService.requirePermission(principal, "document.view");
        Project project = projectService.get(principal, projectKey);
        String normalizedCategory = optionalCategory(category);
        String normalizedFileType = optional(fileType, List.of("word", "pdf", "excel", "powerpoint", "image", "text"), "file_type");
        String normalizedUpdatedRange = optional(updatedRange, List.of("week", "month"), "updated_range");
        String normalizedSearch = search == null ? "" : search.trim();
        List<ProjectDocumentResponse> data = documentMapper.findByProject(project.id(), normalizedCategory, normalizedSearch,
                        normalizedFileType, normalizedUpdatedRange)
                .stream().map(this::response).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        CATEGORIES.forEach(item -> counts.put(item, 0L));
        documentMapper.categoryCounts(project.id()).forEach(item -> counts.put(item.category(), item.total()));
        return ProjectDocumentListResponse.of(data, documentMapper.countByProject(project.id()),
                documentMapper.countFilteredByProject(project.id(), normalizedCategory, normalizedSearch, normalizedFileType, normalizedUpdatedRange), counts);
    }

    /** 上传文档及其真实二进制正文到开发测试数据库。 */
    @Transactional
    public ProjectDocumentResponse upload(UserPrincipal principal, String projectKey, MultipartFile file, String category,
                                          String versionLabel) {
        accessControlService.requirePermission(principal, "document.upload");
        Project project = projectService.get(principal, projectKey);
        projectService.requireManageAccess(principal, project);
        if (file == null || file.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "请选择非空文档");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "单个文档不能超过 20 MB");
        String name = safeFileName(file.getOriginalFilename());
        String normalizedVersion = required(versionLabel, "version_label");
        String normalizedCategory = requiredCategory(category);
        UUID documentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException error) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "document_read_failed", "读取上传文档失败");
        }
        String mimeType = optionalMimeType(file.getContentType(), name);
        documentMapper.insert(new DocumentWriteCommand(documentId, principal.organizationId(), project.id(), normalizedCategory,
                name, normalizedVersion, "database://project-documents/" + documentId, mimeType, content.length,
                principal.userId(), now, now));
        documentMapper.insertContent(documentId, content);
        documentMapper.refreshProjectDocumentCount(project.id());
        documentMapper.insertUploadOperationLog(UUID.randomUUID(), principal.organizationId(), principal.userId(), project.id(),
                project.projectNo(), "上传项目文档：" + name,
                "{\"document_id\":\"" + documentId + "\",\"category\":\"" + normalizedCategory + "\",\"test_data\":false}");
        return response(documentMapper.findById(project.id(), documentId));
    }

    /** 读取项目文档及其真实数据库正文。 */
    public DocumentContent content(UserPrincipal principal, String projectKey, UUID documentId) {
        accessControlService.requirePermission(principal, "document.view");
        Project project = projectService.get(principal, projectKey);
        ProjectDocument document = documentMapper.findById(project.id(), documentId);
        if (document == null) throw new BusinessException(HttpStatus.NOT_FOUND, "document_not_found", "项目文档不存在或无权访问");
        byte[] content = documentMapper.findContent(documentId);
        if (content == null) throw new BusinessException(HttpStatus.NOT_FOUND, "document_content_not_found", "项目文档正文不存在");
        return new DocumentContent(document, content);
    }

    private ProjectDocumentResponse response(ProjectDocument document) {
        return new ProjectDocumentResponse(document.id(), document.name(), extension(document.name()), document.mimeType(),
                document.fileSize(), document.category(), CATEGORY_LABELS.getOrDefault(document.category(), "其他"),
                document.versionLabel(), document.uploadedById(), document.uploadedByName(), document.createdAt(), document.updatedAt());
    }

    private String optionalCategory(String category) { return optional(category, CATEGORIES, "category"); }
    private String requiredCategory(String category) {
        String value = optionalCategory(category);
        if (value.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "category 不能为空");
        return value;
    }
    private String optional(String value, List<String> allowed, String field) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        if (!allowed.contains(normalized)) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", field + " 参数无效");
        return normalized;
    }
    private String required(String value, String field) {
        if (value == null || value.trim().isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", field + " 不能为空");
        return value.trim();
    }
    private String safeFileName(String source) {
        String name = source == null ? "" : source.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255) throw new BusinessException(HttpStatus.BAD_REQUEST, "validation_error", "文件名无效");
        return name;
    }
    private String optionalMimeType(String source, String name) {
        if (source != null && !source.isBlank()) return source;
        return "txt".equals(extension(name)) ? "text/plain; charset=utf-8" : "application/octet-stream";
    }
    private String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 1 || index == name.length() - 1 ? "" : name.substring(index + 1).toLowerCase();
    }

    /** 项目文档及其真实二进制内容。 */
    public record DocumentContent(ProjectDocument document, byte[] content) { }
}
