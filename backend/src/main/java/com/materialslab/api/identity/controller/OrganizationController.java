package com.materialslab.api.identity.controller;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.common.model.ApiResponse;
import com.materialslab.api.identity.domain.OrganizationSummary;
import com.materialslab.api.identity.mapper.OrganizationMapper;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.identity.service.IdentityService;
import com.materialslab.api.identity.service.PlatformOrganizationService;
import com.materialslab.api.identity.domain.PlatformOrganization;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 组织数据边界接口，URL 为 /api/v1/organizations。 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationMapper organizationMapper;
    private final AccessControlService accessControlService;
    private final PlatformOrganizationService platformOrganizationService;

    public OrganizationController(OrganizationMapper organizationMapper, AccessControlService accessControlService,
                                  PlatformOrganizationService platformOrganizationService) {
        this.organizationMapper = organizationMapper;
        this.accessControlService = accessControlService;
        this.platformOrganizationService = platformOrganizationService;
    }

    /** 获取当前会话所属组织。 */
    @GetMapping("/current")
    public ApiResponse<OrganizationSummary> current() {
        UserPrincipal principal = IdentityService.currentPrincipal();
        accessControlService.requirePermission(principal, "organization.read");
        OrganizationSummary organization = organizationMapper.findActiveById(principal.organizationId());
        if (organization == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "organization_not_found", "当前组织不存在或已停用");
        }
        return ApiResponse.of(organization);
    }

    /** 查询平台组织目录；不会返回任何租户的项目、实验或文件数据。 */
    @GetMapping
    public ApiResponse<List<PlatformOrganization>> list() {
        return ApiResponse.of(platformOrganizationService.listOrganizations(IdentityService.currentPrincipal()));
    }

    /** 原子创建组织、三项内置角色和该组织首个超级管理员。 */
    @PostMapping
    public ResponseEntity<ApiResponse<PlatformOrganization>> create(
            @RequestBody PlatformOrganizationService.CreateOrganizationRequest request) {
        PlatformOrganization organization = platformOrganizationService.createOrganization(IdentityService.currentPrincipal(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(organization));
    }
}
