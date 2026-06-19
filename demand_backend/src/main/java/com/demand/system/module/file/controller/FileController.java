package com.demand.system.module.file.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.file.dto.FileUploadDTO;
import com.demand.system.module.file.entity.FileRecord;
import com.demand.system.module.file.service.FileService;
import com.demand.system.module.file.storage.MinioStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    /**
     * 预览场景下 MinIO 预签名 URL 有效期（小时）。
     *
     * <p>预览链路需要 kkFileView 容器反向拉取文件，该 URL 必须对外网可达且长有效期，
     * 避免转码过程中 token 过期导致 403。</p>
     */
    private static final int PREVIEW_PRESIGN_HOURS = 24;

    private final FileService fileService;
    private final MinioStorageService minioStorageService;

    public FileController(FileService fileService, MinioStorageService minioStorageService) {
        this.fileService = fileService;
        this.minioStorageService = minioStorageService;
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public Result<FileUploadDTO> upload(@RequestParam("file") MultipartFile file) {
        Long uploaderId = SecurityUtils.getCurrentUserId();
        if (uploaderId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(fileService.upload(file, uploaderId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return buildFileResponse(id, "attachment");
    }

    /**
     * 内联预览：浏览器原生渲染（图片、PDF、文本），不走 kkFileView。
     *
     * <p>前端场景：{@code FilePreviewDialog} 处理 image / text 类型预览。</p>
     */
    @GetMapping("/{id}/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        return buildFileResponse(id, "inline");
    }

    /**
     * 第三方预览服务（如 kkFileView）专用的对外可达 URL。
     *
     * <p>kkFileView 在容器内部反向拉取文件，<strong>不能</strong> 直接传 {@code /api/v1/files/{id}}
     * （带 JWT header，仅前端可达且需要登录）。本接口签发 24h 长有效期 MinIO 预签名 URL，
     * kkFileView 可通过该 URL 无认证拉取原文件进行转码。</p>
     *
     * <p>调用方：{@link com.demand.system.module.preview.controller.PreviewController} 走
     * {@code /api/v1/preview/office-submit?fileUrl=...} 时，前端先用本接口拿到 URL 再喂给预览服务。</p>
     */
    @GetMapping("/{id}/preview-url")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, String>> previewUrl(@PathVariable Long id) {
        FileRecord fileRecord = fileService.findRecord(id);
        if (fileRecord == null) {
            throw new BusinessException("文件记录不存在");
        }
        try {
            String url = minioStorageService.getPresignedUrlForDocker(fileRecord.getStorageName(), PREVIEW_PRESIGN_HOURS);
            return Result.success(Map.of("url", url));
        } catch (Exception e) {
            throw new BusinessException("获取预览地址失败: " + e.getMessage());
        }
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
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success();
    }
}
