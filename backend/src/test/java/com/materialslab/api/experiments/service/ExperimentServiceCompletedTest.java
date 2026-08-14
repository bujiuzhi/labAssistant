package com.materialslab.api.experiments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.materialslab.api.experiments.domain.Experiment;
import com.materialslab.api.experiments.mapper.ExperimentMapper;
import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 验证已完成实验在接口层保持只读。 */
class ExperimentServiceCompletedTest {
    @Test
    void 已完成实验即使管理员也不可编辑() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        ExperimentMapper mapper = (ExperimentMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {ExperimentMapper.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "listParticipants", "listAttachments" -> List.of();
                    case "findRecord" -> null;
                    default -> throw new AssertionError("未预期的 Mapper 调用：" + method.getName());
                });
        ExperimentService service = new ExperimentService(mapper, new ObjectMapper(), new AccessControlService());
        UserPrincipal principal = new UserPrincipal(
                new UserAccount(userId, organizationId, "admin", "", "测试管理员", "active", true), List.of());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Experiment experiment = new Experiment(
                experimentId, organizationId, UUID.randomUUID(), "PRJ-TEST-001", "测试项目", "EXP-TEST-001",
                "已完成实验", "research", "结果分析", "completed", "验证只读状态", userId,
                "测试管理员", 1, now.minusDays(2), now, now.minusDays(2), now, now.minusDays(3), now);

        assertThat(service.response(principal, experiment).canEdit()).isFalse();
    }
}
