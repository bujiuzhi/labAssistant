package com.materialslab.api.experiments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.materialslab.api.common.exception.BusinessException;
import com.materialslab.api.common.storage.ObjectStorageService;
import com.materialslab.api.experiments.domain.Experiment;
import com.materialslab.api.experiments.domain.ExperimentAttachment;
import com.materialslab.api.experiments.mapper.ExperimentMapper;
import com.materialslab.api.identity.domain.UserAccount;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.identity.security.UserPrincipal;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/** 覆盖实验 PATCH、组织边界、参与人和附件写入规则。 */
class ExperimentServiceWriteTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExperimentMapper mapper;
    private ExperimentService service;
    private ObjectStorageService objectStorageService;
    private UserPrincipal principal;
    private Experiment existing;

    @BeforeEach
    void 准备() {
        mapper = mock(ExperimentMapper.class);
        objectStorageService = mock(ObjectStorageService.class);
        service = new ExperimentService(mapper, objectMapper, new AccessControlService(), objectStorageService);
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        principal = new UserPrincipal(new UserAccount(userId, organizationId, "admin", "", "管理员", "active", true, false, 0), List.of());
        OffsetDateTime start = OffsetDateTime.of(2026, 9, 10, 9, 0, 0, 0, ZoneOffset.ofHours(8));
        existing = new Experiment(UUID.randomUUID(), organizationId, UUID.randomUUID(), "PRJ-001", "项目",
                "EXP-001", "实验", "research", "方案设计", "not_started", "原目的", userId,
                "管理员", 3, start, start.plusHours(2), null, null, start.minusDays(1), start);
        when(mapper.findByNo(organizationId, userId, true, existing.experimentNo())).thenReturn(existing);
        when(mapper.isActiveOrganizationUser(organizationId, userId)).thenReturn(true);
        when(mapper.update(any())).thenReturn(1);
    }

    @Test
    void 只更新基础字段时不应清空记录和计划时间() throws Exception {
        service.update(principal, existing.experimentNo(), 3, objectMapper.readTree("{\"purpose\":\"新目的\"}"));

        ArgumentCaptor<ExperimentMapper.ExperimentUpdateCommand> command = ArgumentCaptor.forClass(ExperimentMapper.ExperimentUpdateCommand.class);
        verify(mapper).update(command.capture());
        assertThat(command.getValue().estimatedStart()).isEqualTo(existing.estimatedStart());
        assertThat(command.getValue().estimatedEnd()).isEqualTo(existing.estimatedEnd());
        verify(mapper, never()).saveRecord(any());
    }

    @Test
    void 更新单个记录字段时应保留其余记录字段() throws Exception {
        when(mapper.findRecord(existing.id())).thenReturn(new ExperimentMapper.ExperimentRecordRow(
                "[{\"id\":\"a\"}]", "[{\"a\":\"1\"}]", "[]", "旧过程", "[]", "旧结果"));

        service.update(principal, existing.experimentNo(), 3, objectMapper.readTree("{\"process_text\":\"新过程\"}"));

        ArgumentCaptor<ExperimentMapper.ExperimentRecordCommand> command = ArgumentCaptor.forClass(ExperimentMapper.ExperimentRecordCommand.class);
        verify(mapper).saveRecord(command.capture());
        assertThat(command.getValue().formulaColumns()).isEqualTo("[{\"id\":\"a\"}]");
        assertThat(command.getValue().processText()).isEqualTo("新过程");
        assertThat(command.getValue().resultText()).isEqualTo("旧结果");
    }

    @Test
    void 超级管理员也不能把实验移到其他组织项目() throws Exception {
        UUID foreignProjectId = UUID.randomUUID();
        when(mapper.isOrganizationProject(principal.organizationId(), foreignProjectId)).thenReturn(false);

        assertThatThrownBy(() -> service.update(principal, existing.experimentNo(), 3,
                objectMapper.readTree("{\"project_id\":\"" + foreignProjectId + "\"}")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("project_not_found"));
        verify(mapper, never()).update(any());
    }

    @Test
    void 非法计划时间应返回参数错误而不是进入数据库转换() {
        assertThatThrownBy(() -> service.update(principal, existing.experimentNo(), 3,
                objectMapper.readTree("{\"estimated_start\":\"not-a-time\"}")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("validation_error"));
        verify(mapper, never()).update(any());
    }

    @Test
    void 提交参与人时应校验组织并整体替换() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(mapper.isActiveOrganizationUser(principal.organizationId(), participantId)).thenReturn(true);

        service.update(principal, existing.experimentNo(), 3,
                objectMapper.readTree("{\"participant_ids\":[\"" + participantId + "\"]}"));

        verify(mapper).deleteParticipants(principal.organizationId(), existing.id());
        ArgumentCaptor<ExperimentMapper.ExperimentParticipantCommand> command =
                ArgumentCaptor.forClass(ExperimentMapper.ExperimentParticipantCommand.class);
        verify(mapper).insertParticipant(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(participantId);
    }

    @Test
    void 结果附件可使用任意格式并流式写入对象存储() {
        byte[] content = "实验结果正文\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "result.unknown-format", "application/x-custom", content);
        when(objectStorageService.put(any(), any(InputStream.class), any(Long.class), any())).thenAnswer(invocation ->
                "s3://materials-lab-assistant/" + invocation.getArgument(0, String.class));

        service.uploadAttachment(principal, existing.experimentNo(), file, "result_file");

        ArgumentCaptor<ExperimentMapper.ExperimentAttachmentCommand> command =
                ArgumentCaptor.forClass(ExperimentMapper.ExperimentAttachmentCommand.class);
        verify(mapper).insertAttachment(command.capture());
        verify(objectStorageService).put(any(), any(InputStream.class), org.mockito.ArgumentMatchers.eq((long) content.length),
                org.mockito.ArgumentMatchers.eq("application/octet-stream"));
        verify(mapper, never()).insertAttachmentContent(any(), any());
        verify(mapper).touchExperiment(existing.id(), principal.userId());
        assertThat(command.getValue().file()).startsWith("s3://materials-lab-assistant/organizations/");
        assertThat(command.getValue().mimeType()).isEqualTo("application/octet-stream");
        assertThat(command.getValue().fileSize()).isEqualTo(content.length);
    }

    @Test
    void 超过三百MiB的结果附件应在读取前拒绝() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(300L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.uploadAttachment(principal, existing.experimentNo(), file, "result_file"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getMessage()).isEqualTo("结果附件不能超过 300 MB"));
        verify(objectStorageService, never()).put(any(), any(InputStream.class), any(Long.class), any());
    }

    @Test
    void 过程图片必须通过服务端文件头校验而非相信客户端MIME() {
        MockMultipartFile file = new MockMultipartFile("file", "unsafe.png", "image/png",
                "<script>not an image</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadAttachment(principal, existing.experimentNo(), file, "process_image"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_document_content"));
        verify(mapper, never()).insertAttachment(any());
    }

    @Test
    void 附件响应必须使用受会话保护的内容路由而非暴露对象存储标识() {
        UUID attachmentId = UUID.randomUUID();
        when(mapper.listAttachments(existing.id(), "process_image")).thenReturn(List.of(
                new ExperimentAttachment(attachmentId, "image.png", "s3://materials-lab-assistant/private-object", "1 KB")));

        var response = service.response(principal, existing);
        String url = response.record().processImages().getFirst().url();

        assertThat(url).isEqualTo("/api/v1/experiments/EXP-001/attachments/" + attachmentId + "/content");
        assertThat(url).doesNotContain("s3://");
    }
}
