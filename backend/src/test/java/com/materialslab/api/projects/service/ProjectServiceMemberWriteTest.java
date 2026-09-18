package com.materialslab.api.projects.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import com.materialslab.api.projects.domain.Project;
import com.materialslab.api.projects.mapper.ProjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.ObjectMapper;

/** 验证项目成员选择会受组织边界校验并持久化为数据范围关系。 */
class ProjectServiceMemberWriteTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 创建项目应写入选中的普通成员并保留创建者管理关系() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        ProjectMapper mapper = org.mockito.Mockito.mock(ProjectMapper.class);
        Project created = project(UUID.randomUUID(), organizationId, ownerId);
        when(mapper.isActiveOrganizationUser(organizationId, ownerId)).thenReturn(true);
        when(mapper.isActiveOrganizationUser(organizationId, researcherId)).thenReturn(true);
        when(mapper.findByKey(eq(organizationId), eq(creatorId), eq(false), anyString())).thenReturn(created);
        ProjectService service = new ProjectService(mapper, null, objectMapper, new AccessControlService());

        service.create(principal(creatorId, organizationId, "project.create", "project.read"), objectMapper.readTree("""
                {"name":"成员同步项目","owner_id":"%s","member_ids":["%s","%s"]}
                """.formatted(ownerId, ownerId, researcherId)));

        verify(mapper).addCreatorAsManager(any(UUID.class), eq(organizationId), any(UUID.class), eq(creatorId));
        verify(mapper).deleteResearcherMembers(eq(organizationId), any(UUID.class));
        verify(mapper).addResearcherMember(any(UUID.class), eq(organizationId), any(UUID.class), eq(researcherId), eq(creatorId));
        verify(mapper, never()).addResearcherMember(any(UUID.class), eq(organizationId), any(UUID.class), eq(ownerId), eq(creatorId));
    }

    @Test
    void 编辑项目应同步成员但拒绝其他组织或非启用账号() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invalidMemberId = UUID.randomUUID();
        Project existing = project(UUID.randomUUID(), organizationId, ownerId);
        ProjectMapper mapper = org.mockito.Mockito.mock(ProjectMapper.class);
        when(mapper.findByKey(organizationId, managerId, false, existing.projectNo())).thenReturn(existing);
        when(mapper.hasManageAccess(organizationId, existing.id(), managerId)).thenReturn(true);
        when(mapper.isActiveOrganizationUser(organizationId, ownerId)).thenReturn(true);
        when(mapper.isActiveOrganizationUser(organizationId, invalidMemberId)).thenReturn(false);
        ProjectService service = new ProjectService(mapper, null, objectMapper, new AccessControlService());

        assertThatThrownBy(() -> service.update(principal(managerId, organizationId, "project.update", "project.read"), existing.projectNo(),
                existing.version(), objectMapper.readTree("{\"member_ids\":[\"" + invalidMemberId + "\"]}")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("validation_error"));

        verify(mapper, never()).update(any());
        verify(mapper, never()).deleteResearcherMembers(any(UUID.class), any(UUID.class));
        verify(mapper, never()).addResearcherMember(any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class));
    }

    private UserPrincipal principal(UUID userId, UUID organizationId, String... permissions) {
        return new UserPrincipal(new UserAccount(userId, organizationId, "manager", "", "项目管理员", "active", false, false, 0),
                java.util.Arrays.stream(permissions).map(permission -> new SimpleGrantedAuthority("PERM_" + permission)).toList());
    }

    private Project project(UUID projectId, UUID organizationId, UUID ownerId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-18T09:00:00+08:00");
        return new Project(projectId, organizationId, "PRJ-TEST-001", "成员同步项目", "聚酰亚胺", "", "方案设计", 0,
                "not_started", ownerId, "项目负责人", "[]", "[]", 0, 0, 0, now, now.plusDays(1), null, null, 1, now, now);
    }
}
