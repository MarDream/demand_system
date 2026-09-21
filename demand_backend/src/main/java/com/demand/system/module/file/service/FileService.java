package com.demand.system.module.file.service;

import com.demand.system.module.file.dto.FileUploadDTO;
import com.demand.system.module.file.entity.FileRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface FileService {

    FileUploadDTO upload(MultipartFile file, Long uploaderId);

    /**
     * 批量查询文件内容哈希(SHA-256 hex)。历史记录哈希为 NULL 时从存储惰性回填并持久化。
     *
     * @param fileIds 文件记录 ID 列表(最多 100 个)
     * @return fileId → contentHash(计算失败的文件不在结果中)
     */
    Map<Long, String> getHashesByIds(List<Long> fileIds);

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
