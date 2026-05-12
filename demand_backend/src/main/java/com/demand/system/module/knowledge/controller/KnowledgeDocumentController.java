package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge/bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService documentService;

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
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<KnowledgeDocumentVO> result = documentService.list(knowledgeBaseId, pageNum, pageSize);
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
        String shareLink = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/api/v1/public/knowledge/shares/" + token)
                .replaceQuery(null)
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
}
