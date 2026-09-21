package com.demand.system.module.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.file.dto.FileUploadDTO;
import com.demand.system.module.file.entity.FileRecord;
import com.demand.system.module.file.mapper.FileRecordMapper;
import com.demand.system.module.file.service.FileService;
import com.demand.system.module.file.storage.MinioStorageService;
import com.demand.system.module.preview.PreviewWarmupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {
    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MAX_HASH_BATCH = 100;
    private static final List<String> ALLOWED_TYPES = List.of(
            "jpg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar", "csv"
    );
    /** 上传后需要预热 kkFileView 转换缓存的类型：ppt/pptx/doc/docx 首次转换耗时可达秒级 */
    private static final List<String> WARMUP_TYPES = List.of("doc", "docx", "ppt", "pptx");

    private final MinioStorageService minioStorageService;
    private final FileRecordMapper fileRecordMapper;
    private final PreviewWarmupService previewWarmupService;

    public FileServiceImpl(MinioStorageService minioStorageService,
                           FileRecordMapper fileRecordMapper,
                           PreviewWarmupService previewWarmupService) {
        this.minioStorageService = minioStorageService;
        this.fileRecordMapper = fileRecordMapper;
        this.previewWarmupService = previewWarmupService;
    }

    @Override
    public FileUploadDTO upload(MultipartFile file, Long uploaderId) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过50MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        if (!ALLOWED_TYPES.contains(extension)) {
            throw new BusinessException("不支持的文件类型: " + extension);
        }

        String storageName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        try {
            // 字节只读一次：SHA-256 与 MinIO 上传共用，避免流被消费两次
            byte[] bytes = file.getInputStream().readAllBytes();
            String contentHash = sha256Hex(bytes);
            String fileUrl = minioStorageService.upload(
                    new ByteArrayInputStream(bytes),
                    storageName,
                    file.getContentType()
            );

            FileRecord fileRecord = new FileRecord();
            fileRecord.setOriginalName(originalFilename);
            fileRecord.setStorageName(storageName);
            fileRecord.setFileSize(file.getSize());
            fileRecord.setContentType(file.getContentType());
            fileRecord.setBucketName(minioStorageService.getBucketName());
            fileRecord.setUploaderId(uploaderId);
            fileRecord.setContentHash(contentHash);

            fileRecordMapper.insert(fileRecord);

            // 尽力预热 kkFileView：转换缓存提前生成，用户首次打开预览免等转码
            if (WARMUP_TYPES.contains(extension)) {
                try {
                    String presignedUrl = minioStorageService.getPresignedUrlForDocker(storageName, 24);
                    previewWarmupService.warmup(presignedUrl, originalFilename);
                } catch (Exception warmupEx) {
                    log.warn("附件预览预热提交失败: fileName={}", originalFilename, warmupEx);
                }
            }

            FileUploadDTO result = new FileUploadDTO();
            result.setFileId(fileRecord.getId());
            result.setName(originalFilename);
            result.setUrl(fileUrl);
            result.setSize(file.getSize());
            result.setContentType(file.getContentType());
            result.setBucketName(fileRecord.getBucketName());
            result.setObjectName(storageName);
            result.setUploaderId(uploaderId);
            result.setContentHash(contentHash);
            return result;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public Map<Long, String> getHashesByIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = fileIds.stream().filter(java.util.Objects::nonNull).distinct().limit(MAX_HASH_BATCH).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<FileRecord> records = fileRecordMapper.selectBatchIds(ids);
        Map<Long, String> result = new HashMap<>();
        List<FileRecord> needBackfill = new java.util.ArrayList<>();
        for (FileRecord record : records) {
            if (record.getContentHash() != null && !record.getContentHash().isBlank()) {
                result.put(record.getId(), record.getContentHash());
            } else {
                needBackfill.add(record);
            }
        }

        // 历史文件哈希为空：从存储拉回内容计算并持久化（每个文件只算一次）
        for (FileRecord record : needBackfill) {
            try (InputStream in = minioStorageService.download(record.getStorageName())) {
                String hash = sha256Hex(in.readAllBytes());
                record.setContentHash(hash);
                fileRecordMapper.updateById(record);
                result.put(record.getId(), hash);
            } catch (Exception e) {
                log.warn("文件哈希回填失败 fileId={}: {}", record.getId(), e.getMessage());
            }
        }
        return result;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException("计算文件哈希失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> download(Long fileId) {
        FileRecord fileRecord = fileRecordMapper.selectById(fileId);
        if (fileRecord == null) {
            throw new BusinessException("文件记录不存在");
        }

        try {
            InputStream inputStream = minioStorageService.download(fileRecord.getStorageName());
            Map<String, Object> result = new HashMap<>();
            result.put("inputStream", inputStream);
            result.put("fileRecord", fileRecord);
            return result;
        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public FileRecord findRecord(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return fileRecordMapper.selectById(fileId);
    }

    @Override
    public void delete(Long fileId) {
        FileRecord fileRecord = fileRecordMapper.selectById(fileId);
        if (fileRecord == null) {
            throw new BusinessException("文件记录不存在");
        }

        try {
            minioStorageService.delete(fileRecord.getStorageName());
            fileRecordMapper.deleteById(fileId);
        } catch (Exception e) {
            log.error("文件删除失败", e);
            throw new BusinessException("文件删除失败: " + e.getMessage());
        }
    }
}
