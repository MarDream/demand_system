package com.demand.system.module.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileService {

    String upload(MultipartFile file, Long uploaderId);

    Map<String, Object> download(Long fileId);

    void delete(Long fileId);
}
