package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge/bases/{knowledgeBaseId}/documents")
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService documentService;

    public KnowledgeDocumentController(KnowledgeDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public Result<KnowledgeDocumentVO> upload(
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        KnowledgeDocumentVO vo = documentService.upload(knowledgeBaseId, file, userId);
        return Result.success(vo);
    }

    @GetMapping
    public Result<PageResult<KnowledgeDocumentVO>> list(
            @PathVariable Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdAtStart,
            @RequestParam(required = false) String createdAtEnd,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) Long requirementId) {
        PageResult<KnowledgeDocumentVO> result = documentService.list(
                knowledgeBaseId,
                pageNum,
                pageSize,
                fileName,
                status,
                createdAtStart,
                createdAtEnd,
                projectName,
                requirementId
        );
        return Result.success(result);
    }

    @DeleteMapping("/{documentId}")
    public Result<Void> delete(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId) {
        documentService.delete(knowledgeBaseId, documentId);
        return Result.success();
    }

    @PostMapping("/{documentId}/share")
    public Result<String> generateShareLink(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "24") Integer expireHours,
            @RequestParam(defaultValue = "false") Boolean requireLogin,
            @RequestParam(defaultValue = "false") Boolean oneTimeAccess,
            HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String token = documentService.generateShareLink(knowledgeBaseId, documentId, expireHours, requireLogin, oneTimeAccess, userId);
        String publicBaseUrl = resolvePublicBaseUrl(request);
        String shareLink = ServletUriComponentsBuilder.fromUriString(publicBaseUrl)
                .path("/public/share/" + token)
                .build()
                .toUriString();
        return Result.success(shareLink);
    }

    @PostMapping("/retry")
    public Result<Map<String, Object>> retryDocuments(
            @PathVariable Long knowledgeBaseId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> documentIds = body.get("documentIds");
        if (documentIds == null || documentIds.isEmpty()) {
            return Result.fail(400, "请选择要重传的文档");
        }
        int retried = documentService.retryDocuments(knowledgeBaseId, documentIds);
        return Result.success(Map.of("retried", retried));
    }

    @PostMapping("/batch-delete")
    public Result<Map<String, Object>> batchDelete(
            @PathVariable Long knowledgeBaseId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> documentIds = body.get("documentIds");
        if (documentIds == null || documentIds.isEmpty()) {
            return Result.fail(400, "请选择要删除的文档");
        }
        int deleted = documentService.batchDelete(knowledgeBaseId, documentIds);
        return Result.success(Map.of("deleted", deleted));
    }

    /**
     * 跳过文档的索引（仅保留文件存储）。
     *
     * <p>适用于大文件卡死"持续索引中"的场景。操作后 status 切到 stored，
     * 保留预览和下载能力。失败/卡死/pending/indexing 状态均可调用。</p>
     */
    @PostMapping("/{documentId}/skip-indexing")
    public Result<Void> skipIndexing(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId) {
        documentService.skipIndexing(knowledgeBaseId, documentId);
        return Result.success();
    }

    @GetMapping("/{documentId}/preview")
    public Result<String> preview(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId) {
        String url = documentService.getPreviewUrl(knowledgeBaseId, documentId);
        return Result.success(url);
    }

    @GetMapping("/{documentId}/download")
    public void download(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId,
            HttpServletResponse response) {
        documentService.downloadDocument(knowledgeBaseId, documentId, response);
    }

    @PostMapping("/batch-download")
    public void batchDownload(
            @PathVariable Long knowledgeBaseId,
            @RequestBody Map<String, List<Long>> body,
            HttpServletResponse response) {
        List<Long> documentIds = body.get("documentIds");
        documentService.batchDownloadDocuments(knowledgeBaseId, documentIds, response);
    }

    private String resolvePublicBaseUrl(HttpServletRequest request) {
        for (String headerName : List.of("Origin", "Referer")) {
            String headerValue = request.getHeader(headerName);
            if (headerValue == null || headerValue.isBlank()) {
                continue;
            }
            try {
                URI uri = URI.create(headerValue);
                if (uri.getScheme() != null && uri.getHost() != null) {
                    StringBuilder builder = new StringBuilder(uri.getScheme())
                            .append("://")
                            .append(uri.getHost());
                    if (uri.getPort() > 0) {
                        builder.append(':').append(uri.getPort());
                    }
                    return builder.toString();
                }
            } catch (Exception ignored) {
            }
        }

        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("")
                .replaceQuery(null)
                .build()
                .toUriString();
    }
}
