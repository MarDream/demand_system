package com.demand.system.module.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.file.entity.FileRecord;
import com.demand.system.module.file.mapper.FileRecordMapper;
import com.demand.system.module.file.service.FileService;
import com.demand.system.module.file.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioStorageService minioStorageService;
    private final FileRecordMapper fileRecordMapper;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    private static final List<String> ALLOWED_TYPES = List.of(
            "jpg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar"
    );

    @Override
    public String upload(MultipartFile file, Long uploaderId) {
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
            String fileUrl = minioStorageService.upload(
                    file.getInputStream(),
                    storageName,
                    file.getContentType()
            );

            FileRecord fileRecord = new FileRecord();
            fileRecord.setOriginalName(originalFilename);
            fileRecord.setStorageName(storageName);
            fileRecord.setFileSize(file.getSize());
            fileRecord.setContentType(file.getContentType());
            fileRecord.setBucketName("demand-system");
            fileRecord.setUploaderId(uploaderId);

            fileRecordMapper.insert(fileRecord);

            return fileUrl;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
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
