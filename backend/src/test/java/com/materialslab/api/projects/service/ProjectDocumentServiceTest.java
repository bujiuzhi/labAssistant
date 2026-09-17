package com.materialslab.api.projects.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.materialslab.api.common.storage.ObjectStorageService;
import com.materialslab.api.identity.security.AccessControlService;
import com.materialslab.api.projects.mapper.ProjectDocumentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 验证数据库事务未提交时会清理已写入对象存储的正文。 */
class ProjectDocumentServiceTest {
    private ObjectStorageService storage;
    private ProjectDocumentService service;

    @BeforeEach
    void 初始化事务同步() {
        storage = mock(ObjectStorageService.class);
        service = new ProjectDocumentService(mock(ProjectDocumentMapper.class), mock(ProjectService.class),
                mock(AccessControlService.class), storage);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void 清理事务同步() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void 回滚后清理已上传对象() {
        service.registerObjectCleanupOnRollback("s3://materials-lab/organizations/org-a/projects/project-a/documents/document-a");

        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).deleteBestEffort("s3://materials-lab/organizations/org-a/projects/project-a/documents/document-a");
    }

    @Test
    void 提交后保留已上传对象() {
        service.registerObjectCleanupOnRollback("s3://materials-lab/organizations/org-a/projects/project-a/documents/document-a");

        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage, never()).deleteBestEffort("s3://materials-lab/organizations/org-a/projects/project-a/documents/document-a");
    }
}
