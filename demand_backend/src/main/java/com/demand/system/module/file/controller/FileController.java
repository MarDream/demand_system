package com.demand.system.module.file.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.file.entity.FileRecord;
import com.demand.system.module.file.service.FileService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        Long uploaderId = 1L;
        String url = fileService.upload(file, uploaderId);
        return Result.success(url);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Map<String, Object> result = fileService.download(id);
        FileRecord fileRecord = (FileRecord) result.get("fileRecord");
        InputStream inputStream = (InputStream) result.get("inputStream");

        try {
            byte[] bytes = inputStream.readAllBytes();
            String encodedName = URLEncoder.encode(fileRecord.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                    .contentType(MediaType.parseMediaType(fileRecord.getContentType()))
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
