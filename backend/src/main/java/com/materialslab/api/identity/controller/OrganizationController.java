package com.materialslab.api.identity.controller;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.common.model.ApiResponse;
import com.materialslab.api.identity.domain.OrganizationSummary;
import com.materialslab.api.identity.mapper.OrganizationMapper;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.identity.service.IdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 组织数据边界接口，URL 为 /api/v1/organizations。 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationMapper organizationMapper;
    private final AccessControlService accessControlService;

    public OrganizationController(OrganizationMapper organizationMapper, AccessControlService accessControlService) {
        this.organizationMapper = organizationMapper;
        this.accessControlService = accessControlService;
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
}
