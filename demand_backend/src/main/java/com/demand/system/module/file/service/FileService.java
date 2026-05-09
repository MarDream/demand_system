package com.demand.system.module.file.service;

import com.demand.system.module.file.dto.FileUploadDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileService {

    FileUploadDTO upload(MultipartFile file, Long uploaderId);

    Map<String, Object> download(Long fileId);

    void delete(Long fileId);
}
