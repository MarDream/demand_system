package com.demand.system.module.file.service;

import com.demand.system.module.file.dto.FileUploadDTO;
import com.demand.system.module.file.entity.FileRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileService {

    FileUploadDTO upload(MultipartFile file, Long uploaderId);

    Map<String, Object> download(Long fileId);

    /**
     * 仅查文件元数据，不下载文件流。供预览 URL 签发场景使用。
     *
     * @param fileId 文件记录 ID
     * @return 文件记录；不存在返回 null
     */
    FileRecord findRecord(Long fileId);

    void delete(Long fileId);
}
