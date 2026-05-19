package com.demand.system.module.file.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.file.dto.FileUploadDTO;
import com.demand.system.module.file.entity.FileRecord;
import com.demand.system.module.file.service.FileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public Result<FileUploadDTO> upload(@RequestParam("file") MultipartFile file) {
        Long uploaderId = SecurityUtils.getCurrentUserId();
        if (uploaderId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(fileService.upload(file, uploaderId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return buildFileResponse(id, "attachment");
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        return buildFileResponse(id, "inline");
    }

    private ResponseEntity<byte[]> buildFileResponse(Long id, String dispositionType) {
        Map<String, Object> result = fileService.download(id);
        FileRecord fileRecord = (FileRecord) result.get("fileRecord");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (fileRecord.getContentType() != null && !fileRecord.getContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(fileRecord.getContentType());
        }

        try (InputStream inputStream = (InputStream) result.get("inputStream")) {
            byte[] bytes = inputStream.readAllBytes();
            String encodedName = URLEncoder.encode(fileRecord.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename*=UTF-8''" + encodedName)
                    .contentType(mediaType)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success();
    }
}
