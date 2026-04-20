package com.demand.system.module.file.storage;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

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

    public void delete(String fileName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .build());
    }

    public String getPresignedUrl(String fileName, int expiryHours) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucketName)
                .object(fileName)
                .method(Method.GET)
                .expiry(expiryHours, TimeUnit.HOURS)
                .build());
    }
}
