package com.materialslab.api.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/** 验证就绪探针不会缓存 RustFS 状态，且历史对象保留其持久化桶名。 */
class ObjectStorageServiceTest {
    @Test
    void 每次就绪检查都应探测当前对象存储状态() {
        var fixture = fixture();

        fixture.service().verifyReady();
        fixture.service().verifyReady();

        verify(fixture.client(), times(2)).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void 应读取持久化引用中的历史桶而非当前配置桶() {
        var fixture = fixture();
        when(fixture.client().getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), "historic-body".getBytes(StandardCharsets.UTF_8)));

        assertThat(fixture.service().read("s3://historic-bucket/organizations/org-a/documents/doc-a"))
                .isEqualTo("historic-body".getBytes(StandardCharsets.UTF_8));
        var request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(fixture.client()).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("historic-bucket");
        assertThat(fixture.service().isObjectReference("s3://historic-bucket/organizations/org-a/documents/doc-a")).isTrue();
        assertThat(fixture.service().isObjectReference("s3://historic-bucket/other/path")).isFalse();
        assertThat(fixture.service().isObjectReference("s3://historic-bucket/organizations/org-a\u0000/documents/doc-a")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture() {
        var client = mock(S3Client.class);
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        var properties = new ObjectStorageProperties(true, "http://rustfs:9000", "access", "secret", "current-bucket", "us-east-1");
        return new Fixture(new ObjectStorageService(properties, provider), client);
    }

    private record Fixture(ObjectStorageService service, S3Client client) { }
}
