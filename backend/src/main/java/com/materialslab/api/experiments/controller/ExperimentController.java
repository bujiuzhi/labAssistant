package com.materialslab.api.experiments.controller;

import tools.jackson.databind.JsonNode;
import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.common.model.ApiResponse;
import com.materialslab.api.common.model.PageResponse;
import com.materialslab.api.experiments.domain.Experiment;
import com.materialslab.api.experiments.domain.ExperimentResponse;
import com.materialslab.api.experiments.service.ExperimentService;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.identity.service.IdentityService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 实验计划和状态迁移接口，URL 为 /api/v1/experiments。 */
@RestController
@RequestMapping("/api/v1/experiments")
public class ExperimentController {
    private final ExperimentService experimentService;
    public ExperimentController(ExperimentService experimentService) { this.experimentService = experimentService; }
    /** 分页查询可见实验。 */
    @GetMapping public PageResponse<ExperimentResponse> list(@RequestParam(defaultValue="1") int page, @RequestParam(name="page_size", defaultValue="20") int pageSize, @RequestParam(name="project_id", required=false) UUID projectId, @RequestParam(required=false) String status, @RequestParam(required=false) String search) {
        UserPrincipal p=IdentityService.currentPrincipal();
        List<Experiment> results=experimentService.list(p.organizationId(),projectId,status,search,page,pageSize);
        return PageResponse.of(experimentService.responses(results), page, pageSize, experimentService.count(p.organizationId(), projectId, status, search));
    }
    /** 创建实验及默认 ELN。 */
    @PostMapping public ResponseEntity<ApiResponse<ExperimentResponse>> create(@RequestBody JsonNode payload) { UserPrincipal p=IdentityService.currentPrincipal(); Experiment result=experimentService.create(p.organizationId(),p.userId(),payload); return ResponseEntity.status(HttpStatus.CREATED).eTag(String.valueOf(result.version())).body(ApiResponse.of(experimentService.response(result))); }
    /** 获取实验详情。 */
    @GetMapping("/{experimentKey}") public ResponseEntity<ApiResponse<ExperimentResponse>> get(@PathVariable String experimentKey) { UserPrincipal p=IdentityService.currentPrincipal(); Experiment result=experimentService.get(p.organizationId(),experimentKey); return ResponseEntity.ok().eTag(String.valueOf(result.version())).body(ApiResponse.of(experimentService.response(result))); }
    /** 迁移实验状态。 */
    @PostMapping("/{experimentKey}/transition") public ResponseEntity<ApiResponse<ExperimentResponse>> transition(@PathVariable String experimentKey,@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,@RequestBody JsonNode payload) { UserPrincipal p=IdentityService.currentPrincipal(); Experiment result=experimentService.transition(p.organizationId(),p.userId(),experimentKey,version(ifMatch),payload.path("target_status").asText()); return ResponseEntity.ok().eTag(String.valueOf(result.version())).body(ApiResponse.of(experimentService.response(result))); }
    private int version(String header) { try{return Integer.parseInt(header.replace("\"", ""));}catch(Exception e){throw new BusinessException(HttpStatus.PRECONDITION_REQUIRED,"if_match_required","If-Match 必须携带资源版本号");} }
}
