package com.materialslab.api.common.storage;

import com.materialslab.api.common.exception.BusinessException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/** 通过 RustFS 的 S3 兼容接口保存、读取和删除私有业务对象。 */
@Service
public class ObjectStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageService.class);
    private static final String REFERENCE_PREFIX = "s3://";

    private final ObjectStorageProperties properties;
    private final ObjectProvider<S3Client> clientProvider;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public ObjectStorageService(ObjectStorageProperties properties, ObjectProvider<S3Client> clientProvider) {
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    /** 验证 RustFS 可访问并确保私有业务桶已创建；供启动和就绪探针调用。 */
    public void verifyReady() {
        if (!properties.enabled()) return;
        ensureBucket();
    }

    /** 返回对象存储是否作为当前运行环境的就绪依赖。 */
    public boolean isEnabled() { return properties.enabled(); }

    /** 将经过上层校验的对象写入 RustFS，并返回数据库保存的稳定存储标识。 */
    public String put(String key, byte[] content, String mimeType) {
        verifyReady();
        try {
            client().putObject(PutObjectRequest.builder().bucket(properties.bucketName()).key(key)
                    .contentType(mimeType).contentLength((long) content.length).build(), RequestBody.fromBytes(content));
            return reference(key);
        } catch (S3Exception | SdkClientException error) {
            throw failure("写入", key, error);
        }
    }

    /**
     * 将已知长度的输入流写入 RustFS，避免大文件在 API 进程中完整驻留。
     *
     * @param key 私有桶内对象键
     * @param content 调用方负责关闭的输入流
     * @param contentLength 已校验的正文长度
     * @param mimeType 服务端决定的媒体类型
     * @return 可持久化的 S3 存储标识
     */
    public String put(String key, InputStream content, long contentLength, String mimeType) {
        verifyReady();
        try {
            client().putObject(PutObjectRequest.builder().bucket(properties.bucketName()).key(key)
                    .contentType(mimeType).contentLength(contentLength).build(),
                    RequestBody.fromInputStream(content, contentLength));
            return reference(key);
        } catch (S3Exception | SdkClientException error) {
            throw failure("写入", key, error);
        }
    }

    /** 读取私有对象正文；授权校验必须由调用方在本方法前完成。 */
    public byte[] read(String reference) {
        String key = key(reference);
        try {
            ResponseBytes<GetObjectResponse> result = client().getObjectAsBytes(
                    GetObjectRequest.builder().bucket(properties.bucketName()).key(key).build());
            return result.asByteArray();
        } catch (NoSuchKeyException error) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "object_content_not_found", "文件正文不存在");
        } catch (S3Exception error) {
            if (error.statusCode() == 404) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "object_content_not_found", "文件正文不存在");
            }
            throw failure("读取", key, error);
        } catch (SdkClientException error) {
            throw failure("读取", key, error);
        }
    }

    /**
     * 打开私有对象输入流；调用方在响应写出结束后必须关闭。
     *
     * @param reference 数据库保存的 S3 对象标识
     * @return 可顺序读取的对象正文流
     */
    public InputStream open(String reference) {
        String key = key(reference);
        try {
            ResponseInputStream<GetObjectResponse> result = client().getObject(
                    GetObjectRequest.builder().bucket(properties.bucketName()).key(key).build());
            return result;
        } catch (NoSuchKeyException error) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "object_content_not_found", "文件正文不存在");
        } catch (S3Exception error) {
            if (error.statusCode() == 404) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "object_content_not_found", "文件正文不存在");
            }
            throw failure("读取", key, error);
        } catch (SdkClientException error) {
            throw failure("读取", key, error);
        }
    }

    /** 尽力删除对象；调用方应在数据库提交后调用以避免正文先于元数据丢失。 */
    public void deleteBestEffort(String reference) {
        if (!isObjectReference(reference)) return;
        String key = key(reference);
        try {
            client().deleteObject(DeleteObjectRequest.builder().bucket(properties.bucketName()).key(key).build());
        } catch (S3Exception | SdkClientException error) {
            LOGGER.error("RustFS 对象删除失败，bucket={}, key={}", properties.bucketName(), key, error);
        }
    }

    /** 供旧 BYTEA 记录兼容判断；新写入必须使用此类生成的 S3 标识。 */
    public boolean isObjectReference(String reference) {
        return reference != null && reference.startsWith(REFERENCE_PREFIX + properties.bucketName() + "/");
    }

    private void ensureBucket() {
        if (bucketReady.get()) return;
        synchronized (bucketReady) {
            if (bucketReady.get()) return;
            try {
                client().headBucket(HeadBucketRequest.builder().bucket(properties.bucketName()).build());
            } catch (S3Exception error) {
                if (error.statusCode() != 404) throw failure("检查桶", properties.bucketName(), error);
                try {
                    client().createBucket(CreateBucketRequest.builder().bucket(properties.bucketName()).build());
                } catch (S3Exception createError) {
                    throw failure("创建桶", properties.bucketName(), createError);
                }
            } catch (SdkClientException error) {
                throw failure("检查桶", properties.bucketName(), error);
            }
            bucketReady.set(true);
        }
    }

    private S3Client client() {
        S3Client client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "object_storage_unavailable", "对象存储未启用或不可用");
        }
        return client;
    }

    private String reference(String key) {
        return REFERENCE_PREFIX + properties.bucketName() + "/" + key;
    }

    private String key(String reference) {
        if (!isObjectReference(reference)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "object_content_not_found", "文件正文不存在");
        }
        return reference.substring((REFERENCE_PREFIX + properties.bucketName() + "/").length());
    }

    private BusinessException failure(String action, String key, RuntimeException error) {
        LOGGER.warn("RustFS 对象{}失败，bucket={}, key={}", action, properties.bucketName(), key, error);
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "object_storage_unavailable", "对象存储暂时不可用，请稍后重试");
    }
}
