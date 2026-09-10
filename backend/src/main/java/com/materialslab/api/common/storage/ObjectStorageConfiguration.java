package com.materialslab.api.common.storage;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 创建与 RustFS 通信的路径风格 S3 客户端。 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {
    /**
     * RustFS 只支持路径风格地址，访问密钥由 Compose secret 映射到配置树。
     *
     * @throws IllegalStateException 启用对象存储却缺少必要连接参数时抛出
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "materials-lab.object-storage", name = "enabled", havingValue = "true")
    public S3Client rustfsS3Client(ObjectStorageProperties properties) {
        require(properties.endpointUrl(), "endpoint-url");
        require(properties.accessKey(), "access-key");
        require(properties.secretKey(), "secret-key");
        require(properties.bucketName(), "bucket-name");
        require(properties.region(), "region");
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpointUrl()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true)
                .build();
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("已启用对象存储但缺少 " + field + " 配置");
        }
    }
}
