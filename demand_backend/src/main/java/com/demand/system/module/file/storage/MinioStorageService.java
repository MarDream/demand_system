package com.demand.system.module.file.storage;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Component
public class MinioStorageService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;

    public MinioStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.public-endpoint}")
    private String publicEndpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    public String getBucketName() {
        return bucketName;
    }

    public String upload(InputStream inputStream, String fileName, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .stream(inputStream, -1, 10 * 1024 * 1024)
                .contentType(contentType)
                .build());
        return endpoint + "/" + bucketName + "/" + fileName;
    }

    public InputStream download(String fileName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .build());
    }

    public InputStream download(String fileName, long offset, long length) throws Exception {
        GetObjectArgs.Builder builder = GetObjectArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .offset(offset);

        if (length > 0) {
            builder.length(length);
        }

        return minioClient.getObject(builder.build());
    }

    public StatObjectResponse stat(String fileName) throws Exception {
        return minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .build());
    }

    public void delete(String fileName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .build());
    }

    public String getPresignedUrl(String fileName, int expiryHours) throws Exception {
        return getPresignedUrl(fileName, expiryHours, endpoint);
    }

    public String getPresignedUrl(String fileName, int expiryHours, String targetEndpoint) throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(targetEndpoint)
                .region("us-east-1")
                .credentials(accessKey, secretKey)
                .build();

        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .method(Method.GET)
                .expiry(expiryHours, TimeUnit.HOURS)
                .build());
    }

    /**
     * 获取用于在线预览的预签名 URL，优先使用对外可访问地址。
     */
    public String getPresignedUrlForDocker(String fileName, int expiryHours) throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(publicEndpoint)
                .region("us-east-1")
                .credentials(accessKey, secretKey)
                .build();

        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .method(Method.GET)
                .expiry(expiryHours, TimeUnit.HOURS)
                .build());
    }
}
