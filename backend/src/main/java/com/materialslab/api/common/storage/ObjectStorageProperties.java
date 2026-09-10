package com.materialslab.api.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** RustFS 的 S3 兼容连接参数；访问密钥必须通过运行时密钥注入。 */
@ConfigurationProperties(prefix = "materials-lab.object-storage")
public record ObjectStorageProperties(
        boolean enabled,
        String endpointUrl,
        String accessKey,
        String secretKey,
        String bucketName,
        String region) { }
