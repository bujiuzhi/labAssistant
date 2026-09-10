package com.materialslab.api.projects.controller;

import tools.jackson.databind.JsonNode;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.common.model.ApiResponse;
import com.materialslab.api.common.model.PageResponse;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.identity.service.IdentityService;
import com.materialslab.api.projects.domain.Project;
import com.materialslab.api.projects.domain.ProjectOperationLog;
import com.materialslab.api.projects.domain.ProjectResponse;
import com.materialslab.api.projects.service.ProjectService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目、项目关注及工作台接口，URL 为 /api/v1。 */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectService projectService;
    public ProjectController(ProjectService projectService) { this.projectService = projectService; }

    /** 分页查询当前用户可见项目。 */
    @GetMapping("/projects")
    public PageResponse<ProjectResponse> list(@RequestParam(defaultValue = "1") int page, @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                                    @RequestParam(required = false) String status, @RequestParam(required = false) String search) {
        UserPrincipal principal = IdentityService.currentPrincipal();
        List<Project> results = projectService.list(principal, status, search, page, pageSize);
        return PageResponse.of(projectService.responses(results), page, pageSize, projectService.count(principal, status, search));
    }

    /** 创建项目。 */
    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@RequestBody JsonNode payload) {
        UserPrincipal principal = IdentityService.currentPrincipal();
        Project project = projectService.create(principal, payload);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(String.valueOf(project.version())).body(ApiResponse.of(projectService.response(project)));
    }

    /** 获取项目详情。 */
    @GetMapping("/projects/{projectKey}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable String projectKey) {
        UserPrincipal principal = IdentityService.currentPrincipal(); Project project = projectService.get(principal, projectKey);
        return ResponseEntity.ok().eTag(String.valueOf(project.version())).body(ApiResponse.of(projectService.response(project)));
    }

    /** 更新项目。 */
    @PatchMapping("/projects/{projectKey}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(@PathVariable String projectKey, @RequestHeader(name=HttpHeaders.IF_MATCH, required=false) String ifMatch, @RequestBody JsonNode payload) {
        UserPrincipal principal = IdentityService.currentPrincipal(); Project project = projectService.update(principal, projectKey, version(ifMatch), payload);
        return ResponseEntity.ok().eTag(String.valueOf(project.version())).body(ApiResponse.of(projectService.response(project)));
    }

    /** 归档项目。 */
    @PostMapping("/projects/{projectKey}/archive")
    public ResponseEntity<ApiResponse<ProjectResponse>> archive(@PathVariable String projectKey, @RequestHeader(name=HttpHeaders.IF_MATCH, required=false) String ifMatch) {
        UserPrincipal principal = IdentityService.currentPrincipal(); Project project = projectService.archive(principal, projectKey, version(ifMatch));
        return ResponseEntity.ok().eTag(String.valueOf(project.version())).body(ApiResponse.of(projectService.response(project)));
    }

    /** 设置关注状态。 */
    @PostMapping("/projects/{projectKey}/follow")
    public ApiResponse<?> follow(@PathVariable String projectKey) { UserPrincipal p = IdentityService.currentPrincipal(); return ApiResponse.of(Map.of("is_followed", projectService.setFollow(p, projectKey, true))); }

    /** 取消关注项目。 */
    @DeleteMapping("/projects/{projectKey}/follow")
    public ApiResponse<?> unfollow(@PathVariable String projectKey) { UserPrincipal p = IdentityService.currentPrincipal(); return ApiResponse.of(Map.of("is_followed", projectService.setFollow(p, projectKey, false))); }

    /** 查询项目真实操作日志。 */
    @GetMapping("/projects/{projectKey}/operation-logs")
    public ApiResponse<List<ProjectOperationLog>> operationLogs(@PathVariable String projectKey) {
        UserPrincipal principal = IdentityService.currentPrincipal();
        return ApiResponse.of(projectService.operationLogs(principal, projectKey));
    }

    /** 工作台实时统计。 */
    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard(@RequestParam(name = "project_id", required = false) java.util.UUID projectId) {
        UserPrincipal principal = IdentityService.currentPrincipal();
        return ApiResponse.of(projectService.dashboard(principal, projectId));
    }

    private int version(String header) { try { return Integer.parseInt(header.replace("\"", "")); } catch (Exception error) { throw new BusinessException(HttpStatus.PRECONDITION_REQUIRED, "if_match_required", "If-Match 必须携带资源版本号"); } }
}
